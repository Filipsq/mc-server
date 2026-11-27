package pl.mchosting.server.hub;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Builds the hotbar items and recognises them when clicked (by a hidden tag, not by name). */
final class HubItems {

    private final NamespacedKey key;

    HubItems(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "hub_item");
    }

    void giveHotbar(Player player, boolean hidingPlayers) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (HubItem item : HubItem.values()) {
            if (item != HubItem.RETURN) {
                inventory.setItem(item.slot(), create(item, player, hidingPlayers));
            }
        }
        inventory.setHeldItemSlot(HubItem.MODE_SELECTOR.slot());
    }

    void refreshHideItem(Player player, boolean hidingPlayers) {
        player.getInventory().setItem(HubItem.HIDE_PLAYERS.slot(), create(HubItem.HIDE_PLAYERS, player, hidingPlayers));
    }

    /** The door that sends the player back to the linked server they came from. */
    void giveReturnItem(Player player, ReturnServer server) {
        ItemStack stack = ItemStack.of(Material.OAK_DOOR);
        stack.editMeta(meta -> {
            meta.customName(
                    HubText.item("<gold>Wróć na <white><server>", Placeholder.unparsed("server", server.name())));
            meta.lore(HubText.lore(List.of("Kliknij albo wpisz /wroc.")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, HubItem.RETURN.name());
        });
        player.getInventory().setItem(HubItem.RETURN.slot(), stack);
    }

    Optional<HubItem> identify(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        for (HubItem item : HubItem.values()) {
            if (item.name().equals(id)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private ItemStack create(HubItem item, Player player, boolean hidingPlayers) {
        ItemStack stack = switch (item) {
            case MODE_SELECTOR ->
                named(Material.COMPASS, "<green>Wybierz tryb", "Minigry, BoxPvP, LifeSteal i dzielnice doliny.");
            case PROFILE -> profileHead(player);
            case PLAYER_SERVERS ->
                named(Material.ENDER_EYE, "<aqua>Serwery graczy", "Serwery z mchosting.pl. Wkrótce!");
            case RETURN -> throw new IllegalArgumentException("use giveReturnItem");
            case HIDE_PLAYERS ->
                hidingPlayers
                        ? named(Material.GRAY_DYE, "<gray>Gracze: ukryci", "Kliknij, aby pokazać graczy.")
                        : named(Material.LIME_DYE, "<green>Gracze: widoczni", "Kliknij, aby ukryć graczy.");
        };
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, item.name()));
        return stack;
    }

    private static ItemStack profileHead(Player player) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player);
            describe(meta, "<yellow>Twój profil", "Poziom, monety, statystyki i nagroda dnia.");
        });
        return head;
    }

    private static ItemStack named(Material material, String name, String hint) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> describe(meta, name, hint));
        return stack;
    }

    private static void describe(ItemMeta meta, String name, String hint) {
        meta.customName(HubText.item(name));
        meta.lore(HubText.lore(List.of(hint)));
    }
}
