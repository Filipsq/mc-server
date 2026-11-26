package pl.mchosting.server.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and changes accounts of players online on this server. The core plugin provides it
 * through Bukkit's services manager; other plugins only ever see this interface.
 *
 * <p>All methods are thread-safe and never touch the disk or the network, so they can be called
 * from the main thread. Changing the account of a player who is not online throws {@link
 * IllegalStateException}.
 */
public interface ProfileService {

    /** Account of an online player, or empty if the player is not loaded. */
    Optional<PlayerProfile> profile(UUID playerId);

    /** Adds network XP and returns the updated account. */
    PlayerProfile addXp(UUID playerId, long xp);

    /**
     * Adds (positive) or takes (negative) hub coins.
     *
     * @throws IllegalStateException if the player would go below zero coins
     */
    PlayerProfile changeCoins(UUID playerId, long delta);

    /** Gives XP and coins at once, for example {@link MatchRewards#rewardFor(MatchResult)}. */
    PlayerProfile grant(UUID playerId, Reward reward);

    /** Records a statistic, see {@link StatKey.Kind} for how it is merged. */
    PlayerProfile recordStat(UUID playerId, StatKey key, long value);

    /** Claims today's daily reward (days follow Polish time) and gives it if not taken yet. */
    DailyClaim claimDaily(UUID playerId);

    /** Marks the hub welcome as seen. */
    PlayerProfile completeTutorial(UUID playerId);
}
