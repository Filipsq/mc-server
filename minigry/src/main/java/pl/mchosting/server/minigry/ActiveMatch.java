package pl.mchosting.server.minigry;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

/** A match being played in one arena: the rules from {@link Match} plus the world it changes. */
final class ActiveMatch {

    static final int COUNTDOWN_SECONDS = 10;
    /** Seconds between the end of the match and everyone going back to the hub. */
    static final int ENDING_SECONDS = 5;

    static final Duration KILL_MEMORY = Duration.ofSeconds(6);

    final GameType type;
    final ArenaConfig arena;
    final World world;
    final Match match;
    final BlockChanges changes = new BlockChanges();
    final KillCredit credit = new KillCredit(KILL_MEMORY);
    /** TNT Run blocks already scheduled to disappear. */
    final Set<BlockPos> fading = new HashSet<>();
    /** Queue entries, so players go back to the front of the queue if the match is called off. */
    final Map<UUID, QueueEntry> entries = new HashMap<>();

    Instant startedAt;
    int endingLeft = -1;

    ActiveMatch(GameType type, ArenaConfig arena, World world, List<QueueEntry> players) {
        this.type = type;
        this.arena = arena;
        this.world = world;
        this.match =
                new Match(players.stream().map(QueueEntry::playerId).toList(), type.minPlayers(), COUNTDOWN_SECONDS);
        players.forEach(entry -> entries.put(entry.playerId(), entry));
    }

    boolean isRunning() {
        return match.phase() == Match.Phase.RUNNING;
    }

    boolean inRegion(BlockPos pos) {
        return arena.region().contains(pos);
    }

    Location spawn(int index) {
        SpawnPoint spawn = arena.spawnFor(index);
        return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    /** Where spectators watch from: above the first spawn. */
    Location spectatorPoint() {
        return spawn(0).add(0, 8, 0);
    }
}
