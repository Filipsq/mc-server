package pl.mchosting.server.hub;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Nobody gets hurt, hungry or loses items in the hub, and only staff can change blocks. */
final class HubProtectionListener implements Listener {

    /** Falling below this height sends the player back to spawn. */
    private static final int VOID_Y = 60;

    @EventHandler(ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (!event.getPlayer().hasPermission(HubPermissions.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().hasPermission(HubPermissions.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        if (!event.getPlayer().hasPermission(HubPermissions.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < VOID_Y) {
            Player player = event.getPlayer();
            player.teleport(player.getWorld().getSpawnLocation());
        }
    }
}
