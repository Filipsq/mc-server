package pl.mchosting.server.hub;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.mchosting.server.api.DailyReward;
import pl.mchosting.server.api.LevelProgress;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;
import pl.mchosting.server.api.Stats;

/** Profile menu: level, coins, daily reward and minigame stats. */
final class ProfileMenu {

    private static final ZoneId POLAND = DailyReward.NETWORK_ZONE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int BAR_WIDTH = 20;

    private record ModeStats(String mode, String name, Material icon, int slot) {}

    private static final List<ModeStats> MINIGAMES = List.of(
            new ModeStats(Stats.SPLEEF, "Spleef", Material.DIAMOND_SHOVEL, 19),
            new ModeStats(Stats.TNT_RUN, "TNT Run", Material.TNT, 21),
            new ModeStats(Stats.PARKOUR, "Parkour", Material.FEATHER, 23),
            new ModeStats(Stats.DUELS, "Duels", Material.IRON_SWORD, 25));

    private final ProfileService profiles;

    ProfileMenu(ProfileService profiles) {
        this.profiles = profiles;
    }

    void open(Player player) {
        profiles.profile(player.getUniqueId())
                .ifPresent(profile -> build(player, profile).open(player));
    }

    private Menu build(Player player, PlayerProfile profile) {
        Menu menu = new Menu(36, HubText.text("<dark_green>Twój profil"));
        LevelProgress progress = profile.levelProgress();
        LocalDate today = LocalDate.now(POLAND);

        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player);
            meta.customName(HubText.item("<yellow><name>", Placeholder.unparsed("name", player.getName())));
            meta.lore(List.of(HubText.item(
                    "<gray>Gra od: <white><date>",
                    Placeholder.unparsed("date", DATE.format(profile.firstJoin().atZone(POLAND))))));
        });
        menu.set(4, head);

        String xpLine = progress.isMaxLevel()
                ? "<gold>Najwyższy poziom!"
                : "<gray>Do następnego: <white>" + progress.xpToNext() + " XP";
        menu.set(
                11,
                item(
                        Material.EXPERIENCE_BOTTLE,
                        "<green>Poziom " + progress.level(),
                        List.of(
                                ProgressBar.render(progress.fraction(), BAR_WIDTH),
                                xpLine,
                                "",
                                "<gray>XP zdobywasz za granie w każdym trybie.",
                                "<gray>Poziom odblokuje kosmetyki i tytuły.")));

        menu.set(
                13,
                item(
                        Material.GOLD_NUGGET,
                        "<gold>Monety: " + profile.coins(),
                        List.of(
                                "<gray>Za minigry, questy i nagrodę dnia.",
                                "<gray>Wydasz je na kosmetyki w hubie.",
                                "<dark_gray>Monet nie da się kupić.")));

        boolean claimable = DailyReward.canClaim(profile.daily(), today);
        int streak = profile.daily().currentStreak(today);
        int nextDay = claimable ? streak + 1 : streak;
        menu.set(
                15,
                item(
                        claimable ? Material.CHEST_MINECART : Material.MINECART,
                        claimable ? "<green>Nagroda dnia: do odebrania!" : "<gray>Nagroda dnia: odebrana",
                        List.of(
                                "<gray>Seria: <white>" + streak + " " + SidebarContent.days(streak),
                                "<gray>Dzień " + nextDay + ": <gold>+"
                                        + DailyReward.rewardForDay(Math.max(1, nextDay))
                                                .coins() + " monet",
                                "<gray>Co " + DailyReward.CYCLE_DAYS + ". dzień duża nagroda.",
                                "",
                                claimable ? "<yellow>Kliknij, aby odebrać" : "<gray>Wróć jutro albo wpisz /nagroda")),
                clicked -> {
                    DailyRewardAction.claim(clicked, profiles);
                    open(clicked);
                });

        for (ModeStats mode : MINIGAMES) {
            menu.set(
                    mode.slot(),
                    item(
                            mode.icon(),
                            "<gold>" + mode.name(),
                            List.of(
                                    "<gray>Wygrane: <white>" + profile.stat(Stats.wins(mode.mode())),
                                    "<gray>Rozegrane: <white>" + profile.stat(Stats.gamesPlayed(mode.mode())))));
        }
        return menu;
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.customName(HubText.item(name));
            meta.lore(lore.stream()
                    .map(line -> line.isEmpty() ? Component.empty() : HubText.item(line))
                    .toList());
        });
        return stack;
    }
}
