package pl.mchosting.server.minigry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaConfigTest {

    private static final SpawnPoint SPAWN = new SpawnPoint(0.5, 70, 0.5, 0, 0);
    private static final BlockBox REGION = BlockBox.between(new BlockPos(-20, 60, -20), new BlockPos(20, 70, 20));

    private static ArenaConfig complete() {
        return ArenaConfig.create("spleef1", GameType.SPLEEF, "minigry")
                .withSpawnAdded(SPAWN)
                .withRegion(REGION)
                .withEliminationY(55)
                .withEnabled(true);
    }

    @Test
    void newArenaListsEverythingMissing() {
        ArenaConfig arena = ArenaConfig.create("spleef1", GameType.SPLEEF, "minigry");
        assertEquals(3, arena.problems().size());
        assertFalse(arena.isReady());
    }

    @Test
    void completeAndEnabledIsReady() {
        assertTrue(complete().problems().isEmpty());
        assertTrue(complete().isReady());
        assertFalse(complete().withEnabled(false).isReady());
    }

    @Test
    void spawnBelowEliminationIsAProblem() {
        assertEquals(1, complete().withEliminationY(80).problems().size());
    }

    @Test
    void hugeRegionIsAProblem() {
        BlockBox huge = BlockBox.between(new BlockPos(-500, 0, -500), new BlockPos(500, 10, 500));
        assertEquals(1, complete().withRegion(huge).problems().size());
    }

    @Test
    void playersShareSpawnsWhenThereAreFew() {
        SpawnPoint second = new SpawnPoint(5.5, 70, 5.5, 180, 0);
        ArenaConfig arena = complete().withSpawnAdded(second);
        assertEquals(SPAWN, arena.spawnFor(0));
        assertEquals(second, arena.spawnFor(1));
        assertEquals(SPAWN, arena.spawnFor(2));
        assertThrows(
                IllegalStateException.class, () -> complete().withoutSpawns().spawnFor(0));
    }

    @Test
    void idMustBeSimple() {
        assertThrows(IllegalArgumentException.class, () -> ArenaConfig.create("Spleef 1", GameType.SPLEEF, "w"));
    }
}
