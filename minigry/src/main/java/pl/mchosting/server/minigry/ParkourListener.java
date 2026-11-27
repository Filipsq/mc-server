package pl.mchosting.server.minigry;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Connects parkour runs to the server: moves, falls, the run items, and nobody gets hurt. */
final class ParkourListener implements Listener {

    private final ParkourManager parkour;

    ParkourListener(ParkourManager parkour) {
        this.parkour = parkour;
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (parkour.isRunning(event.getPlayer().getUniqueId())) {
            parkour.moved(event.getPlayer(), event.getTo());
        }
    }

    /** No damage on courses; falling into the void means back to the checkpoint. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !parkour.isRunning(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            parkour.runOf(player.getUniqueId()).ifPresent(run -> parkour.backToCheckpoint(player, run));
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && parkour.isRunning(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!parkour.isRunning(player.getUniqueId())
                || event.getHand() != EquipmentSlot.HAND
                || event.getAction() == Action.PHYSICAL) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            parkour.useItem(player, player.getInventory().getHeldItemSlot());
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        if (parkour.isRunning(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onSwap(PlayerSwapHandItemsEvent event) {
        if (parkour.isRunning(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && parkour.isRunning(player.getUniqueId())
                && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    /** Nobody flies on a course, even if another plugin allowed it. */
    @EventHandler(ignoreCancelled = true)
    void onFly(PlayerToggleFlightEvent event) {
        if (event.isFlying() && parkour.isRunning(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        parkour.runOf(player.getUniqueId())
                .filter(run -> !run.course().world().equals(player.getWorld().getName()))
                .ifPresent(run -> parkour.stop(player));
    }
}
