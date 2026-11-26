package pl.mchosting.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.api.DailyClaim;
import pl.mchosting.server.api.DailyReward;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.Reward;
import pl.mchosting.server.api.Stats;

class ProfileManagerTest {

    /** 23:30 UTC on 26.11 is already 27.11 in Poland. */
    private static final Instant NOW = Instant.parse("2026-11-26T23:30:00Z");

    private final UUID id = UUID.randomUUID();
    private final MemoryStore store = new MemoryStore();
    private final List<Integer> levelUps = new ArrayList<>();
    private final MutableClock clock = new MutableClock(NOW);
    private final ProfileManager manager = new ProfileManager(
            store, Runnable::run, clock, Logger.getAnonymousLogger(), (player, level) -> levelUps.add(level));

    @Test
    void newPlayerGetsFreshAccount() throws IOException {
        join();
        PlayerProfile profile = manager.profile(id).orElseThrow();
        assertEquals(0, profile.totalXp());
        assertEquals(NOW, profile.firstJoin());
    }

    @Test
    void accountSurvivesLeavingAndJoining() throws IOException {
        join();
        manager.grant(id, new Reward(300, 25));
        manager.unload(id);
        assertEquals(Optional.empty(), manager.profile(id));
        join();
        assertEquals(25, manager.profile(id).orElseThrow().coins());
    }

    @Test
    void joinWithoutPreloadIsRefused() {
        assertFalse(manager.activate(id));
    }

    @Test
    void stalePreloadIsForgotten() throws IOException {
        manager.preload(id);
        clock.advance(Duration.ofMinutes(2));
        manager.forgetStalePending(Duration.ofMinutes(1));
        assertFalse(manager.activate(id));
    }

    @Test
    void loadErrorIsReported() {
        store.failLoads = true;
        assertThrows(IOException.class, () -> manager.preload(id));
    }

    @Test
    void changesNeedOnlinePlayer() {
        assertThrows(IllegalStateException.class, () -> manager.addXp(id, 10));
    }

    @Test
    void coinsCannotGoBelowZero() throws IOException {
        join();
        manager.changeCoins(id, 10);
        assertThrows(IllegalStateException.class, () -> manager.changeCoins(id, -11));
        assertEquals(10, manager.profile(id).orElseThrow().coins());
    }

    @Test
    void levelUpIsAnnounced() throws IOException {
        join();
        manager.addXp(id, 50);
        assertTrue(levelUps.isEmpty());
        manager.addXp(id, 250);
        assertEquals(List.of(3), levelUps);
    }

    @Test
    void dailyRewardOncePerPolishDay() throws IOException {
        join();
        DailyClaim.Claimed first = assertInstanceOf(DailyClaim.Claimed.class, manager.claimDaily(id));
        assertEquals(LocalDate.of(2026, 11, 27), first.streak().lastClaim());
        assertEquals(
                DailyReward.rewardForDay(1).coins(),
                manager.profile(id).orElseThrow().coins());
        assertInstanceOf(DailyClaim.AlreadyClaimed.class, manager.claimDaily(id));
        clock.advance(Duration.ofDays(1));
        DailyClaim.Claimed second = assertInstanceOf(DailyClaim.Claimed.class, manager.claimDaily(id));
        assertEquals(2, second.streak().streak());
    }

    @Test
    void onlyChangedAccountsAreSaved() throws IOException {
        join();
        manager.saveChanged(); // new account is written once
        int saves = store.saves;
        manager.saveChanged();
        assertEquals(saves, store.saves);
        manager.recordStat(id, Stats.wins(Stats.DUELS), 1);
        manager.saveChanged();
        assertEquals(saves + 1, store.saves);
        assertEquals(1, store.saved.get(id).stat(Stats.wins(Stats.DUELS)));
    }

    @Test
    void saveErrorDoesNotBreakTheServer() throws IOException {
        join();
        store.failSaves = true;
        manager.addXp(id, 10);
        manager.saveChanged();
        manager.unload(id);
    }

    @Test
    void tutorialIsRemembered() throws IOException {
        join();
        manager.completeTutorial(id);
        manager.unload(id);
        join();
        assertTrue(manager.profile(id).orElseThrow().tutorialDone());
    }

    private void join() throws IOException {
        manager.preload(id);
        assertTrue(manager.activate(id));
    }

    private static final class MemoryStore implements ProfileStore {
        final Map<UUID, PlayerProfile> saved = new HashMap<>();
        boolean failLoads;
        boolean failSaves;
        int saves;

        @Override
        public Optional<PlayerProfile> load(UUID playerId) throws IOException {
            if (failLoads) {
                throw new IOException("disk on fire");
            }
            return Optional.ofNullable(saved.get(playerId));
        }

        @Override
        public void save(PlayerProfile profile) throws IOException {
            if (failSaves) {
                throw new IOException("disk full");
            }
            saves++;
            saved.put(profile.playerId(), profile);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
