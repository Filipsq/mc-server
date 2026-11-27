package pl.mchosting.server.minigry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Arenas and arena worlds from {@code arenas.yml}. Read once when the plugin starts, written in
 * the background after every change staff make.
 */
final class ArenaStore {

    private final JavaPlugin plugin;
    private final Path file;
    private final Map<String, ArenaConfig> arenas = new LinkedHashMap<>();
    private final Set<String> worlds = new LinkedHashSet<>();
    /** One writer thread, so saves land on disk in the order they were made. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MchostingMinigry-arenas");
        thread.setDaemon(true);
        return thread;
    });

    ArenaStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataPath().resolve("arenas.yml");
    }

    /** Reads the file. Runs once in {@code onEnable}, before any player is online. */
    void load() {
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
        worlds.addAll(yaml.getStringList("worlds"));
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection values = section.getConfigurationSection(id);
            if (values == null) {
                continue;
            }
            try {
                arenas.put(id, ArenaCodec.decode(id, values.getValues(false)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping broken arena " + id + ": " + e.getMessage());
            }
        }
    }

    Collection<ArenaConfig> all() {
        return List.copyOf(arenas.values());
    }

    Optional<ArenaConfig> get(String id) {
        return Optional.ofNullable(arenas.get(id));
    }

    /** Arena worlds created with {@code /minigryadmin swiat}, loaded at every start. */
    Set<String> worlds() {
        return Set.copyOf(worlds);
    }

    void put(ArenaConfig arena) {
        arenas.put(arena.id(), arena);
        save();
    }

    void remove(String id) {
        arenas.remove(id);
        save();
    }

    void addWorld(String name) {
        worlds.add(name);
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds", new ArrayList<>(worlds));
        for (ArenaConfig arena : arenas.values()) {
            yaml.createSection("arenas." + arena.id(), ArenaCodec.encode(arena));
        }
        String text = yaml.saveToString();
        writer.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling("arenas.yml.tmp");
                Files.writeString(temporary, text);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
            }
        });
    }

    /** Waits for the last save when the server stops. */
    void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("arenas.yml was still being saved when the server stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
