package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatchRewardsTest {

    @Test
    void playingAloneGivesNothing() {
        assertEquals(Reward.NONE, MatchRewards.rewardFor(new MatchResult(1, 1, 0)));
    }

    @Test
    void everyPlayerGetsSomething() {
        assertEquals(MatchRewards.PLAYED, MatchRewards.rewardFor(new MatchResult(8, 8, 0)));
    }

    @Test
    void winnerGetsBonus() {
        assertEquals(MatchRewards.PLAYED.plus(MatchRewards.WIN), MatchRewards.rewardFor(new MatchResult(2, 1, 0)));
    }

    @Test
    void podiumOnlyInBiggerMatches() {
        assertEquals(MatchRewards.PLAYED, MatchRewards.rewardFor(new MatchResult(3, 2, 0)));
        assertEquals(MatchRewards.PLAYED.plus(MatchRewards.SECOND), MatchRewards.rewardFor(new MatchResult(4, 2, 0)));
        assertEquals(MatchRewards.PLAYED.plus(MatchRewards.THIRD), MatchRewards.rewardFor(new MatchResult(10, 3, 0)));
    }

    @Test
    void killsArePaidUpToCap() {
        Reward capped = MatchRewards.rewardFor(new MatchResult(16, 5, MatchRewards.MAX_PAID_KILLS));
        assertEquals(capped, MatchRewards.rewardFor(new MatchResult(16, 5, 15)));
        assertTrue(capped.coins() > MatchRewards.PLAYED.coins());
    }

    @Test
    void rejectsImpossibleResults() {
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(2, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(2, 1, -1));
    }
}
