package pl.mchosting.server.api;

/**
 * How much network XP each level needs. Level 1 is the start; going from level n to n + 1 costs
 * {@code 100 * n} XP, so early levels come fast and later ones take longer.
 */
public final class LevelCurve {

    public static final int MAX_LEVEL = 500;

    private LevelCurve() {}

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static long xpToNext(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level starts at 1");
        }
        return 100L * level;
    }

    /** Total XP needed to reach {@code level} from zero. */
    public static long totalXpFor(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level starts at 1");
        }
        long n = level - 1L;
        return 100L * n * (n + 1) / 2;
    }

    /** Level reached with {@code totalXp}, capped at {@link #MAX_LEVEL}. */
    public static int levelFor(long totalXp) {
        if (totalXp < 0) {
            throw new IllegalArgumentException("totalXp must not be negative");
        }
        int level = 1;
        while (level < MAX_LEVEL && totalXpFor(level + 1) <= totalXp) {
            level++;
        }
        return level;
    }

    /** Level and progress inside it for {@code totalXp}. */
    public static LevelProgress progress(long totalXp) {
        int level = levelFor(totalXp);
        long into = totalXp - totalXpFor(level);
        if (level == MAX_LEVEL) {
            return new LevelProgress(level, into, 0);
        }
        return new LevelProgress(level, into, xpToNext(level));
    }
}
