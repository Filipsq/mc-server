package pl.mchosting.server.hub;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.LobbyService;

/** Puts a player at the hub spawn in a clean state with the hub hotbar: on join and after a match. */
final class HubLobby implements LobbyService {

    private final Server server;
    private final HubSettings settings;
    private final HubItems items;
    private final PlayerVisibility visibility;

    HubLobby(Server server, HubSettings settings, HubItems items, PlayerVisibility visibility) {
        this.server = server;
        this.settings = settings;
        this.items = items;
        this.visibility = visibility;
    }

    @Override
    public void sendToLobby(UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            arrive(player);
        }
    }

    void arrive(Player player) {
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
        player.setAllowFlight(false);
        player.clearActivePotionEffects();
        items.giveHotbar(player, visibility.isHiding(player));
    }
}
