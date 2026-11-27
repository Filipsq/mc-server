package pl.mchosting.server.link;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stops a player from spamming /hub (each try opens a new connection to the hub). */
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

    /** Whole seconds left, rounded up, for the "wait" message. */
    long secondsLeft(UUID playerId) {
        Instant ready = readyAt.get(playerId);
        if (ready == null) {
            return 0;
        }
        Duration left = Duration.between(clock.instant(), ready);
        if (left.isNegative() || left.isZero()) {
            return 0;
        }
        return (left.toMillis() + 999) / 1000;
    }

    void forget(UUID playerId) {
        readyAt.remove(playerId);
    }
}
