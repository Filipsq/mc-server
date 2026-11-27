package pl.mchosting.server.minigry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillRatingTest {

    @Test
    void winnerScoresMaxLastScoresZero() {
        assertEquals(SkillRating.MAX, SkillRating.matchPoints(16, 1));
        assertEquals(0, SkillRating.matchPoints(16, 16));
        assertEquals(500, SkillRating.matchPoints(3, 2));
        assertEquals(1000, SkillRating.matchPoints(2, 1));
    }

    @Test
    void newPlayersStartInTheMiddle() {
        assertEquals(SkillRating.START, SkillRating.rating(0, 0));
    }

    @Test
    void oneLuckyWinDoesNotMakeYouTheBest() {
        int afterOneWin = SkillRating.rating(1, 1000);
        assertTrue(afterOneWin > 500 && afterOneWin < 700, "rating " + afterOneWin);
    }

    @Test
    void manyWinsGetCloseToMax() {
        assertTrue(SkillRating.rating(200, 200 * 1000) > 980);
        assertTrue(SkillRating.rating(200, 0) < 20);
    }

    @Test
    void newcomersAreTheFirstTenMatches() {
        assertTrue(SkillRating.isNewcomer(0));
        assertTrue(SkillRating.isNewcomer(9));
        assertFalse(SkillRating.isNewcomer(10));
    }

    @Test
    void rejectsImpossibleInput() {
        assertThrows(IllegalArgumentException.class, () -> SkillRating.matchPoints(1, 1));
        assertThrows(IllegalArgumentException.class, () -> SkillRating.matchPoints(4, 5));
        assertThrows(IllegalArgumentException.class, () -> SkillRating.rating(1, 1001));
        assertThrows(IllegalArgumentException.class, () -> SkillRating.rating(-1, 0));
    }
}
