package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns an arena into simple values for {@code arenas.yml} and back. Numbers are stored as text
 * ({@code "x y z yaw pitch"}) so the file stays short and readable.
 */
final class ArenaCodec {

    private ArenaCodec() {}

    static Map<String, Object> encode(ArenaConfig arena) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("game", arena.type().id());
        values.put("world", arena.world());
        values.put("enabled", arena.enabled());
        if (arena.eliminationY() != null) {
            values.put("elimination-y", arena.eliminationY());
        }
        if (arena.region() != null) {
            values.put(
                    "region",
                    pos(arena.region().min()) + " " + pos(arena.region().max()));
        }
        values.put("spawns", arena.spawns().stream().map(ArenaCodec::spawn).toList());
        return values;
    }

    /**
     * Reads an arena back.
     *
     * @throws IllegalArgumentException if a value is missing or broken
     */
    static ArenaConfig decode(String id, Map<String, ?> values) {
        String gameId = text(values, "game");
        GameType type = GameType.byId(gameId).orElseThrow(() -> new IllegalArgumentException("unknown game " + gameId));
        List<SpawnPoint> spawns = new ArrayList<>();
        if (values.get("spawns") instanceof List<?> list) {
            for (Object entry : list) {
                spawns.add(parseSpawn(String.valueOf(entry)));
            }
        }
        BlockBox region = null;
        if (values.get("region") != null) {
            double[] n = numbers(String.valueOf(values.get("region")), 6);
            region = BlockBox.between(
                    new BlockPos((int) n[0], (int) n[1], (int) n[2]), new BlockPos((int) n[3], (int) n[4], (int) n[5]));
        }
        Integer eliminationY = null;
        if (values.get("elimination-y") instanceof Number y) {
            eliminationY = y.intValue();
        }
        boolean enabled = Boolean.TRUE.equals(values.get("enabled"));
        return new ArenaConfig(id, type, text(values, "world"), spawns, region, eliminationY, enabled);
    }

    private static String text(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return String.valueOf(value);
    }

    private static String pos(BlockPos pos) {
        return pos.x() + " " + pos.y() + " " + pos.z();
    }

    private static String spawn(SpawnPoint spawn) {
        return String.format(
                Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f", spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    private static SpawnPoint parseSpawn(String text) {
        double[] n = numbers(text, 5);
        return new SpawnPoint(n[0], n[1], n[2], (float) n[3], (float) n[4]);
    }

    private static double[] numbers(String text, int count) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != count) {
            throw new IllegalArgumentException("expected " + count + " numbers: " + text);
        }
        double[] numbers = new double[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = Double.parseDouble(parts[i]);
        }
        return numbers;
    }
}
