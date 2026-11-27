package pl.mchosting.server.hub;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Makes hotbar items do their job and stops players from moving them around. */
final class HubItemListener implements Listener {

    private final HubItems items;
    private final ModeMenu modeMenu;
    private final ProfileMenu profileMenu;
    private final PlayerVisibility visibility;
    private final ReturnListener returns;

    HubItemListener(
            HubItems items,
            ModeMenu modeMenu,
            ProfileMenu profileMenu,
            PlayerVisibility visibility,
            ReturnListener returns) {
        this.items = items;
        this.modeMenu = modeMenu;
        this.profileMenu = profileMenu;
        this.visibility = visibility;
        this.returns = returns;
    }

    @EventHandler(priority = EventPriority.LOW)
    void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        items.identify(event.getItem()).ifPresent(item -> {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            Player player = event.getPlayer();
            switch (item) {
                case MODE_SELECTOR -> modeMenu.open(player);
                case PROFILE -> profileMenu.open(player);
                case PLAYER_SERVERS ->
                    player.sendMessage(HubText.text(
                            "<aqua>Menu serwerów graczy będzie wkrótce. Na razie zajrzyj do Alei serwerów (kompas)."));
                case HIDE_PLAYERS -> visibility.toggle(player);
                case RETURN -> ReturnAction.send(player, returns);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !canEditInventory(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!canEditInventory(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private static boolean canEditInventory(Player player) {
        return player.hasPermission(HubPermissions.BUILD) && player.getGameMode() == GameMode.CREATIVE;
    }
}
