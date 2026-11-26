package pl.mchosting.server.api;

/**
 * XP and coins for finishing a minigame match. The same rules for every minigame, so no mode is
 * "the one to farm". A match alone gives nothing, so two accounts cannot farm each other cheaply
 * for more than the small win bonus.
 */
public final class MatchRewards {

    static final Reward PLAYED = new Reward(20, 10);
    static final Reward WIN = new Reward(100, 50);
    static final Reward SECOND = new Reward(40, 20);
    static final Reward THIRD = new Reward(20, 10);
    static final Reward PER_KILL = new Reward(5, 2);
    static final int MAX_PAID_KILLS = 10;
    /** Second and third place only pay when at least this many players started. */
    static final int PODIUM_MIN_PLAYERS = 4;

    private MatchRewards() {}

    public static Reward rewardFor(MatchResult result) {
        if (result.players() < 2) {
            return Reward.NONE;
        }
        Reward reward = PLAYED;
        if (result.won()) {
            reward = reward.plus(WIN);
        } else if (result.players() >= PODIUM_MIN_PLAYERS && result.placement() == 2) {
            reward = reward.plus(SECOND);
        } else if (result.players() >= PODIUM_MIN_PLAYERS && result.placement() == 3) {
            reward = reward.plus(THIRD);
        }
        int paidKills = Math.min(result.kills(), MAX_PAID_KILLS);
        return reward.plus(new Reward(PER_KILL.xp() * paidKills, PER_KILL.coins() * paidKills));
    }
}
