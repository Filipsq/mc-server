package pl.mchosting.server.api;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily reward rules. Claiming on the day after the last claim continues the streak, missing a
 * day starts again from day 1. Rewards grow over a 7-day cycle; day 7 is the big one.
 */
public final class DailyReward {

    public static final int CYCLE_DAYS = 7;

    /** Days of the whole network follow Polish time. */
    public static final ZoneId NETWORK_ZONE = ZoneId.of("Europe/Warsaw");

    private static final long[] COINS = {50, 60, 70, 80, 90, 100, 250};
    private static final long[] XP = {50, 50, 50, 50, 50, 50, 300};

    private DailyReward() {}

    public static DailyClaim claim(DailyStreak current, LocalDate today) {
        if (!canClaim(current, today)) {
            return new DailyClaim.AlreadyClaimed(current);
        }
        int day = current.currentStreak(today) + 1;
        DailyStreak next = new DailyStreak(today, day);
        return new DailyClaim.Claimed(next, rewardForDay(day));
    }

    /** True if today's reward is still waiting. */
    public static boolean canClaim(DailyStreak current, LocalDate today) {
        return current.lastClaim() == null || current.lastClaim().isBefore(today);
    }

    /** Reward for the {@code day}-th day of a streak (1 = first day). */
    public static Reward rewardForDay(int day) {
        if (day < 1) {
            throw new IllegalArgumentException("day starts at 1");
        }
        int index = (day - 1) % CYCLE_DAYS;
        return new Reward(XP[index], COINS[index]);
    }
}
