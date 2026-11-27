package pl.mchosting.server.hub;

import java.util.Locale;
import java.util.Objects;

/**
 * The inside of a minigame gate in the valley: walking into it joins the game's queue. A box of
 * blocks between two corners (both included) in one world.
 */
record GateRegion(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    GateRegion {
        Objects.requireNonNull(world, "world");
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min corner must not be above max corner");
        }
    }

    /** Box between two block corners given in any order. */
    static GateRegion between(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new GateRegion(
                world,
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2));
    }

    boolean contains(String inWorld, int x, int y, int z) {
        return world.equals(inWorld) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** {@code "world x1 y1 z1 x2 y2 z2"}, the form kept in config.yml. */
    String encode() {
        return String.format(Locale.ROOT, "%s %d %d %d %d %d %d", world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Reads the config.yml form back.
     *
     * @throws IllegalArgumentException if the text is broken
     */
    static GateRegion decode(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 7) {
            throw new IllegalArgumentException("expected world and 6 numbers: " + text);
        }
        int[] n = new int[6];
        for (int i = 0; i < 6; i++) {
            n[i] = Integer.parseInt(parts[i + 1]);
        }
        return between(parts[0], n[0], n[1], n[2], n[3], n[4], n[5]);
    }
}
