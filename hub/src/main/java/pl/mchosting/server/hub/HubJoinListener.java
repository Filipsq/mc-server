package pl.mchosting.server.hub;

import java.util.List;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;

/** Puts every joining player at spawn with a clean hub hotbar and greets new players. */
final class HubJoinListener implements Listener {

    private static final List<String> WELCOME = List.of(
            "<green><bold>Witaj w mchosting.pl!</bold>",
            "<gray>• <green>Kompas</green>: minigry, tryby i dzielnice doliny.",
            "<gray>• <yellow>Głowa</yellow>: Twój profil, poziom i statystyki.",
            "<gray>• <white>Barwnik</white>: ukrywa innych graczy, gdy gra się tnie.",
            "<gray>Codziennie odbierz nagrodę: <yellow>/nagroda</yellow>. Miłej zabawy!");

    private final ProfileService profiles;
    private final HubSettings settings;
    private final HubItems items;
    private final HubSidebar sidebar;
    private final PlayerVisibility visibility;

    HubJoinListener(
            ProfileService profiles,
            HubSettings settings,
            HubItems items,
            HubSidebar sidebar,
            PlayerVisibility visibility) {
        this.profiles = profiles;
        this.settings = settings;
        this.items = items;
        this.sidebar = sidebar;
        this.visibility = visibility;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        // With hundreds of players "X joined" lines would flood the chat.
        event.joinMessage(null);
        Player player = event.getPlayer();
        player.teleport(settings.spawn());
        if (!player.hasPermission(HubPermissions.BUILD)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        items.giveHotbar(player, false);
        visibility.onJoin(player);
        sidebar.show(player);
        profiles.profile(player.getUniqueId())
                .filter(profile -> !profile.tutorialDone())
                .ifPresent(profile -> welcome(player, profile));
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        visibility.onQuit(event.getPlayer().getUniqueId());
        sidebar.remove(event.getPlayer().getUniqueId());
    }

    private void welcome(Player player, PlayerProfile profile) {
        player.showTitle(
                Title.title(HubText.text("<green>Witaj!"), HubText.text("<gray>Kliknij kompas, aby wybrać tryb")));
        WELCOME.forEach(line -> player.sendMessage(HubText.text(line)));
        profiles.completeTutorial(profile.playerId());
    }
}
