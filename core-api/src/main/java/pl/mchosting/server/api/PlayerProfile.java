package pl.mchosting.server.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Network-wide player account: level and hub coins shared by every mode.
 *
 * @param playerId Minecraft account id
 * @param totalXp all network XP ever earned (never goes down)
 * @param coins hub coins, earned by playing, never sold for real money
 */
public record PlayerProfile(UUID playerId, long totalXp, long coins) {

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        if (totalXp < 0) {
            throw new IllegalArgumentException("totalXp must not be negative");
        }
        if (coins < 0) {
            throw new IllegalArgumentException("coins must not be negative");
        }
    }

    public static PlayerProfile fresh(UUID playerId) {
        return new PlayerProfile(playerId, 0, 0);
    }

    public int level() {
        return LevelCurve.levelFor(totalXp);
    }

    public PlayerProfile withXpAdded(long xp) {
        if (xp < 0) {
            throw new IllegalArgumentException("xp must not be negative");
        }
        return new PlayerProfile(playerId, Math.addExact(totalXp, xp), coins);
    }

    public PlayerProfile withCoinsChanged(long delta) {
        return new PlayerProfile(playerId, totalXp, Math.addExact(coins, delta));
    }
}
