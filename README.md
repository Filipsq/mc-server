# mc-server

Kod własnej sieci Minecraft mchosting.pl: hub, tryby i wspólne konto gracza.

Plan i decyzje: [Plan serwera: hub, tryby i pluginy](https://claude.ai/artifact/SWphDg5njDh8RDNwj7Kach) oraz [analiza rynku](https://claude.ai/artifact/7DzuPaJwXSwADQswabQ3EM).

## Moduły

| Moduł | Co to jest |
|---|---|
| `core-api` | Wspólne typy i interfejsy (profil gracza, poziomy). Czysta Java, bez Paper. |
| `core` | Plugin `MchostingCore`: konto gracza dla całej sieci. Wgrywany na każdy serwer sieci. |
| `hub` | Plugin `MchostingHub`: wszystko, co dzieje się w hubie. Wymaga `MchostingCore`. |
| `minigry` | Plugin `MchostingMinigry`: kolejki, dobieranie graczy i mecze (na razie Spleef i TNT Run). Wymaga `MchostingCore`, działa na serwerze huba. |
| `maps/boxpvp` | Mapa BoxPvP „Serwerownia”: generator, paczka danych i koordynaty stref dla przyszłego pluginu ([opis](maps/boxpvp/README.md)). |

Więcej: [docs/architektura.md](docs/architektura.md).

## Jak zbudować

Potrzebna Java 25.

```
./gradlew build
```

Gotowe pluginy są w `core/build/libs/`, `hub/build/libs/` i `minigry/build/libs/`. GitHub buduje je też przy każdym pull requeście (zakładka Actions, plik „pluginy”).

Lokalny serwer testowy z hubem: `./gradlew :hub:runServer`, z hubem i minigrami: `./gradlew :minigry:runServer`.

## Jak ustawić minigry (dla obsługi)

1. `/minigryadmin swiat minigry` tworzy pusty świat na areny i przenosi Cię tam.
2. Zbuduj arenę (Spleef: podłoga ze śniegu, bloki `SNOW_BLOCK`; TNT Run: piętra z piasku lub żwiru, pod nimi może być TNT).
3. `/minigryadmin arena utworz spleef1 spleef` (albo `tntrun`).
4. `/minigryadmin arena spawn spleef1` w kilku miejscach startu, `rog1` i `rog2` w dwóch przeciwległych rogach areny, `wysokosc` pod areną (kto spadnie niżej, odpada).
5. `/minigryadmin arena wlacz spleef1`. `/minigryadmin arena lista` pokazuje, czego jeszcze brakuje.
6. W hubie: `/hubadmin brama spleef rog1` i `rog2` w rogach bramy w dzielnicy Minigry. Wejście w bramę lub kliknięcie gry w kompasie = kolejka.

Gracze: `/graj spleef`, `/graj tntrun`, `/opusc` (wyjście z kolejki albo z meczu).

## Zasady

Zasady pisania kodu są w [CLAUDE.md](CLAUDE.md). `./gradlew build` sprawdza formatowanie i testy, więc kod, który przechodzi build, spełnia podstawowe zasady.
