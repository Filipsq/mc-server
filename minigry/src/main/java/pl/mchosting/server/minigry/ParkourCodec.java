package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns courses and record boards into simple values for {@code parkour.yml} and back. A point is
 * stored as {@code "x y z yaw pitch fallY"}, a record as {@code "uuid millis setAt name"}.
 */
final class ParkourCodec {

    private ParkourCodec() {}

    static Map<String, Object> encode(ParkourCourse course) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", course.name());
        values.put("difficulty", course.difficulty());
        values.put("world", course.world());
        values.put("enabled", course.enabled());
        if (course.start() != null) {
            values.put("start", point(course.start()));
        }
        values.put(
                "checkpoints",
                course.checkpoints().stream().map(ParkourCodec::point).toList());
        if (course.finish() != null) {
            BlockPos finish = course.finish();
            values.put("finish", finish.x() + " " + finish.y() + " " + finish.z());
        }
        return values;
    }

    /**
     * Reads a course back.
     *
     * @throws IllegalArgumentException if a value is missing or broken
     */
    static ParkourCourse decode(String id, Map<String, ?> values) {
        Object difficulty = values.get("difficulty");
        if (!(difficulty instanceof Number level)) {
            throw new IllegalArgumentException("missing difficulty");
        }
        Checkpoint start = values.get("start") == null ? null : parsePoint(String.valueOf(values.get("start")));
        List<Checkpoint> checkpoints = new ArrayList<>();
        if (values.get("checkpoints") instanceof List<?> list) {
            for (Object entry : list) {
                checkpoints.add(parsePoint(String.valueOf(entry)));
            }
        }
        BlockPos finish = null;
        if (values.get("finish") != null) {
            double[] n = numbers(String.valueOf(values.get("finish")), 3);
            finish = new BlockPos((int) n[0], (int) n[1], (int) n[2]);
        }
        return new ParkourCourse(
                id,
                text(values, "name"),
                level.intValue(),
                text(values, "world"),
                start,
                checkpoints,
                finish,
                Boolean.TRUE.equals(values.get("enabled")));
    }

    static String encodeRecord(Leaderboard.Entry entry) {
        return entry.playerId() + " " + entry.millis() + " " + entry.setAt() + " " + entry.name();
    }

    /**
     * @throws IllegalArgumentException if the text is broken
     */
    static Leaderboard.Entry decodeRecord(String text) {
        String[] parts = text.trim().split(" ", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("bad record: " + text);
        }
        return new Leaderboard.Entry(
                UUID.fromString(parts[0]), parts[3], Long.parseLong(parts[1]), Long.parseLong(parts[2]));
    }

    private static String point(Checkpoint point) {
        SpawnPoint spawn = point.spawn();
        return String.format(
                Locale.ROOT,
                "%.2f %.2f %.2f %.1f %.1f %d",
                spawn.x(),
                spawn.y(),
                spawn.z(),
                spawn.yaw(),
                spawn.pitch(),
                point.fallY());
    }

    private static Checkpoint parsePoint(String text) {
        double[] n = numbers(text, 6);
        return new Checkpoint(new SpawnPoint(n[0], n[1], n[2], (float) n[3], (float) n[4]), (int) n[5]);
    }

    private static String text(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return String.valueOf(value);
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
