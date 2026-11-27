package pl.mchosting.server.link;

import java.time.Clock;
import java.util.Optional;
import pl.mchosting.server.link.ticket.ReturnTicket;

/** Signs the return ticket a player takes to the hub, when this server has a key. */
final class TicketIssuer {

    private final Optional<LinkConfig.Signing> signing;
    private final Clock clock;

    TicketIssuer(Optional<LinkConfig.Signing> signing, Clock clock) {
        this.signing = signing;
        this.clock = clock;
    }

    /**
     * The cookie's bytes, or empty when there is no key or the name can't be put in a ticket (a
     * non-premium server may allow names the hub never would). Either way the player still goes.
     */
    Optional<byte[]> issue(String playerName) {
        return signing.flatMap(s -> {
            try {
                return Optional.of(new ReturnTicket(s.serverId(), playerName, clock.instant()).sign(s.key()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }
}
