package pl.mchosting.server.api;

/**
 * Where a player is inside their current level.
 *
 * @param level current level
 * @param xpIntoLevel XP earned since reaching this level
 * @param xpForLevel XP this level needs in total, 0 at {@link LevelCurve#MAX_LEVEL}
 */
public record LevelProgress(int level, long xpIntoLevel, long xpForLevel) {

    public boolean isMaxLevel() {
        return xpForLevel == 0;
    }

    /** XP still missing to the next level, 0 at max level. */
    public long xpToNext() {
        return xpForLevel - xpIntoLevel;
    }

    /** Share of the level done, from 0.0 to 1.0 (1.0 at max level). */
    public double fraction() {
        return isMaxLevel() ? 1.0 : (double) xpIntoLevel / xpForLevel;
    }
}
