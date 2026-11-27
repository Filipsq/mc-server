package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * One parkour course. Players start on {@code start}, must touch every checkpoint in order and
 * finish on {@code finish}. Plain data, so the rules are tested without a server.
 *
 * <p>Points are platforms: standing on the point's block or any block next to it (a 3 by 3
 * platform) counts.
 *
 * @param id short name, also part of the record stat, for example {@code trasa3}
 * @param name name shown to players
 * @param difficulty 1 (easiest) to 10 (hardest)
 * @param world the world the course is in
 * @param start where runs begin; leaving its platform starts the clock
 * @param checkpoints points between start and finish, in order
 * @param finish the block that ends the run, null until staff set it
 * @param enabled whether staff switched the course on
 */
record ParkourCourse(
        String id,
        String name,
        int difficulty,
        String world,
        Checkpoint start,
        List<Checkpoint> checkpoints,
        BlockPos finish,
        boolean enabled) {

    /** Shorter than {@link ArenaConfig#ID} because the id is part of a stat name. */
    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");

    static final int MIN_DIFFICULTY = 1;
    static final int MAX_DIFFICULTY = 10;

    ParkourCourse {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(world, "world");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("course id must match " + ID + ": " + id);
        }
        if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be 1-10: " + difficulty);
        }
        checkpoints = List.copyOf(checkpoints);
    }

    static ParkourCourse create(String id, String name, int difficulty, String world) {
        return new ParkourCourse(id, name, difficulty, world, null, List.of(), null, false);
    }

    /** What is still missing before players can run the course; empty when it is ready. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (start == null) {
            problems.add("brak startu (/parkouradmin start " + id + ")");
        }
        if (finish == null) {
            problems.add("brak mety (/parkouradmin meta " + id + ")");
        }
        return problems;
    }

    boolean isReady() {
        return enabled && problems().isEmpty();
    }

    Difficulty level() {
        return Difficulty.of(difficulty);
    }

    /** Where the player stands after reaching {@code reached} checkpoints (0 = the start). */
    Checkpoint respawnAfter(int reached) {
        if (reached < 0 || reached > checkpoints.size()) {
            throw new IllegalArgumentException("bad checkpoint " + reached);
        }
        return reached == 0 ? start : checkpoints.get(reached - 1);
    }

    /** Index (1 = first) of the checkpoint whose platform includes {@code standing}, if any. */
    OptionalInt checkpointAt(BlockPos standing) {
        for (int i = 0; i < checkpoints.size(); i++) {
            if (onPlatform(standing, checkpoints.get(i).block())) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    boolean isFinish(BlockPos standing) {
        return finish != null && onPlatform(standing, finish);
    }

    boolean isStart(BlockPos standing) {
        return start != null && onPlatform(standing, start.block());
    }

    /** Whether a player whose feet are in {@code feet} is still above the start platform. */
    boolean aboveStart(BlockPos feet) {
        BlockPos pad = start.block();
        return Math.abs(feet.x() - pad.x()) <= 1 && Math.abs(feet.z() - pad.z()) <= 1;
    }

    ParkourCourse withStart(Checkpoint point) {
        return new ParkourCourse(id, name, difficulty, world, point, checkpoints, finish, enabled);
    }

    ParkourCourse withCheckpointAdded(Checkpoint point) {
        List<Checkpoint> updated = new ArrayList<>(checkpoints);
        updated.add(point);
        return new ParkourCourse(id, name, difficulty, world, start, updated, finish, enabled);
    }

    ParkourCourse withoutCheckpoints() {
        return new ParkourCourse(id, name, difficulty, world, start, List.of(), finish, enabled);
    }

    ParkourCourse withFinish(BlockPos block) {
        return new ParkourCourse(id, name, difficulty, world, start, checkpoints, block, enabled);
    }

    ParkourCourse withEnabled(boolean on) {
        return new ParkourCourse(id, name, difficulty, world, start, checkpoints, finish, on);
    }

    /**
     * The block under a player standing at this position. Only counts when the feet touch the top
     * of a full block, so jumping over a checkpoint does not reach it.
     */
    static BlockPos standingBlock(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y - 0.01), (int) Math.floor(z));
    }

    private static boolean onPlatform(BlockPos standing, BlockPos center) {
        return standing.y() == center.y()
                && Math.abs(standing.x() - center.x()) <= 1
                && Math.abs(standing.z() - center.z()) <= 1;
    }

    /** Difficulty groups shown to players. */
    enum Difficulty {
        LATWA("Łatwa", "<green>"),
        SREDNIA("Średnia", "<yellow>"),
        TRUDNA("Trudna", "<gold>"),
        BARDZO_TRUDNA("Bardzo trudna", "<red>");

        private final String displayName;
        private final String color;

        Difficulty(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        String displayName() {
            return displayName;
        }

        String color() {
            return color;
        }

        static Difficulty of(int difficulty) {
            if (difficulty <= 3) {
                return LATWA;
            }
            if (difficulty <= 5) {
                return SREDNIA;
            }
            if (difficulty <= 8) {
                return TRUDNA;
            }
            return BARDZO_TRUDNA;
        }
    }
}
