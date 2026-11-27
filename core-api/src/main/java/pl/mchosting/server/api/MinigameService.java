package pl.mchosting.server.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Minigame queues (Spleef, TNT Run, ...). The minigames plugin provides it through Bukkit's
 * services manager; the hub calls it when a player walks into a gate or clicks a game in the
 * compass menu.
 *
 * <p>Queued players keep walking around the hub until a match is ready. The service itself tells
 * the player what happened (joined, already queued, no arena), so callers only need the result to
 * decide what to do next. Call it from the main server thread only.
 */
public interface MinigameService {

    /** Ids (like {@link Stats#SPLEEF}) of games with at least one ready arena. */
    Set<String> availableGames();

    /** Puts an online player in the queue of {@code gameId}, leaving any other queue first. */
    QueueJoin join(UUID playerId, String gameId);

    /** Takes the player out of their queue. Returns false if they were not queued. */
    boolean leave(UUID playerId);

    /** Game the player is queued for, if any. */
    Optional<String> queuedGame(UUID playerId);

    /** Players waiting in the queue of {@code gameId}. */
    int queued(String gameId);

    /** Players in running matches of {@code gameId}, spectators not counted. */
    int playing(String gameId);
}
