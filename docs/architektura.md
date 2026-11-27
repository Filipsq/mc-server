# Architektura

## Jak wygląda sieć

```
gracz → ochrona DDoS → Velocity (proxy) → Hub (Paper)
                                        → BoxPvP (Paper)
                                        → LifeSteal (Paper)
                                        → Minigry (Paper)
serwery klientów mchosting.pl ⇄ hub przez /transfer (plugin mchosting-link)
```

Każdy serwer Paper ma `MchostingCore` (konto gracza) i plugin swojego trybu (`MchostingHub`, później `MchostingBoxPvP` itd.).

## Moduły i zależności

```
core-api  ←  core (implementacja, rejestruje usługi)
   ↑
  hub, boxpvp, lifesteal, ... (tylko przez interfejsy z core-api)
```

- `core-api`: rekordy i interfejsy (`PlayerProfile`, `ProfileService`, `LevelCurve`, `StatKey`/`Stats`, `DailyReward`, `MatchRewards`). Bez zależności od Paper, w pełni testowany.
- `core`: implementacje usług. Klasy `core-api` są wbudowane w jar `core`, więc inne pluginy dostają je w czasie działania przez `join-classpath`.
- Pluginy trybów: `compileOnly(project(":core-api"))` i zależność `MchostingCore` w `paper-plugin.yml`.

## Serwery klientów: /hub i /wroc (mchosting-link)

Plugin `link` (jar `mchosting-link`) panel wgrywa na serwery klientów, które mają włączone połączenie z hubem. Kompiluje się pod Java 21 i Paper 1.20.6 (konwencja `mcserver.client-plugin`), bo klienci mają różne wersje; nowsze serwery też go wczytają.

```
serwer klienta: /hub
  1. podpisuje bilet powrotu swoim kluczem Ed25519 (id serwera z panelu, nick, czas)
  2. zapisuje go w grze gracza jako cookie mchosting:return
  3. /transfer do huba
hub: gracz wchodzi
  4. czyta cookie (poza głównym wątkiem)
  5. pyta panel: GET <panel>/api/link/servers/<id> → nazwa, adres, port, klucz publiczny
  6. sprawdza podpis, nick (bez wielkości liter) i wiek biletu (do 6 h)
  7. daje drzwi „Wróć na <serwer>” i /wroc → /transfer na adres z panelu
```

Bezpieczeństwo:
- Adres powrotu nigdy nie pochodzi z cookie, tylko z panelu, i musi być w domenie z `link.allowed-domains` w hubie (domyślnie `mchost.pl`). Podrobione cookie nie wyśle nikogo na obcy serwer.
- Każdy serwer ma własny klucz. Klient widzi swój klucz prywatny w plikach serwera, ale może nim podpisać tylko powrót na swój własny serwer.
- Bilet jest związany z nickiem, więc skopiowane cookie innego gracza nie działa. Hub zna tylko klucze publiczne.
- Format biletu i sprawdzanie: moduł `link-protocol` (czysta Java 21, wbudowany w jary `link` i `hub`).

Do sprawdzenia na żywym serwerze: czy Velocity przed hubem przepuszcza prośbę o cookie i transfer z huba (Velocity 3.3+ powinien), oraz gracze Bedrock (Geyser nie obsługuje cookies, więc nie dostaną przycisku powrotu).

## Dane gracza

Konto gracza (`PlayerProfile`) jest wspólne dla całej sieci: XP i poziom, monety huba, seria nagrody dnia, statystyki trybów, data pierwszego wejścia i to, czy gracz widział powitanie.

Jak tryb (np. minigra) korzysta z konta, przez `ProfileService` z `ServicesManager`:
- po meczu: `grant(id, MatchRewards.rewardFor(new MatchResult(gracze, miejsce, zabójstwa)))`, te same zasady nagród w każdej minigrze,
- statystyki: `recordStat(id, Stats.wins(Stats.SPLEEF), 1)`; liczniki się sumują, rekordy (`Stats.bestTimeMillis`) zostają najlepsze,
- nic nie da się kupić za prawdziwe pieniądze i nic nie daje przewagi w walce.

Teraz: jeden plik na gracza w `plugins/MchostingCore/profiles/<uuid>.properties` (`FileProfileStore`). Konto wczytuje się w `AsyncPlayerPreLoginEvent` (poza głównym wątkiem), zapisuje przy wyjściu i co 5 minut, wszystko w jednym wątku w tle. Jeśli konta nie da się wczytać, gracz nie wchodzi, żeby puste konto nie nadpisało prawdziwego. Doba dla nagrody dnia liczy się po czasie polskim.

Docelowo:
- MariaDB zamiast plików (`ProfileStore` ma już ten kształt): profile, kosmetyki, statystyki, kary. Migracje schematu w repo, dostęp przez pulę połączeń, zawsze poza głównym wątkiem.
- Redis: szybka wymiana między serwerami (kto jest online gdzie, party, wiadomości między trybami).

## Kolejne moduły (plan)

| Moduł | Etap |
|---|---|
| `link` + `link-protocol` (mchosting-link na serwerach klientów, Paper 1.20.6+): gotowe | 1 |
| `minigry` | 1 |
| `boxpvp`, `przedmioty`, `party` | 2 |
| `lifesteal` | 3 |
