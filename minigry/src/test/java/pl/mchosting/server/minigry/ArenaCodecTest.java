package pl.mchosting.server.minigry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArenaCodecTest {

    @Test
    void roundTrip() {
        ArenaConfig arena = ArenaConfig.create("tnt1", GameType.TNT_RUN, "minigry")
                .withSpawnAdded(new SpawnPoint(10.5, 80, -3.25, 90, 0))
                .withSpawnAdded(new SpawnPoint(-10.5, 80, 3.75, -90, 10))
                .withRegion(BlockBox.between(new BlockPos(-30, 60, -30), new BlockPos(30, 79, 30)))
                .withEliminationY(55)
                .withEnabled(true);
        assertEquals(arena, ArenaCodec.decode("tnt1", ArenaCodec.encode(arena)));
    }

    @Test
    void freshArenaRoundTrip() {
        ArenaConfig arena = ArenaConfig.create("spleef1", GameType.SPLEEF, "minigry");
        assertEquals(arena, ArenaCodec.decode("spleef1", ArenaCodec.encode(arena)));
    }

    @Test
    void readsHandWrittenValues() {
        ArenaConfig arena = ArenaCodec.decode(
                "s",
                Map.of(
                        "game",
                        "spleef",
                        "world",
                        "w",
                        "enabled",
                        true,
                        "elimination-y",
                        40,
                        "region",
                        "0 40 0  10 50 10",
                        "spawns",
                        List.of("5 51 5 0 0")));
        assertEquals(40, arena.eliminationY());
        assertEquals(new SpawnPoint(5, 51, 5, 0, 0), arena.spawns().getFirst());
    }

    @Test
    void rejectsBrokenValues() {
        assertThrows(
                IllegalArgumentException.class, () -> ArenaCodec.decode("s", Map.of("game", "golf", "world", "w")));
        assertThrows(IllegalArgumentException.class, () -> ArenaCodec.decode("s", Map.of("game", "spleef")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArenaCodec.decode("s", Map.of("game", "spleef", "world", "w", "region", "1 2 3")));
    }
}
