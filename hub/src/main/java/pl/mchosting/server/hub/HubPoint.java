package pl.mchosting.server.hub;

import java.util.Locale;
import java.util.Optional;

/** Places in the valley that staff set with {@code /hubadmin ustaw} and players travel to. */
enum HubPoint {
    SPAWN("Spawn"),
    MINIGRY("Minigry (północ)"),
    TRYBY("Tryby (wschód)"),
    ALEJA("Aleja serwerów (południe)"),
    AMFITEATR("Amfiteatr (zachód)");

    private final String displayName;

    HubPoint(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    /** Name used in commands and in config.yml, for example {@code aleja}. */
    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<HubPoint> byId(String id) {
        for (HubPoint point : values()) {
            if (point.id().equals(id)) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }
}
