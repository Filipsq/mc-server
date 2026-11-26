package pl.mchosting.server.api;

/**
 * Network XP and hub coins given to a player in one go (a match, a quest, a daily reward).
 *
 * @param xp network XP, never negative
 * @param coins hub coins, never negative
 */
public record Reward(long xp, long coins) {

    public static final Reward NONE = new Reward(0, 0);

    public Reward {
        if (xp < 0 || coins < 0) {
            throw new IllegalArgumentException("reward must not be negative");
        }
    }

    public Reward plus(Reward other) {
        return new Reward(Math.addExact(xp, other.xp), Math.addExact(coins, other.coins));
    }

    public boolean isEmpty() {
        return xp == 0 && coins == 0;
    }
}
