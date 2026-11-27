package pl.mchosting.server.minigry;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.mchosting.server.api.LobbyService;

/** Sends players back to the hub after a match or a parkour run, and cleans them up for a game. */
final class Lobby {

    private final Plugin plugin;

    Lobby(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Back to the hub with full health and the hub's own items. */
    void send(Player player) {
        prepare(player, GameMode.ADVENTURE);
        player.sendActionBar(Component.empty());
        LobbyService lobby = plugin.getServer().getServicesManager().load(LobbyService.class);
        if (lobby != null) {
            lobby.sendToLobby(player.getUniqueId());
        } else {
            Location spawn = plugin.getServer().getWorlds().getFirst().getSpawnLocation();
            player.teleport(spawn);
        }
    }

    /** Empty inventory, no effects, full health and food, not flying. */
    static void prepare(Player player, GameMode mode) {
        player.getInventory().clear();
        player.setGameMode(mode);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.clearActivePotionEffects();
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
    }
}
