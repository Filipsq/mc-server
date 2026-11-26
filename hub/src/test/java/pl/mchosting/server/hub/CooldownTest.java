package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownTest {

    private final UUID player = UUID.randomUUID();
    private final TestClock clock = new TestClock();
    private final Cooldown cooldown = new Cooldown(Duration.ofSeconds(3), clock);

    @Test
    void firstUseIsAllowed() {
        assertTrue(cooldown.tryUse(player));
        assertEquals(Duration.ofSeconds(3), cooldown.remaining(player));
    }

    @Test
    void secondUseMustWait() {
        cooldown.tryUse(player);
        clock.now = clock.now.plusSeconds(2);
        assertFalse(cooldown.tryUse(player));
        assertEquals(Duration.ofSeconds(1), cooldown.remaining(player));
    }

    @Test
    void allowedAgainAfterCooldown() {
        cooldown.tryUse(player);
        clock.now = clock.now.plusSeconds(3);
        assertEquals(Duration.ZERO, cooldown.remaining(player));
        assertTrue(cooldown.tryUse(player));
    }

    @Test
    void playersHaveSeparateCooldowns() {
        cooldown.tryUse(player);
        assertTrue(cooldown.tryUse(UUID.randomUUID()));
    }

    @Test
    void forgottenPlayerStartsFresh() {
        cooldown.tryUse(player);
        cooldown.forget(player);
        assertTrue(cooldown.tryUse(player));
    }

    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
