package pl.mchosting.server.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.mchosting.server.api.DailyClaim;
import pl.mchosting.server.api.DailyReward;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;
import pl.mchosting.server.api.Reward;
import pl.mchosting.server.api.StatKey;

/**
 * Accounts of players on this server. Loading and saving goes through one background executor,
 * so a save from leaving always finishes before the load of the next join of the same player.
 *
 * <p>Life of an account: {@link #preload} (async pre-login) → {@link #activate} (join) → changes in
 * memory, saved every few minutes → {@link #unload} (quit, saved).
 */
final class ProfileManager implements ProfileService {

    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(10);

    private record Pending(PlayerProfile profile, Instant loadedAt) {}

    private final ProfileStore store;
    private final Executor io;
    private final Clock clock;
    private final Logger logger;
    private final LevelUpNotifier levelUps;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerProfile> online = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    ProfileManager(ProfileStore store, Executor io, Clock clock, Logger logger, LevelUpNotifier levelUps) {
        this.store = store;
        this.io = io;
        this.clock = clock;
        this.logger = logger;
        this.levelUps = levelUps;
    }

    /**
     * Loads the account before the player joins. Blocks, so only call it from the async pre-login
     * event.
     *
     * @throws IOException if the account could not be read; the player must not join then, or an
     *     empty account would overwrite the real one
     */
    void preload(UUID playerId) throws IOException {
        CompletableFuture<Optional<PlayerProfile>> load = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return store.load(playerId);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                io);
        Optional<PlayerProfile> stored;
        try {
            stored = load.get(LOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IOException("could not load account of " + playerId, e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("loading account of " + playerId + " took too long", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while loading account of " + playerId, e);
        }
        PlayerProfile profile = stored.orElseGet(() -> PlayerProfile.fresh(playerId, clock.instant()));
        pending.put(playerId, new Pending(profile, clock.instant()));
        if (stored.isEmpty()) {
            dirty.add(playerId);
        }
    }

    /**
     * Makes a preloaded account the live one when the player joins.
     *
     * @return false if nothing was preloaded (the player should be kicked and try again)
     */
    boolean activate(UUID playerId) {
        if (online.containsKey(playerId)) {
            return true;
        }
        Pending loaded = pending.remove(playerId);
        if (loaded == null) {
            return false;
        }
        online.put(playerId, loaded.profile());
        return true;
    }

    /** Drops the account of a player who left and saves it in the background. */
    void unload(UUID playerId) {
        PlayerProfile profile = online.remove(playerId);
        dirty.remove(playerId);
        if (profile != null) {
            saveLater(profile);
        }
    }

    /** Saves accounts changed since the last save. Call regularly from a timer. */
    void saveChanged() {
        for (UUID playerId : List.copyOf(dirty)) {
            dirty.remove(playerId);
            PlayerProfile profile = online.get(playerId);
            if (profile != null) {
                saveLater(profile);
            }
        }
    }

    /** Forgets preloaded accounts of players who never finished joining (kicked, lost connection). */
    void forgetStalePending(Duration maxAge) {
        Instant limit = clock.instant().minus(maxAge);
        pending.values().removeIf(entry -> entry.loadedAt().isBefore(limit));
    }

    /** Queues a save of every online account; used when the server stops. */
    void saveAll() {
        dirty.clear();
        online.values().forEach(this::saveLater);
    }

    @Override
    public Optional<PlayerProfile> profile(UUID playerId) {
        return Optional.ofNullable(online.get(playerId));
    }

    @Override
    public PlayerProfile addXp(UUID playerId, long xp) {
        return update(playerId, profile -> profile.withXpAdded(xp));
    }

    @Override
    public PlayerProfile changeCoins(UUID playerId, long delta) {
        return update(playerId, profile -> {
            if (profile.coins() + delta < 0) {
                throw new IllegalStateException("not enough coins");
            }
            return profile.withCoinsChanged(delta);
        });
    }

    @Override
    public PlayerProfile grant(UUID playerId, Reward reward) {
        return update(playerId, profile -> profile.withReward(reward));
    }

    @Override
    public PlayerProfile recordStat(UUID playerId, StatKey key, long value) {
        return update(playerId, profile -> profile.withStat(key, value));
    }

    @Override
    public DailyClaim claimDaily(UUID playerId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), DailyReward.NETWORK_ZONE);
        DailyClaim[] result = new DailyClaim[1];
        update(playerId, profile -> {
            DailyClaim claim = DailyReward.claim(profile.daily(), today);
            result[0] = claim;
            return switch (claim) {
                case DailyClaim.Claimed claimed ->
                    profile.withDaily(claimed.streak()).withReward(claimed.reward());
                case DailyClaim.AlreadyClaimed _ -> profile;
            };
        });
        return result[0];
    }

    @Override
    public PlayerProfile completeTutorial(UUID playerId) {
        return update(playerId, PlayerProfile::withTutorialDone);
    }

    private PlayerProfile update(UUID playerId, UnaryOperator<PlayerProfile> change) {
        int[] levelBefore = new int[1];
        PlayerProfile updated = online.computeIfPresent(playerId, (id, profile) -> {
            levelBefore[0] = profile.level();
            return change.apply(profile);
        });
        if (updated == null) {
            throw new IllegalStateException("player " + playerId + " is not online");
        }
        dirty.add(playerId);
        if (updated.level() > levelBefore[0]) {
            levelUps.levelUp(playerId, updated.level());
        }
        return updated;
    }

    private void saveLater(PlayerProfile profile) {
        io.execute(() -> {
            try {
                store.save(profile);
            } catch (IOException | RuntimeException e) {
                logger.log(Level.SEVERE, "Could not save account of " + profile.playerId(), e);
            }
        });
    }
}
