package pl.mchosting.server.minigry;

import java.time.Duration;
import java.util.Optional;
import pl.mchosting.server.api.Stats;

/**
 * Games played as matches from a queue. Player counts follow the server plan. Parkour is not here:
 * it is played alone, without a queue (see {@link ParkourManager}).
 */
enum GameType {
    SPLEEF(Stats.SPLEEF, "Spleef", 2, 16, Duration.ofMinutes(5), 10),
    TNT_RUN(Stats.TNT_RUN, "TNT Run", 4, 24, Duration.ofMinutes(5), 10),
    /** One on one with a kit; the kit may set its own time limit ({@link DuelKit#timeLimit()}). */
    DUELS(Stats.DUELS, "Duels", 2, 2, Duration.ofMinutes(5), 5);

    private final String id;
    private final String displayName;
    private final int minPlayers;
    private final int maxPlayers;
    private final Duration timeLimit;
    private final int countdownSeconds;

    GameType(String id, String displayName, int minPlayers, int maxPlayers, Duration timeLimit, int countdownSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.timeLimit = timeLimit;
        this.countdownSeconds = countdownSeconds;
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

    /** Seconds on the arena before the match starts. */
    int countdownSeconds() {
        return countdownSeconds;
    }

    /** Arenas of this game need at least this many start points. */
    int minSpawns() {
        return this == DUELS ? 2 : 1;
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
