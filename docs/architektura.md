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

- `core-api`: rekordy i interfejsy (`PlayerProfile`, `ProfileService`, `LevelCurve`). Bez zależności od Paper, w pełni testowany.
- `core`: implementacje usług. Klasy `core-api` są wbudowane w jar `core`, więc inne pluginy dostają je w czasie działania przez `join-classpath`.
- Pluginy trybów: `compileOnly(project(":core-api"))` i zależność `MchostingCore` w `paper-plugin.yml`.

## Dane gracza

Teraz: profile tylko w pamięci (`InMemoryProfileService`), znikają po restarcie. To świadomy etap przejściowy.

Docelowo:
- MariaDB: profile, kosmetyki, statystyki, kary. Migracje schematu w repo, dostęp przez pulę połączeń, zawsze poza głównym wątkiem.
- Redis: szybka wymiana między serwerami (kto jest online gdzie, party, wiadomości między trybami).

## Kolejne moduły (plan)

| Moduł | Etap |
|---|---|
| `link` (mchosting-link na serwerach klientów, kompilowany pod starsze Paper 1.20.5+) | 1 |
| `minigry` | 1 |
| `boxpvp`, `przedmioty`, `party` | 2 |
| `lifesteal` | 3 |
