package pl.mchosting.server.hub;

import org.bukkit.Material;
import org.bukkit.World;
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
 * falling into the void brings you back to spawn. Only in the hub world: minigame arenas in other
 * worlds of the same server have their own rules.
 */
final class HubProtectionListener implements Listener {

    private final HubSettings settings;

    HubProtectionListener(HubSettings settings) {
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && inHub(player.getWorld())) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(settings.spawn());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHunger(FoodLevelChangeEvent event) {
        if (inHub(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (inHub(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (inHub(event.getBlock().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        if (inHub(event.getPlayer().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** No opening chests, trampling farmland or similar. Buttons and pressure plates still work. */
    @EventHandler
    void onInteractBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !inHub(block.getWorld()) || canBuild(event.getPlayer())) {
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
        if (!(event.getRightClicked() instanceof Player)
                && inHub(event.getRightClicked().getWorld())
                && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (inHub(event.getRightClicked().getWorld()) && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!inHub(event.getEntity().getWorld())) {
            return;
        }
        if (!(event.getRemover() instanceof Player player) || !canBuild(player)) {
            event.setCancelled(true);
        }
    }

    /** No wild mobs in the hub; NPCs and staff-spawned entities are fine. */
    @EventHandler(ignoreCancelled = true)
    void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                && inHub(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && inHub(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !inHub(event.getTo().getWorld())) {
            return;
        }
        boolean inVoid = event.getTo().getY() < settings.voidY();
        boolean inWater =
                settings.waterReturnsToSpawn() && event.getTo().getBlock().isLiquid();
        if (inVoid || inWater) {
            event.getPlayer().teleport(settings.spawn());
        }
    }

    private boolean inHub(World world) {
        return world != null && settings.isHubWorld(world);
    }

    private static boolean canBuild(Player player) {
        return player.hasPermission(HubPermissions.BUILD);
    }
}
