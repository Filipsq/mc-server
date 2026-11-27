package pl.mchosting.server.minigry;

/**
 * How good a player is at one game, from 0 to 1000. Each match scores the share of opponents the
 * player beat (winner 1000, last 0); the rating is the average of those scores, pulled towards
 * 500 for players with few matches so one lucky win does not put them with the best.
 *
 * <p>Only counters are stored on the account ({@code skill_points} and {@code games}), so the
 * rating can be recomputed anywhere from the profile.
 */
final class SkillRating {

    static final long MAX = 1000;
    static final long START = 500;
    /** Players with fewer matches than this play with each other first. */
    static final int NEWCOMER_GAMES = 10;
    /** How many imaginary average matches every player starts with. */
    static final int PRIOR_GAMES = 5;

    private SkillRating() {}

    /** Score for finishing on {@code placement} out of {@code players}. */
    static long matchPoints(int players, int placement) {
        if (players < 2 || placement < 1 || placement > players) {
            throw new IllegalArgumentException("bad placement " + placement + " of " + players);
        }
        return Math.round((double) MAX * (players - placement) / (players - 1));
    }

    /** Rating from the stored counters. */
    static int rating(long games, long points) {
        if (games < 0 || points < 0 || points > games * MAX) {
            throw new IllegalArgumentException("bad skill counters: games=" + games + " points=" + points);
        }
        return (int) ((points + START * PRIOR_GAMES) / (games + PRIOR_GAMES));
    }

    static boolean isNewcomer(long games) {
        return games < NEWCOMER_GAMES;
    }
}
