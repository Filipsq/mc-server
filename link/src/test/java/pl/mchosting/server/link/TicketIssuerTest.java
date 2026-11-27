package pl.mchosting.server.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.link.ticket.LinkKeys;
import pl.mchosting.server.link.ticket.SignedTicket;
import pl.mchosting.server.link.ticket.TicketCheck;

class TicketIssuerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    @Test
    void issuesSignedTicketWithServerPlayerAndClockTime() {
        var keys = LinkKeys.generate();
        TicketIssuer issuer = new TicketIssuer(signing(keys), Clock.fixed(NOW, ZoneOffset.UTC));

        SignedTicket ticket =
                SignedTicket.parse(issuer.issue("Fiq").orElseThrow()).orElseThrow();

        assertEquals("cm3abc", ticket.unverified().serverId());
        assertEquals("Fiq", ticket.unverified().playerName());
        assertEquals(NOW, ticket.unverified().issuedAt());
        assertEquals(TicketCheck.Verdict.VALID, TicketCheck.check(ticket, keys.getPublic(), "Fiq", NOW));
    }

    @Test
    void withoutKeyDoesNotIssueTicket() {
        TicketIssuer issuer = new TicketIssuer(Optional.empty(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(issuer.issue("Fiq").isEmpty());
    }

    @Test
    void invalidPlayerNameDoesNotThrowOrIssueTicket() {
        var keys = LinkKeys.generate();
        TicketIssuer issuer = new TicketIssuer(signing(keys), Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(issuer.issue("zły nick").isEmpty());
        assertTrue(issuer.issue("a".repeat(40)).isEmpty());
    }

    private static Optional<LinkConfig.Signing> signing(java.security.KeyPair keys) {
        return Optional.of(new LinkConfig.Signing("cm3abc", keys.getPrivate()));
    }
}
