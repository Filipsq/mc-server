# Zasady dla tego repozytorium

Repo zawiera pluginy Paper dla własnej sieci Minecraft mchosting.pl. Właściciel (Fiq) pisze po polsku i nie jest programistą: opisy PR i wiadomości po polsku, prostym językiem.

## Budowanie i sprawdzanie

- `./gradlew build` musi przejść przed każdym commitem: kompilacja z ostrzeżeniami jako błędami, testy i `spotlessCheck`.
- Formatowanie: `./gradlew spotlessApply` (Palantir Java Format). Nie formatuj ręcznie.
- Wersje bibliotek tylko w `gradle/libs.versions.toml`. Wspólna konfiguracja modułów w `build-logic/` (`mcserver.java`, `mcserver.paper-plugin`), nie kopiuj jej do modułów.

## Kod

- Java 25, Paper API (wersja w `libs.versions.toml`). Kod i komentarze po angielsku, teksty dla graczy po polsku.
- Teksty dla graczy przez MiniMessage z `Placeholder.unparsed` dla danych od graczy (nigdy nie wklejaj nicku ani tekstu gracza jako tagów MiniMessage).
- Komendy przez Brigadier (`LifecycleEvents.COMMANDS`), nie przez `plugin.yml`. Pluginy używają `paper-plugin.yml`.
- Moduły rozmawiają ze sobą tylko przez interfejsy z `core-api` (Bukkit `ServicesManager`). Plugin trybu nie importuje klas z `core`.
- Logika gry (liczenie poziomów, nagród, cen) w czystych klasach bez Bukkit, z testami JUnit. Listenery i komendy tylko łączą Bukkit z tą logiką.
- Klasy domyślnie `final` i package-private. Publiczne jest tylko to, czego potrzebuje inny moduł.
- Żadnych operacji na bazie, plikach ani sieci w głównym wątku serwera.
- Bez pay-to-win: nic, co daje przewagę w grze, nie może być sprzedawane za prawdziwe pieniądze (zasady Mojang).

## Git

- Każda zmiana przez pull request do `main`. Build na GitHubie musi być zielony.
- Jeden PR = jedna rzecz (np. „kompas wyboru trybu”), opis po polsku: co zmienia i jak to sprawdzić w grze.
