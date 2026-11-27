package pl.mchosting.server.api;

/** What happened when a player tried to join a minigame queue. */
public enum QueueJoin {
    /** The player is now waiting in the queue. */
    JOINED,
    /** The player left another queue and is now waiting in this one. */
    SWITCHED,
    /** The player was already waiting in this queue; nothing changed. */
    ALREADY_QUEUED,
    /** The player is playing a match right now. */
    IN_MATCH,
    /** No game with this id exists. */
    UNKNOWN_GAME,
    /** The game exists but has no ready arena yet. */
    NO_ARENA
}
