package pl.mchosting.server.minigry;

/** Coordinates of one block. */
record BlockPos(int x, int y, int z) {

    BlockPos below() {
        return new BlockPos(x, y - 1, z);
    }
}
