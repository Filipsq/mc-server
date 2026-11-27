package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatsTest {

    private static final List<String> MODES = List.of(Stats.SPLEEF, Stats.TNT_RUN, Stats.PARKOUR, Stats.DUELS);

    @Test
    void everyModeHasValidIdsForItsStats() {
        for (String mode : MODES) {
            assertValidId(mode, Stats.gamesPlayed(mode), "games");
            assertValidId(mode, Stats.wins(mode), "wins");
            assertValidId(mode, Stats.kills(mode), "kills");
            assertValidId(mode, Stats.deaths(mode), "deaths");
            assertValidId(mode, Stats.bestWinStreak(mode), "best_streak");
        }
    }

    @Test
    void statsHaveExpectedKinds() {
        for (String mode : MODES) {
            assertEquals(StatKey.Kind.COUNTER, Stats.gamesPlayed(mode).kind());
            assertEquals(StatKey.Kind.COUNTER, Stats.wins(mode).kind());
            assertEquals(StatKey.Kind.COUNTER, Stats.kills(mode).kind());
            assertEquals(StatKey.Kind.COUNTER, Stats.deaths(mode).kind());
            assertEquals(StatKey.Kind.HIGHEST, Stats.bestWinStreak(mode).kind());
        }
        assertEquals(StatKey.Kind.LOWEST, Stats.bestTimeMillis("las").kind());
    }

    @Test
    void parkourCourseTimeHasExpectedId() {
        assertEquals("parkour.time_las", Stats.bestTimeMillis("las").id());
    }

    @Test
    void statsHaveUniqueIdsWithinAMode() {
        for (String mode : MODES) {
            Set<String> ids = Set.of(
                    Stats.gamesPlayed(mode).id(),
                    Stats.wins(mode).id(),
                    Stats.kills(mode).id(),
                    Stats.deaths(mode).id(),
                    Stats.bestWinStreak(mode).id());
            assertEquals(5, ids.size());
        }
    }

    @Test
    void sameStatInDifferentModesHasDifferentKeys() {
        assertNotEquals(Stats.wins(Stats.SPLEEF), Stats.wins(Stats.DUELS));
    }

    private static void assertValidId(String mode, StatKey key, String stat) {
        assertEquals(mode + "." + stat, key.id());
        assertTrue(StatKey.isValidId(key.id()));
    }
}
