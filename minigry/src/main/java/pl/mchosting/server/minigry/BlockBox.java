package pl.mchosting.server.minigry;

/** A box of blocks between two corners, both included. */
record BlockBox(BlockPos min, BlockPos max) {

    BlockBox {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("min must not be above max: " + min + " " + max);
        }
    }

    /** Box between two corners given in any order. */
    static BlockBox between(BlockPos a, BlockPos b) {
        return new BlockBox(
                new BlockPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new BlockPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }

    boolean contains(BlockPos pos) {
        return pos.x() >= min.x()
                && pos.x() <= max.x()
                && pos.y() >= min.y()
                && pos.y() <= max.y()
                && pos.z() >= min.z()
                && pos.z() <= max.z();
    }

    long volume() {
        return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }
}
