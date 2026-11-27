package pl.mchosting.server.link.ticket;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Constants and helpers of the cookie format, shared by signing and reading. */
final class TicketFormat {

    /** Version tag; part of what is signed, so a later format can't be mistaken for this one. */
    static final String PREFIX = "mchl1.";

    static final String ALGORITHM = "Ed25519";

    /** Cookies may hold 5 KiB; ours are about 150 bytes, so anything big is not ours. */
    static final int MAX_BYTES = 1024;

    private TicketFormat() {}

    static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
