package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyRewardTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    @Test
    void firstClaimStartsStreak() {
        DailyClaim.Claimed claim =
                assertInstanceOf(DailyClaim.Claimed.class, DailyReward.claim(DailyStreak.NONE, MONDAY));
        assertEquals(new DailyStreak(MONDAY, 1), claim.streak());
        assertEquals(DailyReward.rewardForDay(1), claim.reward());
    }

    @Test
    void secondClaimSameDayIsRefused() {
        DailyStreak streak = new DailyStreak(MONDAY, 1);
        DailyClaim claim = DailyReward.claim(streak, MONDAY);
        assertInstanceOf(DailyClaim.AlreadyClaimed.class, claim);
        assertEquals(streak, claim.streak());
    }

    @Test
    void nextDayContinuesStreak() {
        DailyClaim claim = DailyReward.claim(new DailyStreak(MONDAY, 3), MONDAY.plusDays(1));
        assertEquals(new DailyStreak(MONDAY.plusDays(1), 4), claim.streak());
    }

    @Test
    void missedDayStartsOver() {
        DailyClaim claim = DailyReward.claim(new DailyStreak(MONDAY, 5), MONDAY.plusDays(2));
        assertEquals(new DailyStreak(MONDAY.plusDays(2), 1), claim.streak());
    }

    @Test
    void clockGoingBackDoesNotPayTwice() {
        assertInstanceOf(
                DailyClaim.AlreadyClaimed.class, DailyReward.claim(new DailyStreak(MONDAY, 2), MONDAY.minusDays(1)));
    }

    @Test
    void seventhDayIsTheBigOneAndCycleRepeats() {
        Reward first = DailyReward.rewardForDay(1);
        Reward seventh = DailyReward.rewardForDay(7);
        assertEquals(250, seventh.coins());
        assertEquals(first, DailyReward.rewardForDay(8));
        assertEquals(seventh, DailyReward.rewardForDay(14));
    }

    @Test
    void rewardsNeverShrinkWithinCycle() {
        for (int day = 2; day <= DailyReward.CYCLE_DAYS; day++) {
            Reward before = DailyReward.rewardForDay(day - 1);
            Reward now = DailyReward.rewardForDay(day);
            if (now.coins() < before.coins() || now.xp() < before.xp()) {
                throw new AssertionError("day " + day + " pays less than day " + (day - 1));
            }
        }
    }

    @Test
    void streakShownAsZeroAfterMissedDay() {
        DailyStreak streak = new DailyStreak(MONDAY, 4);
        assertEquals(4, streak.currentStreak(MONDAY));
        assertEquals(4, streak.currentStreak(MONDAY.plusDays(1)));
        assertEquals(0, streak.currentStreak(MONDAY.plusDays(2)));
    }

    @Test
    void rejectsInconsistentStreak() {
        assertThrows(IllegalArgumentException.class, () -> new DailyStreak(null, 2));
        assertThrows(IllegalArgumentException.class, () -> new DailyStreak(MONDAY, 0));
    }
}
