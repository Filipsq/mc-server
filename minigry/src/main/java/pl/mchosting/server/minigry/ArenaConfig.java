package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One arena as staff set it up with {@code /minigryadmin}. Plain data, so the checks are tested
 * without a server.
 *
 * @param id short name, for example {@code spleef1}
 * @param type which game is played here
 * @param world name of the world the arena is in (not the hub world)
 * @param spawns where players start; with fewer spawns than players they share them
 * @param region the blocks the game may change and that are restored after each match
 * @param eliminationY a player below this height is out
 * @param enabled whether staff switched the arena on
 */
record ArenaConfig(
        String id,
        GameType type,
        String world,
        List<SpawnPoint> spawns,
        BlockBox region,
        Integer eliminationY,
        boolean enabled) {

    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    /** Larger regions would make restoring the arena after a match too slow. */
    static final long MAX_REGION_BLOCKS = 500_000;

    ArenaConfig {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(world, "world");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("arena id must match " + ID + ": " + id);
        }
        spawns = List.copyOf(spawns);
    }

    static ArenaConfig create(String id, GameType type, String world) {
        return new ArenaConfig(id, type, world, List.of(), null, null, false);
    }

    /** What is still missing before the arena can host matches; empty when it is ready. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (spawns.isEmpty()) {
            problems.add("brak punktów startowych (/minigryadmin arena spawn " + id + ")");
        } else if (spawns.size() < type.minSpawns()) {
            problems.add("potrzeba co najmniej " + type.minSpawns() + " punktów startowych, naprzeciw siebie");
        }
        if (region == null) {
            problems.add("brak obszaru (/minigryadmin arena rog1 i rog2 " + id + ")");
        } else if (region.volume() > MAX_REGION_BLOCKS) {
            problems.add("obszar jest za duży (" + region.volume() + " bloków, limit " + MAX_REGION_BLOCKS + ")");
        }
        if (eliminationY == null) {
            problems.add("brak wysokości odpadania (/minigryadmin arena wysokosc " + id + ")");
        } else {
            for (SpawnPoint spawn : spawns) {
                if (spawn.y() <= eliminationY) {
                    problems.add("punkt startowy jest poniżej wysokości odpadania");
                    break;
                }
            }
        }
        return problems;
    }

    /** Set up completely and switched on. */
    boolean isReady() {
        return enabled && problems().isEmpty();
    }

    /** Start point of the {@code index}-th player, sharing spawns when there are more players. */
    SpawnPoint spawnFor(int index) {
        if (spawns.isEmpty()) {
            throw new IllegalStateException("arena " + id + " has no spawns");
        }
        return spawns.get(index % spawns.size());
    }

    ArenaConfig withSpawnAdded(SpawnPoint spawn) {
        List<SpawnPoint> updated = new ArrayList<>(spawns);
        updated.add(spawn);
        return new ArenaConfig(id, type, world, updated, region, eliminationY, enabled);
    }

    ArenaConfig withoutSpawns() {
        return new ArenaConfig(id, type, world, List.of(), region, eliminationY, enabled);
    }

    ArenaConfig withRegion(BlockBox box) {
        return new ArenaConfig(id, type, world, spawns, box, eliminationY, enabled);
    }

    ArenaConfig withEliminationY(int y) {
        return new ArenaConfig(id, type, world, spawns, region, y, enabled);
    }

    ArenaConfig withEnabled(boolean on) {
        return new ArenaConfig(id, type, world, spawns, region, eliminationY, on);
    }
}
