package pl.mchosting.server.minigry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lets a player use something (the TNT Run double jump) once every {@code length}. */
final class Cooldown {

    private final Duration length;
    private final Clock clock;
    private final Map<UUID, Instant> readyAt = new HashMap<>();

    Cooldown(Duration length, Clock clock) {
        this.length = length;
        this.clock = clock;
    }

    /** True and starts the cooldown if the player may act now; false if they must wait. */
    boolean tryUse(UUID playerId) {
        Instant now = clock.instant();
        Instant ready = readyAt.get(playerId);
        if (ready != null && now.isBefore(ready)) {
            return false;
        }
        readyAt.put(playerId, now.plus(length));
        return true;
    }

    boolean isReady(UUID playerId) {
        return remaining(playerId).isZero();
    }

    Duration remaining(UUID playerId) {
        Instant ready = readyAt.get(playerId);
        if (ready == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(clock.instant(), ready);
        return left.isNegative() ? Duration.ZERO : left;
    }

    void forget(UUID playerId) {
        readyAt.remove(playerId);
    }
}
