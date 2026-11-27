package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelProgressTest {

    @Test
    void xpToNextIsRemainingXpForTheLevel() {
        assertEquals(250, new LevelProgress(3, 50, 300).xpToNext());
    }

    @Test
    void fractionShowsProgressThroughTheLevel() {
        assertEquals(0.5, new LevelProgress(2, 100, 200).fraction(), 1e-9);
        assertEquals(0.0, new LevelProgress(2, 0, 200).fraction(), 1e-9);
    }

    @Test
    void maxLevelHasFullFractionAndNoXpRemaining() {
        LevelProgress progress = new LevelProgress(10, 0, 0);
        assertTrue(progress.isMaxLevel());
        assertEquals(1.0, progress.fraction(), 1e-9);
        assertEquals(0, progress.xpToNext());
    }

    @Test
    void ordinaryLevelIsNotMaxLevel() {
        assertFalse(new LevelProgress(3, 50, 300).isMaxLevel());
    }
}
