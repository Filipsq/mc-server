package pl.mchosting.server.hub;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Items every player gets in the hub hotbar. */
final class HubItems {

    static final int SELECTOR_SLOT = 0;

    private HubItems() {}

    static ItemStack modeSelector() {
        ItemStack item = ItemStack.of(Material.COMPASS);
        item.editMeta(meta -> meta.customName(
                Component.text("Wybierz tryb", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        return item;
    }
}
