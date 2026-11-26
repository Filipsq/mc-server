package pl.mchosting.server.hub;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Nobody gets hurt, hungry or loses items in the hub, only staff can change the valley, and
 * falling into the void brings you back to spawn.
 */
final class HubProtectionListener implements Listener {

    private final HubSettings settings;

    HubProtectionListener(HubSettings settings) {
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(settings.spawn());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        if (!canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** No opening chests, trampling farmland or similar. Buttons and pressure plates still work. */
    @EventHandler
    void onInteractBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || canBuild(event.getPlayer())) {
            return;
        }
        boolean trample = event.getAction() == Action.PHYSICAL && block.getType() == Material.FARMLAND;
        boolean container =
                event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getState(false) instanceof InventoryHolder;
        if (trample || container) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** Item frames, armor stands and other decorations stay as they are. */
    @EventHandler(ignoreCancelled = true)
    void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player) || !canBuild(player)) {
            event.setCancelled(true);
        }
    }

    /** No wild mobs in the hub; NPCs and staff-spawned entities are fine. */
    @EventHandler(ignoreCancelled = true)
    void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        boolean inVoid = event.getTo().getY() < settings.voidY();
        boolean inWater =
                settings.waterReturnsToSpawn() && event.getTo().getBlock().isLiquid();
        if (inVoid || inWater) {
            event.getPlayer().teleport(settings.spawn());
        }
    }

    private static boolean canBuild(Player player) {
        return player.hasPermission(HubPermissions.BUILD);
    }
}
