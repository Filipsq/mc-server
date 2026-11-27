package pl.mchosting.server.minigry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** A chest menu (parkour courses, Duels kits) where clicking an item runs an action. */
final class ChoiceMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    ChoiceMenu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    void set(int slot, ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Keeps items in the menus and runs the clicked action. */
    static final class Clicks implements Listener {

        @EventHandler(priority = EventPriority.HIGH)
        void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder(false) instanceof ChoiceMenu menu)) {
                return;
            }
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getClickedInventory() == event.getView().getTopInventory()) {
                Consumer<Player> action = menu.actions.get(event.getSlot());
                if (action != null) {
                    player.closeInventory();
                    action.accept(player);
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGH)
        void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder(false) instanceof ChoiceMenu) {
                event.setCancelled(true);
            }
        }
    }
}
