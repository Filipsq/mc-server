package pl.mchosting.server.hub;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** The compass menu: game modes and the districts of the valley. */
final class ModeMenu {

    private final HubSettings settings;

    ModeMenu(HubSettings settings) {
        this.settings = settings;
    }

    void open(Player player) {
        Menu menu = new Menu(HubDestination.MENU_SIZE, HubText.text("<dark_green>Wybierz tryb"));
        for (HubDestination destination : HubDestination.values()) {
            menu.set(destination.slot(), icon(destination), clicked -> travel(clicked, destination));
        }
        menu.open(player);
    }

    private ItemStack icon(HubDestination destination) {
        Material material = Material.matchMaterial(destination.icon());
        ItemStack stack = ItemStack.of(material == null ? Material.PAPER : material);
        boolean pointSet = settings.destination(destination.point()).isPresent();
        stack.editMeta(meta -> {
            meta.customName(HubText.item((destination.isMode() ? "<gold>" : "<aqua>") + destination.displayName()));
            List<Component> lore = new ArrayList<>(HubText.lore(destination.description()));
            lore.add(Component.empty());
            if (destination.isMode()) {
                lore.add(HubText.item("<yellow>Wkrótce! Na razie zabierzemy Cię do bramy."));
            }
            lore.add(HubText.item(
                    pointSet ? "<green>Kliknij, aby się przenieść" : "<red>Ten punkt nie jest jeszcze ustawiony"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return stack;
    }

    private void travel(Player player, HubDestination destination) {
        player.closeInventory();
        settings.destination(destination.point())
                .ifPresentOrElse(
                        player::teleport,
                        () -> player.sendMessage(HubText.text("<red>Ten punkt nie jest jeszcze ustawiony.")));
    }
}
