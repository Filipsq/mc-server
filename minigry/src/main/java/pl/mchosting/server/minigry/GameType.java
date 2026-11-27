package pl.mchosting.server.minigry;

import java.time.Duration;
import java.util.Optional;
import pl.mchosting.server.api.Stats;

/** Games this plugin runs. Player counts follow the server plan. */
enum GameType {
    SPLEEF(Stats.SPLEEF, "Spleef", 2, 16, Duration.ofMinutes(5)),
    TNT_RUN(Stats.TNT_RUN, "TNT Run", 4, 24, Duration.ofMinutes(5));

    private final String id;
    private final String displayName;
    private final int minPlayers;
    private final int maxPlayers;
    private final Duration timeLimit;

    GameType(String id, String displayName, int minPlayers, int maxPlayers, Duration timeLimit) {
        this.id = id;
        this.displayName = displayName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.timeLimit = timeLimit;
    }

    /** Id used in commands, configs and stats, for example {@code tntrun}. */
    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    int minPlayers() {
        return minPlayers;
    }

    int maxPlayers() {
        return maxPlayers;
    }

    /** A match still running after this long ends without a winner. */
    Duration timeLimit() {
        return timeLimit;
    }

    static Optional<GameType> byId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
