package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LevelCurveTest {

    @Test
    void newPlayerIsLevelOne() {
        assertEquals(1, LevelCurve.levelFor(0));
        assertEquals(1, LevelCurve.levelFor(99));
    }

    @Test
    void levelsNeedGrowingXp() {
        assertEquals(100, LevelCurve.xpToNext(1));
        assertEquals(200, LevelCurve.xpToNext(2));
        assertEquals(2, LevelCurve.levelFor(100));
        assertEquals(2, LevelCurve.levelFor(299));
        assertEquals(3, LevelCurve.levelFor(300));
    }

    @Test
    void totalXpMatchesSumOfSteps() {
        long sum = 0;
        for (int level = 1; level < 50; level++) {
            assertEquals(sum, LevelCurve.totalXpFor(level));
            sum += LevelCurve.xpToNext(level);
        }
    }

    @Test
    void levelIsCapped() {
        assertEquals(LevelCurve.MAX_LEVEL, LevelCurve.levelFor(Long.MAX_VALUE / 2));
    }

    @Test
    void rejectsNegativeXp() {
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.levelFor(-1));
    }

    @Test
    void progressInsideLevel() {
        LevelProgress progress = LevelCurve.progress(350);
        assertEquals(3, progress.level());
        assertEquals(50, progress.xpIntoLevel());
        assertEquals(300, progress.xpForLevel());
        assertEquals(250, progress.xpToNext());
    }

    @Test
    void progressAtMaxLevel() {
        LevelProgress progress = LevelCurve.progress(LevelCurve.totalXpFor(LevelCurve.MAX_LEVEL));
        assertEquals(LevelCurve.MAX_LEVEL, progress.level());
        assertEquals(0, progress.xpToNext());
        assertEquals(1.0, progress.fraction());
    }
}
