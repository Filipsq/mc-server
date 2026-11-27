package pl.mchosting.server.minigry;

/**
 * Ranking points of ranked duels (Elo). Everyone starts at {@link #START}. Beating a stronger
 * player gives more points than beating a weaker one; a draw moves points towards the weaker
 * player. New players move faster so they reach their real level after a few duels.
 */
final class EloRating {

    static final int START = 1000;
    /** Points at stake in one duel for players with fewer than {@link #SETTLED_GAMES} ranked duels. */
    static final int NEW_PLAYER_K = 40;

    static final int K = 20;
    static final int SETTLED_GAMES = 10;

    private EloRating() {}

    /** Chance (0..1) that a player rated {@code rating} beats one rated {@code opponent}. */
    static double expected(int rating, int opponent) {
        return 1.0 / (1.0 + Math.pow(10, (opponent - rating) / 400.0));
    }

    /**
     * How the player's points change after one duel. Never takes the player below 0.
     *
     * @param score 1 for a win, 0.5 for a draw, 0 for a loss
     * @param rankedGames ranked duels the player finished before this one
     */
    static int change(int rating, int opponent, double score, long rankedGames) {
        if (score != 0 && score != 0.5 && score != 1) {
            throw new IllegalArgumentException("score must be 0, 0.5 or 1: " + score);
        }
        if (rating < 0 || opponent < 0 || rankedGames < 0) {
            throw new IllegalArgumentException("bad rating " + rating + " vs " + opponent);
        }
        int k = rankedGames < SETTLED_GAMES ? NEW_PLAYER_K : K;
        int change = (int) Math.round(k * (score - expected(rating, opponent)));
        return Math.max(change, -rating);
    }

    /** Points from the stored counters: start plus everything gained minus everything lost. */
    static int rating(long gained, long lost) {
        if (gained < 0 || lost < 0) {
            throw new IllegalArgumentException("bad counters " + gained + " " + lost);
        }
        long rating = START + gained - lost;
        return Math.clamp(rating, 0, Integer.MAX_VALUE);
    }
}
