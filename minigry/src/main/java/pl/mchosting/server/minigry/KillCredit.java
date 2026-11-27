package pl.mchosting.server.minigry;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who knocked a player out. In Spleef nobody hits the final blow, so the kill goes to whoever
 * last broke a block under the player or hit them with a snowball, if it was recent enough.
 */
final class KillCredit {

    private record Hit(UUID attacker, Instant at) {}

    private final Duration memory;
    private final Map<UUID, Hit> lastHit = new HashMap<>();

    KillCredit(Duration memory) {
        this.memory = memory;
    }

    void hit(UUID victim, UUID attacker, Instant at) {
        if (!victim.equals(attacker)) {
            lastHit.put(victim, new Hit(attacker, at));
        }
    }

    /** Who gets the kill if {@code victim} is out at {@code at}. */
    Optional<UUID> killer(UUID victim, Instant at) {
        Hit hit = lastHit.get(victim);
        if (hit == null || Duration.between(hit.at(), at).compareTo(memory) > 0) {
            return Optional.empty();
        }
        return Optional.of(hit.attacker());
    }

    void forget(UUID victim) {
        lastHit.remove(victim);
    }
}
