package pl.mchosting.server.minigry;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Blocks a player stands on. A player is 0.6 blocks wide, so standing on an edge they touch up
 * to four blocks; TNT Run removes all of them, otherwise edge-walking would be a cheat.
 */
final class Footprint {

    static final double HALF_WIDTH = 0.3;
    /** How far below the feet a block still counts as "under" the player. */
    static final double DEPTH = 0.05;

    private Footprint() {}

    static Set<BlockPos> under(double x, double y, double z) {
        int blockY = (int) Math.floor(y - DEPTH);
        Set<BlockPos> blocks = new LinkedHashSet<>();
        for (double dx : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
            for (double dz : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
                blocks.add(new BlockPos((int) Math.floor(x + dx), blockY, (int) Math.floor(z + dz)));
            }
        }
        return blocks;
    }
}
