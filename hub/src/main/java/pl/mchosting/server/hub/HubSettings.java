package pl.mchosting.server.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Hub options from config.yml and the points staff set in the valley. */
final class HubSettings {

    private static final String POINTS = "points.";

    private final JavaPlugin plugin;

    HubSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Spawn point, or the world spawn until staff set one. */
    Location spawn() {
        return point(HubPoint.SPAWN)
                .orElseGet(() -> plugin.getServer().getWorlds().getFirst().getSpawnLocation());
    }

    Optional<Location> point(HubPoint point) {
        Location location = config().getLocation(POINTS + point.id());
        return Optional.ofNullable(location).map(Location::clone);
    }

    /** Where a player is sent: the point if set, spawn for {@link HubPoint#SPAWN}. */
    Optional<Location> destination(HubPoint point) {
        return point == HubPoint.SPAWN ? Optional.of(spawn()) : point(point);
    }

    /** Falling below this height sends the player back to spawn. */
    int voidY() {
        return config().getInt("void-y", 60);
    }

    boolean waterReturnsToSpawn() {
        return config().getBoolean("water-returns-to-spawn", false);
    }

    /** Stores a point and writes config.yml in the background. */
    void setPoint(HubPoint point, Location location) {
        config().set(POINTS + point.id(), location.clone());
        String yaml = config().saveToString();
        Path file = plugin.getDataPath().resolve("config.yml");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, yaml);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", e);
            }
        });
    }

    /** The panel that knows linked customers' servers (mchosting-link). */
    String panelUrl() {
        return config().getString("link.panel-url", "https://mchosting.pl");
    }

    /** Domains /wroc may send players to. */
    List<String> returnDomains() {
        List<String> domains = config().getStringList("link.allowed-domains");
        return domains.isEmpty() ? List.of("mchosting.pl") : domains;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}
