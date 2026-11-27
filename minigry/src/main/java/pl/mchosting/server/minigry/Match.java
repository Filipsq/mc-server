package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import pl.mchosting.server.api.MatchResult;

/**
 * Rules of one last-player-standing match (Spleef, TNT Run): countdown, eliminations, places and
 * kills. Knows nothing about the server, so every rule is tested without Minecraft.
 */
final class Match {

    enum Phase {
        COUNTDOWN,
        RUNNING,
        ENDED
    }

    private final int minPlayers;
    private final Set<UUID> roster;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, Integer> placements = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private Phase phase = Phase.COUNTDOWN;
    private int countdownLeft;
    private int starters;

    Match(List<UUID> players, int minPlayers, int countdownSeconds) {
        if (players.size() < minPlayers || countdownSeconds < 1) {
            throw new IllegalArgumentException("need " + minPlayers + " players and a countdown");
        }
        this.roster = new LinkedHashSet<>(players);
        if (roster.size() != players.size()) {
            throw new IllegalArgumentException("player listed twice");
        }
        this.minPlayers = minPlayers;
        this.countdownLeft = countdownSeconds;
        alive.addAll(roster);
    }

    Phase phase() {
        return phase;
    }

    /** Everyone still in the match: alive or already out (spectating). */
    Set<UUID> roster() {
        return Collections.unmodifiableSet(roster);
    }

    Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    boolean isAlive(UUID playerId) {
        return alive.contains(playerId);
    }

    /** Players who were there when the match started, 0 before it starts. */
    int starters() {
        return starters;
    }

    int countdownLeft() {
        return countdownLeft;
    }

    /**
     * One second of the countdown. Returns the seconds still left; at 0 the match is running.
     *
     * @throws IllegalStateException if the match is not counting down or has too few players
     */
    int tickCountdown() {
        if (phase != Phase.COUNTDOWN) {
            throw new IllegalStateException("not counting down");
        }
        if (!canStart()) {
            throw new IllegalStateException("too few players to start");
        }
        countdownLeft--;
        if (countdownLeft == 0) {
            phase = Phase.RUNNING;
            starters = alive.size();
        }
        return countdownLeft;
    }

    /** False when players left during the countdown and the match must be called off. */
    boolean canStart() {
        return alive.size() >= minPlayers;
    }

    /**
     * Takes a player out. Before the start the player simply leaves the match. While running they
     * get the worst free place, and the kill goes to {@code killer} if it was someone else in the
     * match. When one player is left they win and the match ends.
     *
     * @return the place given, or empty if the player was not alive or the match is not running
     */
    OptionalInt eliminate(UUID playerId, UUID killer) {
        if (!alive.contains(playerId)) {
            return OptionalInt.empty();
        }
        if (phase == Phase.COUNTDOWN) {
            alive.remove(playerId);
            roster.remove(playerId);
            return OptionalInt.empty();
        }
        if (phase == Phase.ENDED) {
            return OptionalInt.empty();
        }
        int place = alive.size();
        alive.remove(playerId);
        placements.put(playerId, place);
        if (killer != null && !killer.equals(playerId) && roster.contains(killer)) {
            kills.merge(killer, 1, Integer::sum);
        }
        if (alive.size() == 1) {
            UUID winner = alive.iterator().next();
            placements.put(winner, 1);
            phase = Phase.ENDED;
        } else if (alive.isEmpty()) {
            phase = Phase.ENDED;
        }
        return OptionalInt.of(place);
    }

    /**
     * Ends a running match that took too long. Everyone still standing shares the worst of their
     * places, so nobody wins and nobody is rewarded for hiding.
     */
    void endByTimeout() {
        if (phase != Phase.RUNNING) {
            throw new IllegalStateException("match is not running");
        }
        int shared = alive.size();
        for (UUID survivor : alive) {
            placements.put(survivor, shared);
        }
        phase = Phase.ENDED;
    }

    /** A player stops watching; their result is kept. */
    void forget(UUID playerId) {
        if (alive.contains(playerId)) {
            throw new IllegalStateException("eliminate the player first");
        }
        roster.remove(playerId);
    }

    /** The winner, if the match ended with exactly one player standing. */
    Optional<UUID> winner() {
        if (phase != Phase.ENDED) {
            return Optional.empty();
        }
        List<UUID> first = new ArrayList<>();
        placements.forEach((player, place) -> {
            if (place == 1) {
                first.add(player);
            }
        });
        return first.size() == 1 ? Optional.of(first.getFirst()) : Optional.empty();
    }

    int kills(UUID playerId) {
        return kills.getOrDefault(playerId, 0);
    }

    /** Result of every player who got a place, including those who already stopped watching. */
    Map<UUID, MatchResult> results() {
        Map<UUID, MatchResult> results = new HashMap<>();
        placements.forEach((player, place) -> results.put(player, new MatchResult(starters, place, kills(player))));
        return results;
    }
}
