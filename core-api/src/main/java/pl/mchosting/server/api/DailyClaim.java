package pl.mchosting.server.api;

/** Result of trying to claim the daily reward. */
public sealed interface DailyClaim {

    /** Streak after the attempt. */
    DailyStreak streak();

    /** The reward was given. {@code streak().streak()} is the day of the streak it was for. */
    record Claimed(DailyStreak streak, Reward reward) implements DailyClaim {}

    /** The player already took today's reward. */
    record AlreadyClaimed(DailyStreak streak) implements DailyClaim {}
}
