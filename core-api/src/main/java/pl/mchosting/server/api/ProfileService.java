package pl.mchosting.server.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and changes player profiles. The core plugin provides it through Bukkit's services
 * manager; other plugins only ever see this interface.
 */
public interface ProfileService {

    /** Profile of an online player, or empty if the player is not loaded. */
    Optional<PlayerProfile> profile(UUID playerId);

    /** Adds network XP and returns the updated profile. */
    PlayerProfile addXp(UUID playerId, long xp);

    /**
     * Adds (positive) or takes (negative) hub coins.
     *
     * @throws IllegalStateException if the player would go below zero coins
     */
    PlayerProfile changeCoins(UUID playerId, long delta);
}
