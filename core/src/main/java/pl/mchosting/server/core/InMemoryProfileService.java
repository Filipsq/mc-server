package pl.mchosting.server.core;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;

/**
 * Keeps profiles of online players in memory. Temporary: profiles are lost on restart until the
 * MariaDB storage is added (see docs/architektura.md).
 */
final class InMemoryProfileService implements ProfileService {

    private final Map<UUID, PlayerProfile> online = new ConcurrentHashMap<>();

    void load(UUID playerId) {
        online.putIfAbsent(playerId, PlayerProfile.fresh(playerId));
    }

    void unload(UUID playerId) {
        online.remove(playerId);
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

    private PlayerProfile update(UUID playerId, UnaryOperator<PlayerProfile> change) {
        PlayerProfile updated = online.computeIfPresent(playerId, (id, profile) -> change.apply(profile));
        if (updated == null) {
            throw new IllegalStateException("player " + playerId + " is not online");
        }
        return updated;
    }
}
