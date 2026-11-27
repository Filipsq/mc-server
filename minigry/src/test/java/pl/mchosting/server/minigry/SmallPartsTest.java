package pl.mchosting.server.minigry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SmallPartsTest {

    private static final Instant T0 = Instant.parse("2026-11-27T18:00:00Z");

    @Test
    void killGoesToRecentAttacker() {
        KillCredit credit = new KillCredit(Duration.ofSeconds(5));
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        credit.hit(victim, attacker, T0);
        assertEquals(Optional.of(attacker), credit.killer(victim, T0.plusSeconds(5)));
        assertEquals(Optional.empty(), credit.killer(victim, T0.plusSeconds(6)));
        credit.hit(victim, victim, T0.plusSeconds(6));
        assertEquals(Optional.empty(), credit.killer(victim, T0.plusSeconds(6)));
        credit.forget(victim);
        assertEquals(Optional.empty(), credit.killer(victim, T0));
    }

    @Test
    void footprintInTheMiddleOfABlock() {
        assertEquals(Set.of(new BlockPos(10, 63, -5)), Footprint.under(10.5, 64.0, -4.5));
    }

    @Test
    void footprintOnACornerTouchesFourBlocks() {
        assertEquals(
                Set.of(
                        new BlockPos(9, 63, 4),
                        new BlockPos(10, 63, 4),
                        new BlockPos(9, 63, 5),
                        new BlockPos(10, 63, 5)),
                Footprint.under(10.0, 64.0, 5.0));
    }

    @Test
    void footprintInTheAirIsTheBlockAroundTheFeet() {
        // Mid-jump the feet are inside an air block, so nothing gets removed under a jumping player.
        assertEquals(65, Footprint.under(0.5, 65.5, 0.5).iterator().next().y());
    }

    @Test
    void boxFromCornersInAnyOrder() {
        BlockBox box = BlockBox.between(new BlockPos(5, 70, -3), new BlockPos(-5, 60, 3));
        assertTrue(box.contains(new BlockPos(-5, 60, -3)));
        assertTrue(box.contains(new BlockPos(5, 70, 3)));
        assertFalse(box.contains(new BlockPos(6, 65, 0)));
        assertEquals(11L * 11 * 7, box.volume());
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(new BlockPos(1, 0, 0), new BlockPos(0, 0, 0)));
    }

    @Test
    void arenaPoolHandsOutFreeArenas() {
        ArenaPool pool = new ArenaPool();
        pool.setReady("spleef1", GameType.SPLEEF, true);
        pool.setReady("tnt1", GameType.TNT_RUN, true);
        assertEquals(Optional.of("spleef1"), pool.claim(GameType.SPLEEF));
        assertEquals(Optional.empty(), pool.claim(GameType.SPLEEF));
        assertTrue(pool.hasArena(GameType.SPLEEF));
        assertEquals(0, pool.free(GameType.SPLEEF));
        pool.release("spleef1");
        assertEquals(1, pool.free(GameType.SPLEEF));
        pool.setReady("spleef1", GameType.SPLEEF, false);
        assertFalse(pool.hasArena(GameType.SPLEEF));
        assertEquals(1, pool.free(GameType.TNT_RUN));
    }

    @Test
    void cooldownBlocksUntilItEnds() {
        UUID player = UUID.randomUUID();
        Cooldown cooldown = new Cooldown(Duration.ofSeconds(10), Clock.fixed(T0, ZoneOffset.UTC));
        assertTrue(cooldown.isReady(player));
        assertTrue(cooldown.tryUse(player));
        assertFalse(cooldown.tryUse(player));
        assertEquals(Duration.ofSeconds(10), cooldown.remaining(player));
        cooldown.forget(player);
        assertTrue(cooldown.isReady(player));
    }

    @Test
    void gameTypesFollowThePlan() {
        assertEquals(Optional.of(GameType.TNT_RUN), GameType.byId("tntrun"));
        assertEquals(Optional.empty(), GameType.byId("TNT_RUN"));
        for (GameType type : GameType.values()) {
            assertTrue(type.minPlayers() >= 2 && type.maxPlayers() >= type.minPlayers(), type.name());
        }
        assertEquals(16, GameType.SPLEEF.maxPlayers());
        assertEquals(24, GameType.TNT_RUN.maxPlayers());
    }
}
