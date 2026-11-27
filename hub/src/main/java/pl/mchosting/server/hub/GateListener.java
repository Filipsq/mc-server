package pl.mchosting.server.hub;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** Walking into a minigame gate joins its queue; the player keeps walking around the hub meanwhile. */
final class GateListener implements Listener {

    private final HubSettings settings;
    private final HubMinigames minigames;

    GateListener(HubSettings settings, HubMinigames minigames) {
        this.settings = settings;
        this.minigames = minigames;
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        for (Map.Entry<String, GateRegion> gate : settings.gates().entrySet()) {
            if (inside(gate.getValue(), event.getTo()) && !inside(gate.getValue(), event.getFrom())) {
                enter(player, gate.getKey());
                return;
            }
        }
    }

    private void enter(Player player, String gameId) {
        minigames
                .service()
                .ifPresentOrElse(
                        service -> service.join(player.getUniqueId(), gameId),
                        () -> player.sendActionBar(HubText.text("<yellow>Ta minigra będzie wkrótce!")));
    }

    private static boolean inside(GateRegion gate, Location at) {
        return at.getWorld() != null
                && gate.contains(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }
}
