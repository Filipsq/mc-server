package pl.mchosting.server.minigry;

import pl.mchosting.server.api.Reward;

/**
 * Parkour pays only the first time a player finishes a course, more for harder courses. Running
 * the same course again is for records, so nobody farms XP on the easiest one.
 */
final class ParkourRewards {

    static final int XP_PER_LEVEL = 30;
    static final int COINS_PER_LEVEL = 15;

    private ParkourRewards() {}

    static Reward firstFinish(int difficulty) {
        if (difficulty < ParkourCourse.MIN_DIFFICULTY || difficulty > ParkourCourse.MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be 1-10: " + difficulty);
        }
        return new Reward((long) XP_PER_LEVEL * difficulty, (long) COINS_PER_LEVEL * difficulty);
    }
}
