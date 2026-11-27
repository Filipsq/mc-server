package pl.mchosting.server.minigry;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** Remembers how arena blocks looked before a match changed them, to put them back afterwards. */
final class BlockChanges {

    private final Map<BlockPos, BlockData> original = new HashMap<>();

    /** Call before changing {@code block}; only the first state is kept. */
    void remember(Block block) {
        original.putIfAbsent(pos(block), block.getBlockData());
    }

    void restore(World world) {
        original.forEach(
                (pos, data) -> world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(data, false));
        original.clear();
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }
}
