package pl.mchosting.server.minigry;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Which ready arenas are free. An arena hosts one match at a time. */
final class ArenaPool {

    private final Map<String, GameType> ready = new LinkedHashMap<>();
    private final Set<String> busy = new HashSet<>();

    /** Makes an arena available for matches (or not, when staff switch it off). */
    void setReady(String arenaId, GameType type, boolean isReady) {
        if (isReady) {
            ready.put(arenaId, type);
        } else {
            ready.remove(arenaId);
        }
    }

    /** Takes a free arena for a match of {@code type}. */
    Optional<String> claim(GameType type) {
        for (Map.Entry<String, GameType> arena : ready.entrySet()) {
            if (arena.getValue() == type && !busy.contains(arena.getKey())) {
                busy.add(arena.getKey());
                return Optional.of(arena.getKey());
            }
        }
        return Optional.empty();
    }

    void release(String arenaId) {
        busy.remove(arenaId);
    }

    boolean isBusy(String arenaId) {
        return busy.contains(arenaId);
    }

    boolean hasArena(GameType type) {
        return ready.containsValue(type);
    }

    int free(GameType type) {
        int free = 0;
        for (Map.Entry<String, GameType> arena : ready.entrySet()) {
            if (arena.getValue() == type && !busy.contains(arena.getKey())) {
                free++;
            }
        }
        return free;
    }
}
