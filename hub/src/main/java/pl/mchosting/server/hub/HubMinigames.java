package pl.mchosting.server.hub;

import java.util.Optional;
import org.bukkit.Server;
import pl.mchosting.server.api.MinigameService;

/** The minigame queues, if the minigames plugin is running on this server. */
final class HubMinigames {

    private final Server server;

    HubMinigames(Server server) {
        this.server = server;
    }

    Optional<MinigameService> service() {
        return Optional.ofNullable(server.getServicesManager().load(MinigameService.class));
    }

    /** True if players can queue for {@code gameId} right now. */
    boolean isOpen(String gameId) {
        return service()
                .map(service -> service.availableGames().contains(gameId))
                .orElse(false);
    }
}
