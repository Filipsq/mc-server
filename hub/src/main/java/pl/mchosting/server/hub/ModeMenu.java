package pl.mchosting.server.hub;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** The compass menu: game modes and the districts of the valley. */
final class ModeMenu {

    private final HubSettings settings;
    private final HubMinigames minigames;

    ModeMenu(HubSettings settings, HubMinigames minigames) {
        this.settings = settings;
        this.minigames = minigames;
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
        String openGame = openGame(destination);
        stack.editMeta(meta -> {
            meta.customName(HubText.item((destination.isMode() ? "<gold>" : "<aqua>") + destination.displayName()));
            List<Component> lore = new ArrayList<>(HubText.lore(destination.description()));
            lore.add(Component.empty());
            if (openGame != null) {
                minigames
                        .service()
                        .ifPresent(service -> lore.add(HubText.item(
                                "<gray>W kolejce: <white><queued></white> • Gra: <white><playing>",
                                Placeholder.unparsed("queued", String.valueOf(service.queued(openGame))),
                                Placeholder.unparsed("playing", String.valueOf(service.playing(openGame))))));
                lore.add(HubText.item("<green>Kliknij, aby dołączyć do kolejki"));
            } else {
                if (destination.isMode()) {
                    lore.add(HubText.item("<yellow>Wkrótce! Na razie zabierzemy Cię do bramy."));
                }
                lore.add(HubText.item(
                        pointSet ? "<green>Kliknij, aby się przenieść" : "<red>Ten punkt nie jest jeszcze ustawiony"));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return stack;
    }

    /** The minigame id if this entry is a minigame that can be played right now, else null. */
    private String openGame(HubDestination destination) {
        return destination.gameId().filter(minigames::isOpen).orElse(null);
    }

    private void travel(Player player, HubDestination destination) {
        player.closeInventory();
        String openGame = openGame(destination);
        if (openGame != null) {
            minigames.service().ifPresent(service -> service.join(player.getUniqueId(), openGame));
            return;
        }
        settings.destination(destination.point())
                .ifPresentOrElse(
                        player::teleport,
                        () -> player.sendMessage(HubText.text("<red>Ten punkt nie jest jeszcze ustawiony.")));
    }
}
