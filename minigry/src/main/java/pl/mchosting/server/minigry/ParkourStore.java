package pl.mchosting.server.minigry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Parkour courses and their record boards from {@code parkour.yml}. Read once when the plugin
 * starts, written in the background after every change (a new course, a new record).
 */
final class ParkourStore {

    private final JavaPlugin plugin;
    private final Path file;
    private final Map<String, ParkourCourse> courses = new LinkedHashMap<>();
    private final Map<String, Leaderboard> boards = new HashMap<>();
    /** One writer thread, so saves land on disk in the order they were made. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MchostingMinigry-parkour");
        thread.setDaemon(true);
        return thread;
    });

    ParkourStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataPath().resolve("parkour.yml");
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
        ConfigurationSection section = yaml.getConfigurationSection("courses");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection values = section.getConfigurationSection(id);
                if (values == null) {
                    continue;
                }
                try {
                    courses.put(id, ParkourCodec.decode(id, values.getValues(false)));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping broken parkour course " + id + ": " + e.getMessage());
                }
            }
        }
        ConfigurationSection records = yaml.getConfigurationSection("records");
        if (records != null) {
            for (String id : records.getKeys(false)) {
                List<Leaderboard.Entry> entries = new ArrayList<>();
                for (String line : records.getStringList(id)) {
                    try {
                        entries.add(ParkourCodec.decodeRecord(line));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Skipping broken record on " + id + ": " + line);
                    }
                }
                boards.put(id, new Leaderboard(entries));
            }
        }
    }

    /** Every course, easiest first. */
    Collection<ParkourCourse> all() {
        List<ParkourCourse> sorted = new ArrayList<>(courses.values());
        sorted.sort(Comparator.comparingInt(ParkourCourse::difficulty).thenComparing(ParkourCourse::id));
        return sorted;
    }

    Optional<ParkourCourse> get(String id) {
        return Optional.ofNullable(courses.get(id));
    }

    void put(ParkourCourse course) {
        courses.put(course.id(), course);
        save();
    }

    /** Removes a course; its records go too, since a new course with that id is a new course. */
    void remove(String id) {
        courses.remove(id);
        boards.remove(id);
        save();
    }

    /** Record board of a course, empty if nobody finished it yet. */
    Leaderboard board(String courseId) {
        return boards.computeIfAbsent(courseId, id -> new Leaderboard(List.of()));
    }

    /** Clears the records of a course after it was rebuilt differently. */
    void clearBoard(String courseId) {
        boards.remove(courseId);
        save();
    }

    /** Call after a board changed. */
    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ParkourCourse course : courses.values()) {
            yaml.createSection("courses." + course.id(), ParkourCodec.encode(course));
        }
        boards.forEach((id, board) -> {
            if (!board.entries().isEmpty()) {
                yaml.set(
                        "records." + id,
                        board.entries().stream().map(ParkourCodec::encodeRecord).toList());
            }
        });
        String text = yaml.saveToString();
        writer.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling("parkour.yml.tmp");
                Files.writeString(temporary, text);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save parkour.yml", e);
            }
        });
    }

    /** Waits for the last save when the server stops. */
    void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("parkour.yml was still being saved when the server stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
