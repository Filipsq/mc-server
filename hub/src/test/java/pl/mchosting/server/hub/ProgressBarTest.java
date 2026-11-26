package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProgressBarTest {

    @Test
    void fillsProportionally() {
        assertEquals(0, ProgressBar.filledCells(0.0, 10));
        assertEquals(5, ProgressBar.filledCells(0.5, 10));
        assertEquals(10, ProgressBar.filledCells(1.0, 10));
    }

    @Test
    void neverShowsFullBeforeTheLevelIsDone() {
        assertEquals(9, ProgressBar.filledCells(0.999, 10));
    }

    @Test
    void outOfRangeIsClamped() {
        assertEquals(0, ProgressBar.filledCells(-1, 10));
        assertEquals(10, ProgressBar.filledCells(3, 10));
    }

    @Test
    void renderHasExactWidth() {
        String bar = ProgressBar.render(0.3, 10).replaceAll("<[^>]+>", "");
        assertEquals("■■■□□□□□□□", bar);
    }

    @Test
    void rejectsZeroWidth() {
        assertThrows(IllegalArgumentException.class, () -> ProgressBar.filledCells(0.5, 0));
    }
}
