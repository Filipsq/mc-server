package pl.mchosting.server.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Network-wide player account shared by every mode: level, hub coins, daily streak and stats.
 * Nothing here can be bought for real money and nothing here gives an advantage in a fight.
 *
 * @param playerId Minecraft account id
 * @param totalXp all network XP ever earned (never goes down)
 * @param coins hub coins, earned by playing, never sold for real money
 * @param daily daily reward streak
 * @param stats statistics by {@link StatKey#id()}, never negative
 * @param firstJoin when the player first joined the network
 * @param tutorialDone whether the player already saw the hub welcome
 */
public record PlayerProfile(
        UUID playerId,
        long totalXp,
        long coins,
        DailyStreak daily,
        Map<String, Long> stats,
        Instant firstJoin,
        boolean tutorialDone) {

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(daily, "daily");
        Objects.requireNonNull(firstJoin, "firstJoin");
        if (totalXp < 0) {
            throw new IllegalArgumentException("totalXp must not be negative");
        }
        if (coins < 0) {
            throw new IllegalArgumentException("coins must not be negative");
        }
        stats = Map.copyOf(stats);
        for (Map.Entry<String, Long> stat : stats.entrySet()) {
            if (!StatKey.isValidId(stat.getKey()) || stat.getValue() < 0) {
                throw new IllegalArgumentException("bad stat " + stat.getKey() + "=" + stat.getValue());
            }
        }
    }

    public static PlayerProfile fresh(UUID playerId, Instant now) {
        return new PlayerProfile(playerId, 0, 0, DailyStreak.NONE, Map.of(), now, false);
    }

    public int level() {
        return LevelCurve.levelFor(totalXp);
    }

    public LevelProgress levelProgress() {
        return LevelCurve.progress(totalXp);
    }

    /** Stored value of {@code key}, 0 if the player has none yet. */
    public long stat(StatKey key) {
        return stats.getOrDefault(key.id(), 0L);
    }

    public boolean hasStat(StatKey key) {
        return stats.containsKey(key.id());
    }

    public PlayerProfile withXpAdded(long xp) {
        if (xp < 0) {
            throw new IllegalArgumentException("xp must not be negative");
        }
        return new PlayerProfile(playerId, Math.addExact(totalXp, xp), coins, daily, stats, firstJoin, tutorialDone);
    }

    /**
     * Adds (positive) or takes (negative) coins.
     *
     * @throws IllegalArgumentException if the result would be below zero
     */
    public PlayerProfile withCoinsChanged(long delta) {
        return new PlayerProfile(playerId, totalXp, Math.addExact(coins, delta), daily, stats, firstJoin, tutorialDone);
    }

    public PlayerProfile withReward(Reward reward) {
        return withXpAdded(reward.xp()).withCoinsChanged(reward.coins());
    }

    public PlayerProfile withDaily(DailyStreak streak) {
        return new PlayerProfile(playerId, totalXp, coins, streak, stats, firstJoin, tutorialDone);
    }

    /** Records {@code value} for {@code key}: added for counters, kept if better for best values. */
    public PlayerProfile withStat(StatKey key, long value) {
        Map<String, Long> updated = new HashMap<>(stats);
        updated.put(key.id(), key.merge(stats.get(key.id()), value));
        return new PlayerProfile(playerId, totalXp, coins, daily, updated, firstJoin, tutorialDone);
    }

    public PlayerProfile withTutorialDone() {
        return new PlayerProfile(playerId, totalXp, coins, daily, stats, firstJoin, true);
    }
}
