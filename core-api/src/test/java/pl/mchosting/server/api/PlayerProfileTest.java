package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProfileTest {

    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-27T10:00:00Z");

    @Test
    void freshProfileStartsEmpty() {
        PlayerProfile profile = PlayerProfile.fresh(id, now);
        assertEquals(0, profile.totalXp());
        assertEquals(0, profile.coins());
        assertEquals(1, profile.level());
        assertEquals(DailyStreak.NONE, profile.daily());
        assertEquals(now, profile.firstJoin());
        assertFalse(profile.tutorialDone());
        assertTrue(profile.stats().isEmpty());
    }

    @Test
    void addingXpRaisesLevel() {
        assertEquals(3, PlayerProfile.fresh(id, now).withXpAdded(300).level());
    }

    @Test
    void coinsCannotGoNegative() {
        PlayerProfile profile = PlayerProfile.fresh(id, now).withCoinsChanged(10);
        assertThrows(IllegalArgumentException.class, () -> profile.withCoinsChanged(-11));
    }

    @Test
    void rewardAddsXpAndCoins() {
        PlayerProfile profile = PlayerProfile.fresh(id, now).withReward(new Reward(150, 40));
        assertEquals(150, profile.totalXp());
        assertEquals(40, profile.coins());
    }

    @Test
    void countersAddUp() {
        StatKey wins = Stats.wins(Stats.SPLEEF);
        PlayerProfile profile = PlayerProfile.fresh(id, now).withStat(wins, 1).withStat(wins, 2);
        assertEquals(3, profile.stat(wins));
    }

    @Test
    void bestTimeKeepsTheLowest() {
        StatKey time = Stats.bestTimeMillis("las");
        PlayerProfile profile =
                PlayerProfile.fresh(id, now).withStat(time, 50_000).withStat(time, 70_000);
        assertEquals(50_000, profile.stat(time));
        assertEquals(42_000, profile.withStat(time, 42_000).stat(time));
    }

    @Test
    void bestStreakKeepsTheHighest() {
        StatKey streak = Stats.bestWinStreak(Stats.DUELS);
        PlayerProfile profile = PlayerProfile.fresh(id, now).withStat(streak, 5).withStat(streak, 3);
        assertEquals(5, profile.stat(streak));
    }

    @Test
    void missingStatIsZero() {
        PlayerProfile profile = PlayerProfile.fresh(id, now);
        assertEquals(0, profile.stat(Stats.kills(Stats.DUELS)));
        assertFalse(profile.hasStat(Stats.kills(Stats.DUELS)));
    }

    @Test
    void statsCannotBeChangedFromOutside() {
        PlayerProfile profile = PlayerProfile.fresh(id, now).withStat(Stats.wins(Stats.SPLEEF), 1);
        assertThrows(UnsupportedOperationException.class, () -> profile.stats().put("spleef.wins", 99L));
    }

    @Test
    void rejectsBadStoredStats() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerProfile(id, 0, 0, DailyStreak.NONE, Map.of("no dots", 1L), now, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerProfile(id, 0, 0, DailyStreak.NONE, Map.of("spleef.wins", -1L), now, false));
    }

    @Test
    void tutorialCanBeMarkedDone() {
        assertTrue(PlayerProfile.fresh(id, now).withTutorialDone().tutorialDone());
    }
}
