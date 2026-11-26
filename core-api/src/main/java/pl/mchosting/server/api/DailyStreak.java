package pl.mchosting.server.api;

import java.time.LocalDate;

/**
 * Daily reward streak.
 *
 * @param lastClaim day of the last claimed reward, or null if the player never claimed one
 * @param streak days in a row with a claimed reward, 0 if never claimed
 */
public record DailyStreak(LocalDate lastClaim, int streak) {

    public static final DailyStreak NONE = new DailyStreak(null, 0);

    public DailyStreak {
        if (streak < 0) {
            throw new IllegalArgumentException("streak must not be negative");
        }
        if ((lastClaim == null) != (streak == 0)) {
            throw new IllegalArgumentException("streak and lastClaim must both be set or both empty");
        }
    }

    /** Streak as the player sees it on {@code today}: 0 if they missed a day. */
    public int currentStreak(LocalDate today) {
        if (lastClaim == null || lastClaim.isBefore(today.minusDays(1))) {
            return 0;
        }
        return streak;
    }
}
