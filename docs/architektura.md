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
| `link` (mchosting-link na serwerach klientów, kompilowany pod starsze Paper 1.20.5+) | 1 |
| `minigry` | 1 |
| `boxpvp`, `przedmioty`, `party` | 2 |
| `lifesteal` | 3 |
