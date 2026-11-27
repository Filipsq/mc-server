package pl.mchosting.server.minigry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Queue of one game. Picks groups of players with similar ratings; newcomers play with newcomers
 * until they waited a while (then they may meet anyone, so they never wait forever). Players who waited longest are served first.
 */
final class Matchmaker {

    private final int minPlayers;
    private final int maxPlayers;
    private final MatchmakingRules rules;
    private final Map<UUID, QueueEntry> entries = new LinkedHashMap<>();

    Matchmaker(int minPlayers, int maxPlayers, MatchmakingRules rules) {
        if (minPlayers < 2 || maxPlayers < minPlayers) {
            throw new IllegalArgumentException("bad player limits " + minPlayers + "-" + maxPlayers);
        }
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.rules = rules;
    }

    /** Adds a player; returns false if they were already queued. */
    boolean add(QueueEntry entry) {
        return entries.putIfAbsent(entry.playerId(), entry) == null;
    }

    boolean remove(UUID playerId) {
        return entries.remove(playerId) != null;
    }

    boolean contains(UUID playerId) {
        return entries.containsKey(playerId);
    }

    Optional<QueueEntry> entry(UUID playerId) {
        return Optional.ofNullable(entries.get(playerId));
    }

    int size() {
        return entries.size();
    }

    /**
     * Takes the next ready group out of the queue, or returns empty if nobody can play yet. A group
     * is ready when it is full, or has enough players and its oldest player waited {@link
     * MatchmakingRules#fillWait()}. Call it again to form more matches.
     */
    Optional<List<QueueEntry>> nextMatch(Instant now) {
        List<QueueEntry> byAge = new ArrayList<>(entries.values());
        byAge.sort(Comparator.comparing(QueueEntry::joinedAt));
        for (QueueEntry anchor : byAge) {
            List<QueueEntry> group = groupFor(anchor, byAge, now);
            boolean full = group.size() >= maxPlayers;
            boolean enough = group.size() >= minPlayers && anchor.waited(now).compareTo(rules.fillWait()) >= 0;
            if (full || enough) {
                List<QueueEntry> match = List.copyOf(group.subList(0, Math.min(group.size(), maxPlayers)));
                match.forEach(entry -> entries.remove(entry.playerId()));
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    /** The anchor first, then everyone who fits with it, closest rating first. */
    private List<QueueEntry> groupFor(QueueEntry anchor, List<QueueEntry> byAge, Instant now) {
        List<QueueEntry> others = new ArrayList<>();
        for (QueueEntry other : byAge) {
            if (other != anchor && fits(anchor, other, now)) {
                others.add(other);
            }
        }
        others.sort(Comparator.comparingInt((QueueEntry other) -> Math.abs(other.rating() - anchor.rating()))
                .thenComparing(QueueEntry::joinedAt));
        List<QueueEntry> group = new ArrayList<>();
        group.add(anchor);
        group.addAll(others);
        return group;
    }

    private boolean fits(QueueEntry anchor, QueueEntry other, Instant now) {
        if (anchor.newcomer() != other.newcomer()) {
            // Newcomers are protected from experienced players until the newcomer waited a while.
            QueueEntry newcomer = anchor.newcomer() ? anchor : other;
            if (newcomer.waited(now).compareTo(rules.mixNewcomersAfter()) < 0) {
                return false;
            }
        }
        long difference = Math.abs((long) anchor.rating() - other.rating());
        return difference <= rules.window(anchor.waited(now));
    }
}
