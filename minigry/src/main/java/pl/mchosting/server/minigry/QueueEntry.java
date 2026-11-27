package pl.mchosting.server.minigry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One player waiting for a match.
 *
 * @param playerId the player
 * @param rating {@link SkillRating#rating} for this game
 * @param newcomer whether the player still has fewer than {@link SkillRating#NEWCOMER_GAMES} matches
 * @param joinedAt when the player joined the queue
 */
record QueueEntry(UUID playerId, int rating, boolean newcomer, Instant joinedAt) {

    QueueEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }

    Duration waited(Instant now) {
        Duration waited = Duration.between(joinedAt, now);
        return waited.isNegative() ? Duration.ZERO : waited;
    }
}
