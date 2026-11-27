package pl.mchosting.server.minigry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * One player's attempt at a course: the clock and the checkpoints reached. The listener feeds it
 * positions; it says what happened.
 */
final class ParkourRun {

    /** What a move meant for the run. */
    enum Step {
        NOTHING,
        /** The player left the start platform; the clock runs. */
        STARTED,
        /** Back on the start platform after starting: the clock waits for the next start. */
        RESET,
        CHECKPOINT,
        /** Landed on a later checkpoint without touching an earlier one; it does not count. */
        SKIPPED,
        FINISHED,
        /** Reached the finish without every checkpoint; the run goes on. */
        FINISH_MISSING_CHECKPOINTS
    }

    private final ParkourCourse course;
    private Instant startedAt;
    private int reached;
    private int falls;
    private Duration finalTime;

    ParkourRun(ParkourCourse course) {
        if (!course.problems().isEmpty()) {
            throw new IllegalArgumentException("course " + course.id() + " is not set up");
        }
        this.course = course;
    }

    ParkourCourse course() {
        return course;
    }

    boolean started() {
        return startedAt != null;
    }

    boolean finished() {
        return finalTime != null;
    }

    /** Checkpoints reached so far (0 before the first one). */
    int reached() {
        return reached;
    }

    int falls() {
        return falls;
    }

    /**
     * A move of the player.
     *
     * @param feet the block the player's feet are in
     * @param standing the block the player stands on, or null while in the air
     */
    Step moved(BlockPos feet, BlockPos standing, Instant now) {
        Objects.requireNonNull(feet, "feet");
        if (finished()) {
            return Step.NOTHING;
        }
        if (!started()) {
            if (course.aboveStart(feet)) {
                return Step.NOTHING;
            }
            startedAt = now;
            return Step.STARTED;
        }
        if (standing == null) {
            return Step.NOTHING;
        }
        if (reached == 0 && course.isStart(standing)) {
            startedAt = null;
            falls = 0;
            return Step.RESET;
        }
        OptionalInt checkpoint = course.checkpointAt(standing);
        if (checkpoint.isPresent()) {
            int index = checkpoint.getAsInt();
            if (index == reached + 1) {
                reached = index;
                return Step.CHECKPOINT;
            }
            return index > reached + 1 ? Step.SKIPPED : Step.NOTHING;
        }
        if (course.isFinish(standing)) {
            if (reached < course.checkpoints().size()) {
                return Step.FINISH_MISSING_CHECKPOINTS;
            }
            finalTime = Duration.between(startedAt, now);
            return Step.FINISHED;
        }
        return Step.NOTHING;
    }

    /** Whether a player at height {@code y} fell off and must go back to the last checkpoint. */
    boolean fellAt(double y) {
        return !finished() && y < course.respawnAfter(reached).fallY();
    }

    /** The player fell: where they go back to. */
    Checkpoint fall() {
        falls++;
        return course.respawnAfter(reached);
    }

    /** Back to the start with the clock stopped. */
    void restart() {
        startedAt = null;
        reached = 0;
        falls = 0;
        finalTime = null;
    }

    /** Time on the clock: the final time when finished, zero before the start. */
    Duration elapsed(Instant now) {
        if (finalTime != null) {
            return finalTime;
        }
        if (startedAt == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(startedAt, now);
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}
