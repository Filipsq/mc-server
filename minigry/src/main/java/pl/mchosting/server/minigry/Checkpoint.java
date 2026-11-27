package pl.mchosting.server.minigry;

import java.util.Objects;

/**
 * A point on a parkour course where a player comes back after falling.
 *
 * @param spawn where the player appears (standing on the checkpoint block, looking ahead)
 * @param fallY a player below this height after reaching this point goes back to it
 */
record Checkpoint(SpawnPoint spawn, int fallY) {

    Checkpoint {
        Objects.requireNonNull(spawn, "spawn");
        if (fallY >= spawn.y()) {
            throw new IllegalArgumentException("fall height must be below the checkpoint");
        }
    }

    /** The block the player stands on at this point. */
    BlockPos block() {
        return ParkourCourse.standingBlock(spawn.x(), spawn.y(), spawn.z());
    }
}
