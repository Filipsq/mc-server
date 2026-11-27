package pl.mchosting.server.minigry;

import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.StatKey;
import pl.mchosting.server.api.Stats;

/**
 * Ranked Duels statistics on the player account, one set per kit. The account only keeps
 * counters that grow, so ranking points are stored as points gained and points lost.
 */
final class DuelStats {

    private DuelStats() {}

    static StatKey pointsGained(DuelKit kit) {
        return StatKey.counter(Stats.DUELS, "elo_gain_" + kit.id());
    }

    static StatKey pointsLost(DuelKit kit) {
        return StatKey.counter(Stats.DUELS, "elo_loss_" + kit.id());
    }

    static StatKey rankedGames(DuelKit kit) {
        return StatKey.counter(Stats.DUELS, "ranked_" + kit.id());
    }

    static StatKey rankedWins(DuelKit kit) {
        return StatKey.counter(Stats.DUELS, "ranked_wins_" + kit.id());
    }

    static int rating(PlayerProfile profile, DuelKit kit) {
        return EloRating.rating(profile.stat(pointsGained(kit)), profile.stat(pointsLost(kit)));
    }
}
