package pl.mchosting.server.link;

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

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final UUID PLAYER = UUID.fromString("3c4a0c26-7db9-4bb5-b53d-e2fa7dbfb5c1");

    @Test
    void firstUseIsAllowedAndNextOneWaits() {
        Cooldown cooldown = cooldownAt(NOW);

        assertTrue(cooldown.tryUse(PLAYER));
        assertFalse(cooldown.tryUse(PLAYER));
    }

    @Test
    void useIsAllowedAgainAfterCooldown() {
        TestClock clock = new TestClock(NOW);
        Cooldown cooldown = new Cooldown(Duration.ofSeconds(3), clock);
        cooldown.tryUse(PLAYER);
        clock.now = NOW.plusSeconds(3);

        assertTrue(cooldown.tryUse(PLAYER));
    }

    @Test
    void secondsLeftRoundsUpAndEventuallyIsZero() {
        TestClock clock = new TestClock(NOW);
        Cooldown cooldown = new Cooldown(Duration.ofSeconds(3), clock);
        cooldown.tryUse(PLAYER);
        clock.now = NOW.plusMillis(1800);

        assertEquals(2, cooldown.secondsLeft(PLAYER));
        clock.now = NOW.plusSeconds(3);
        assertEquals(0, cooldown.secondsLeft(PLAYER));
    }

    @Test
    void forgetRemovesBlock() {
        Cooldown cooldown = cooldownAt(NOW);
        cooldown.tryUse(PLAYER);

        cooldown.forget(PLAYER);

        assertTrue(cooldown.tryUse(PLAYER));
    }

    private static Cooldown cooldownAt(Instant now) {
        return new Cooldown(Duration.ofSeconds(3), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static final class TestClock extends Clock {

        private Instant now;

        private TestClock(Instant now) {
            this.now = now;
        }

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
