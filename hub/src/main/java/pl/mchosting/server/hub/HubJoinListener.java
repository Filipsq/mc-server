package pl.mchosting.server.hub;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Puts every joining player at spawn with a clean hub hotbar. */
final class HubJoinListener implements Listener {

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(player.getWorld().getSpawnLocation());
        if (!player.hasPermission(HubPermissions.BUILD)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.getInventory().clear();
        player.getInventory().setItem(HubItems.SELECTOR_SLOT, HubItems.modeSelector());
        player.setFoodLevel(20);
    }
}
