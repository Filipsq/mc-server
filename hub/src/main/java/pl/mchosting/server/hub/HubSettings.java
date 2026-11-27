package pl.mchosting.server.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Hub options from config.yml and the points staff set in the valley. */
final class HubSettings {

    private static final String POINTS = "points.";
    private static final String GATES = "gates";

    private final JavaPlugin plugin;
    /** Gates by game id, read once because they are checked on every step. */
    private final Map<String, GateRegion> gates = new HashMap<>();

    HubSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection section = config().getConfigurationSection(GATES);
        if (section != null) {
            for (String gameId : section.getKeys(false)) {
                try {
                    gates.put(gameId, GateRegion.decode(section.getString(gameId, "")));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping broken gate " + gameId + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * True in the hub world (the world of the spawn). Hub rules only apply there, so minigame
     * arenas in other worlds on the same server work normally.
     */
    boolean isHubWorld(World world) {
        return world.equals(spawn().getWorld());
    }

    /** Minigame gates by game id. */
    Map<String, GateRegion> gates() {
        return Map.copyOf(gates);
    }

    void setGate(String gameId, GateRegion gate) {
        gates.put(gameId, gate);
        config().set(GATES + "." + gameId, gate.encode());
        save();
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
        save();
    }

    private void save() {
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

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}
