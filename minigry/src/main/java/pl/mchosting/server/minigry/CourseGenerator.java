package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a parkour course from a difficulty, so the server has ten ready courses without anyone
 * building them by hand. The course runs along +X from {@code origin}: a start platform, single
 * blocks to jump on, a 3 by 3 checkpoint platform every few jumps and a finish platform.
 *
 * <p>Every jump comes from {@link #jumpsFor}, a table of jumps known to be possible in vanilla
 * Minecraft (the hardest ones need sprinting). The same difficulty and seed always give the same
 * course.
 */
final class CourseGenerator {

    /** What a block of the course is for; the plugin picks a material for each. */
    enum Kind {
        START,
        JUMP,
        CHECKPOINT,
        FINISH
    }

    record Piece(BlockPos pos, Kind kind) {}

    /**
     * One jump from the block the player stands on to the next one.
     *
     * @param gap air blocks between the two blocks along the course (1 to 4)
     * @param up height difference (-1 down, 0 flat, +1 up)
     * @param side how many blocks the next block is moved sideways (0 or 1)
     */
    record Jump(int gap, int up, int side) {

        Jump {
            if (gap < 1 || gap > MAX_GAP || up < -1 || up > 1 || side < 0 || side > 1) {
                throw new IllegalArgumentException("impossible jump " + gap + "/" + up + "/" + side);
            }
            if (gap > maxGap(up, side)) {
                throw new IllegalArgumentException("jump too long: " + gap + "/" + up + "/" + side);
            }
        }

        /**
         * Longest gap a sprinting player can clear: 4 flat or down, 3 up a block; one less when
         * the next block is also moved sideways.
         */
        static int maxGap(int up, int side) {
            int max = up > 0 ? 3 : MAX_GAP;
            return max - side;
        }
    }

    /**
     * The generated course, already placed at the origin.
     *
     * @param pieces every block to place
     * @param start where runs begin
     * @param checkpoints the checkpoints in order
     * @param finish the middle of the finish platform
     */
    record Blueprint(List<Piece> pieces, Checkpoint start, List<Checkpoint> checkpoints, BlockPos finish) {

        Blueprint {
            pieces = List.copyOf(pieces);
            checkpoints = List.copyOf(checkpoints);
        }

        ParkourCourse toCourse(String id, String name, int difficulty, String world) {
            return new ParkourCourse(id, name, difficulty, world, start, checkpoints, finish, true);
        }
    }

    static final int MAX_GAP = 4;
    /** Courses never drift further sideways than this from the origin, so they fit side by side. */
    static final int MAX_DRIFT = 4;
    /** Courses stay between these heights relative to the origin. */
    static final int MIN_RISE = -6;

    static final int MAX_RISE = 24;
    /** A player this many blocks below the lowest block of a section is falling. */
    static final int FALL_MARGIN = 3;

    private CourseGenerator() {}

    /** How many jumps a course of this difficulty has: 13 for the easiest, 40 for the hardest. */
    static int jumpCount(int difficulty) {
        checkDifficulty(difficulty);
        return 10 + 3 * difficulty;
    }

    /** Jumps between checkpoints: 4 on easy courses, up to 8 on the hardest. */
    static int jumpsPerCheckpoint(int difficulty) {
        checkDifficulty(difficulty);
        return 3 + (difficulty + 1) / 2;
    }

    /** The jumps a course of this difficulty is made of. Harder courses have longer jumps. */
    static List<Jump> jumpsFor(int difficulty) {
        checkDifficulty(difficulty);
        return switch (ParkourCourse.Difficulty.of(difficulty)) {
            case LATWA ->
                List.of(
                        new Jump(1, 0, 0),
                        new Jump(2, 0, 0),
                        new Jump(1, 1, 0),
                        new Jump(1, -1, 0),
                        new Jump(2, -1, 0),
                        new Jump(1, 0, 1));
            case SREDNIA ->
                List.of(
                        new Jump(2, 0, 0),
                        new Jump(3, 0, 0),
                        new Jump(1, 1, 0),
                        new Jump(2, 1, 0),
                        new Jump(2, -1, 0),
                        new Jump(3, -1, 0),
                        new Jump(2, 0, 1));
            case TRUDNA ->
                List.of(
                        new Jump(3, 0, 0),
                        new Jump(2, 1, 0),
                        new Jump(2, 1, 1),
                        new Jump(3, -1, 0),
                        new Jump(4, -1, 0),
                        new Jump(3, 0, 1));
            case BARDZO_TRUDNA ->
                List.of(
                        new Jump(4, 0, 0),
                        new Jump(3, 1, 0),
                        new Jump(4, -1, 0),
                        new Jump(3, 0, 1),
                        new Jump(2, 1, 1),
                        new Jump(3, -1, 1));
        };
    }

    static Blueprint generate(int difficulty, BlockPos origin, long seed) {
        Random random = new Random(seed);
        List<Jump> jumps = jumpsFor(difficulty);
        int total = jumpCount(difficulty);
        int perCheckpoint = jumpsPerCheckpoint(difficulty);

        List<Piece> pieces = new ArrayList<>();
        List<Checkpoint> checkpoints = new ArrayList<>();
        platform(pieces, origin, Kind.START);
        Checkpoint start = null;
        // The point (start or checkpoint) the current section begins at, and its lowest block.
        BlockPos sectionPoint = origin;
        int sectionLowest = origin.y();
        // The block the next jump starts from: the front edge of the platform.
        BlockPos from = new BlockPos(origin.x() + 1, origin.y(), origin.z());

        for (int done = 1; done <= total; done++) {
            Jump jump = pick(jumps, random, from, origin);
            int sideways = jump.side() == 0 ? 0 : sideDirection(random, from, origin);
            BlockPos landing = new BlockPos(from.x() + jump.gap() + 1, from.y() + jump.up(), from.z() + sideways);
            sectionLowest = Math.min(sectionLowest, landing.y());
            boolean last = done == total;
            if (!last && done % perCheckpoint != 0) {
                pieces.add(new Piece(landing, Kind.JUMP));
                from = landing;
                continue;
            }
            // Platforms are 3 long: the player lands on the near edge, the middle is the point.
            BlockPos middle = new BlockPos(landing.x() + 1, landing.y(), landing.z());
            Checkpoint finishedSection = pointAt(sectionPoint, sectionLowest);
            if (start == null) {
                start = finishedSection;
            } else {
                checkpoints.add(finishedSection);
            }
            if (last) {
                platform(pieces, middle, Kind.FINISH);
                return new Blueprint(pieces, start, checkpoints, middle);
            }
            platform(pieces, middle, Kind.CHECKPOINT);
            sectionPoint = middle;
            sectionLowest = middle.y();
            from = new BlockPos(middle.x() + 1, middle.y(), middle.z());
        }
        throw new IllegalStateException("course ended without a finish");
    }

    /** A point on {@code block}; falling below the lowest block of its section sends players back. */
    private static Checkpoint pointAt(BlockPos block, int sectionLowest) {
        // Looking along +X is yaw -90 in Minecraft.
        SpawnPoint spawn = new SpawnPoint(block.x() + 0.5, block.y() + 1, block.z() + 0.5, -90f, 0f);
        return new Checkpoint(spawn, Math.min(block.y(), sectionLowest) - FALL_MARGIN);
    }

    /** Picks a jump that keeps the course within its height limits. */
    private static Jump pick(List<Jump> jumps, Random random, BlockPos from, BlockPos origin) {
        int rise = from.y() - origin.y();
        List<Jump> allowed = new ArrayList<>();
        for (Jump jump : jumps) {
            int next = rise + jump.up();
            if (next >= MIN_RISE && next <= MAX_RISE) {
                allowed.add(jump);
            }
        }
        return allowed.get(random.nextInt(allowed.size()));
    }

    /** Sideways step, turning back towards the middle when the course drifted far enough. */
    private static int sideDirection(Random random, BlockPos from, BlockPos origin) {
        int drift = from.z() - origin.z();
        if (drift >= MAX_DRIFT) {
            return -1;
        }
        if (drift <= -MAX_DRIFT) {
            return 1;
        }
        return random.nextBoolean() ? 1 : -1;
    }

    private static void platform(List<Piece> pieces, BlockPos middle, Kind kind) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                pieces.add(new Piece(new BlockPos(middle.x() + dx, middle.y(), middle.z() + dz), kind));
            }
        }
    }

    private static void checkDifficulty(int difficulty) {
        if (difficulty < ParkourCourse.MIN_DIFFICULTY || difficulty > ParkourCourse.MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be 1-10: " + difficulty);
        }
    }
}
