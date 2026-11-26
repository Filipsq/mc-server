package pl.mchosting.server.hub;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * "Hide players" toggle. With 1000 players in the hub this keeps weaker computers and phones
 * smooth. Only for this visit; everyone starts visible.
 */
final class PlayerVisibility {

    private static final Duration TOGGLE_COOLDOWN = Duration.ofSeconds(3);

    private final Plugin plugin;
    private final HubItems items;
    private final Set<UUID> hiding = ConcurrentHashMap.newKeySet();
    private final Cooldown cooldown = new Cooldown(TOGGLE_COOLDOWN, Clock.systemUTC());

    PlayerVisibility(Plugin plugin, HubItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    boolean isHiding(Player player) {
        return hiding.contains(player.getUniqueId());
    }

    void toggle(Player player) {
        UUID id = player.getUniqueId();
        if (!cooldown.tryUse(id)) {
            long seconds = Math.max(1, cooldown.remaining(id).toSeconds());
            player.sendActionBar(HubText.text("<red>Poczekaj " + seconds + " s"));
            return;
        }
        boolean hide = !hiding.remove(id);
        if (hide) {
            hiding.add(id);
        }
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (hide) {
                player.hidePlayer(plugin, other);
            } else {
                player.showPlayer(plugin, other);
            }
        }
        items.refreshHideItem(player, hide);
        player.sendActionBar(HubText.text(hide ? "<gray>Gracze ukryci" : "<green>Gracze widoczni"));
    }

    /** Hides a new player from everyone who is hiding players. */
    void onJoin(Player joined) {
        for (UUID hiderId : hiding) {
            Player hider = plugin.getServer().getPlayer(hiderId);
            if (hider != null && !hider.equals(joined)) {
                hider.hidePlayer(plugin, joined);
            }
        }
    }

    void onQuit(UUID playerId) {
        hiding.remove(playerId);
        cooldown.forget(playerId);
    }
}
