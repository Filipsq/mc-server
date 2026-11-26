package pl.mchosting.server.hub;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stops a player from using the same thing too often (spam-clicking items lags the server). */
final class Cooldown {

    private final Duration length;
    private final Clock clock;
    private final Map<UUID, Instant> readyAt = new ConcurrentHashMap<>();

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

    /** Time left before the player may act again, zero if they may act now. */
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
