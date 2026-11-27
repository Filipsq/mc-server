package pl.mchosting.server.minigry;

import java.time.Duration;

/**
 * How picky the matchmaker is. Players start with opponents close to their rating; the longer
 * they wait, the wider the range, so nobody waits forever on a quiet evening.
 *
 * @param fillWait a match with fewer than the maximum players starts once its oldest player waited this long
 * @param baseWindow allowed rating difference right after joining
 * @param windowGrowth how much the allowed difference grows every {@code growthStep}
 * @param growthStep how often the allowed difference grows
 * @param anyoneAfter after waiting this long a player is matched with any rating
 * @param mixNewcomersAfter after waiting this long newcomers and experienced players can meet
 */
record MatchmakingRules(
        Duration fillWait,
        int baseWindow,
        int windowGrowth,
        Duration growthStep,
        Duration anyoneAfter,
        Duration mixNewcomersAfter) {

    static final MatchmakingRules DEFAULT = new MatchmakingRules(
            Duration.ofSeconds(15), 150, 50, Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(45));

    MatchmakingRules {
        if (baseWindow < 0 || windowGrowth < 0 || growthStep.isZero() || growthStep.isNegative()) {
            throw new IllegalArgumentException("bad matchmaking rules");
        }
    }

    /** Largest rating difference accepted for a player who waited {@code waited}. */
    int window(Duration waited) {
        if (waited.compareTo(anyoneAfter) >= 0) {
            return Integer.MAX_VALUE;
        }
        long steps = waited.toMillis() / growthStep.toMillis();
        return (int) Math.min(Integer.MAX_VALUE, baseWindow + steps * windowGrowth);
    }
}
