package pl.mchosting.server.link.ticket;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;

/** Decides whether the hub may trust a ticket. */
public final class TicketCheck {

    /**
     * How long a ticket counts. Long enough for a player who leaves the hub for a minigame and comes
     * back; the cookie only lives in their game until they close it, and is bound to their name.
     */
    public static final Duration MAX_AGE = Duration.ofHours(6);

    /** Servers' clocks may differ a little. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(2);

    public enum Verdict {
        VALID,
        BAD_SIGNATURE,
        /** Signed for someone else: a cookie copied from another player's game. */
        OTHER_PLAYER,
        EXPIRED,
        FROM_FUTURE
    }

    private TicketCheck() {}

    /**
     * @param key the public key the panel has for {@code ticket.unverified().serverId()}
     * @param playerName the name the hub's login confirmed with Mojang
     */
    public static Verdict check(SignedTicket ticket, PublicKey key, String playerName, Instant now) {
        if (!ticket.signedBy(key)) {
            return Verdict.BAD_SIGNATURE;
        }
        ReturnTicket contents = ticket.unverified();
        // Minecraft names are unique regardless of case; a non-premium server may have the case wrong.
        if (!contents.playerName().equalsIgnoreCase(playerName)) {
            return Verdict.OTHER_PLAYER;
        }
        if (contents.issuedAt().isAfter(now.plus(CLOCK_SKEW))) {
            return Verdict.FROM_FUTURE;
        }
        if (contents.issuedAt().isBefore(now.minus(MAX_AGE))) {
            return Verdict.EXPIRED;
        }
        return Verdict.VALID;
    }
}
