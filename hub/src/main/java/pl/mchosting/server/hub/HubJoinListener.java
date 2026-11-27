package pl.mchosting.server.hub;

import java.util.List;
import net.kyori.adventure.title.Title;
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
    private final HubLobby lobby;
    private final HubSidebar sidebar;
    private final PlayerVisibility visibility;

    HubJoinListener(ProfileService profiles, HubLobby lobby, HubSidebar sidebar, PlayerVisibility visibility) {
        this.profiles = profiles;
        this.lobby = lobby;
        this.sidebar = sidebar;
        this.visibility = visibility;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        // With hundreds of players "X joined" lines would flood the chat.
        event.joinMessage(null);
        Player player = event.getPlayer();
        lobby.arrive(player);
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
