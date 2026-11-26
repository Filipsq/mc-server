# mc-server

Kod własnej sieci Minecraft mchosting.pl: hub, tryby i wspólne konto gracza.

Plan i decyzje: [Plan serwera: hub, tryby i pluginy](https://claude.ai/artifact/SWphDg5njDh8RDNwj7Kach) oraz [analiza rynku](https://claude.ai/artifact/7DzuPaJwXSwADQswabQ3EM).

## Moduły

| Moduł | Co to jest |
|---|---|
| `core-api` | Wspólne typy i interfejsy (profil gracza, poziomy). Czysta Java, bez Paper. |
| `core` | Plugin `MchostingCore`: konto gracza dla całej sieci. Wgrywany na każdy serwer sieci. |
| `hub` | Plugin `MchostingHub`: wszystko, co dzieje się w hubie. Wymaga `MchostingCore`. |

Więcej: [docs/architektura.md](docs/architektura.md).

## Jak zbudować

Potrzebna Java 25.

```
./gradlew build
```

Gotowe pluginy są w `core/build/libs/` i `hub/build/libs/`. GitHub buduje je też przy każdym pull requeście (zakładka Actions, plik „pluginy”).

Lokalny serwer testowy z hubem: `./gradlew :hub:runServer`.

## Zasady

Zasady pisania kodu są w [CLAUDE.md](CLAUDE.md). `./gradlew build` sprawdza formatowanie i testy, więc kod, który przechodzi build, spełnia podstawowe zasady.
