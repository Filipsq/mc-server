package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;

/** Kit choice for Duels: the top row joins normal duels, the bottom row ranked ones. */
final class DuelMenu {

    private static final int NORMAL_ROW_START = 2;
    private static final int RANKED_ROW_START = 20;

    private DuelMenu() {}

    static void open(MinigameEngine engine, ProfileService profiles, Player player) {
        ChoiceMenu menu = new ChoiceMenu(3, MiniText.text("<dark_red>Duels: wybierz zestaw"));
        Optional<PlayerProfile> profile = profiles.profile(player.getUniqueId());
        DuelKit[] kits = DuelKit.values();
        for (int i = 0; i < kits.length; i++) {
            DuelKit kit = kits[i];
            QueueId normal = QueueId.duel(kit, false);
            QueueId ranked = QueueId.duel(kit, true);
            menu.set(NORMAL_ROW_START + i, icon(engine, normal, profile), clicked -> engine.joinQueue(clicked, normal));
            menu.set(RANKED_ROW_START + i, icon(engine, ranked, profile), clicked -> engine.joinQueue(clicked, ranked));
        }
        menu.open(player);
    }

    private static ItemStack icon(MinigameEngine engine, QueueId queue, Optional<PlayerProfile> profile) {
        DuelKit kit = queue.kit();
        ItemStack stack = ItemStack.of(DuelKits.icon(kit));
        stack.editMeta(meta -> {
            meta.customName(MiniText.item(
                    (queue.ranked() ? "<gold>" : "<aqua>") + "<kit>" + (queue.ranked() ? " <yellow>(rankingowy)" : ""),
                    Placeholder.unparsed("kit", kit.displayName())));
            List<Component> lore = new ArrayList<>();
            for (String line : kit.description()) {
                lore.add(MiniText.item("<gray><line>", Placeholder.unparsed("line", line)));
            }
            lore.add(Component.empty());
            if (queue.ranked()) {
                int points = profile.map(p -> DuelStats.rating(p, kit)).orElse(EloRating.START);
                long games =
                        profile.map(p -> p.stat(DuelStats.rankedGames(kit))).orElse(0L);
                long wins = profile.map(p -> p.stat(DuelStats.rankedWins(kit))).orElse(0L);
                DuelRank rank = DuelRank.of(points);
                lore.add(MiniText.item(
                        "<gray>Twoje punkty: <white><points></white> • " + rank.color() + "<rank>",
                        Placeholder.unparsed("points", String.valueOf(points)),
                        Placeholder.unparsed("rank", rank.displayName())));
                lore.add(MiniText.item(
                        "<gray>Wygrane: <white><wins></white> z <white><games>",
                        Placeholder.unparsed("wins", String.valueOf(wins)),
                        Placeholder.unparsed("games", String.valueOf(games))));
                lore.add(MiniText.item("<gray>Wygrana daje punkty, przegrana je zabiera."));
            } else {
                lore.add(MiniText.item("<gray>Bez punktów rankingu, na rozgrzewkę."));
            }
            lore.add(MiniText.item(
                    "<gray>W kolejce: <white><queued>",
                    Placeholder.unparsed("queued", String.valueOf(engine.queued(queue)))));
            lore.add(MiniText.item("<green>Kliknij, aby dołączyć do kolejki"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (queue.ranked()) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }
}
