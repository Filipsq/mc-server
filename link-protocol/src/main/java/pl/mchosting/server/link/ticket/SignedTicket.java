package pl.mchosting.server.link.ticket;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * A cookie read back from a player's game. Its contents are whatever the game sent, so nothing in
 * {@link #unverified()} may be trusted until {@link TicketCheck} has checked the signature.
 */
public final class SignedTicket {

    private final ReturnTicket ticket;
    private final byte[] signedPart;
    private final byte[] signature;

    private SignedTicket(ReturnTicket ticket, byte[] signedPart, byte[] signature) {
        this.ticket = ticket;
        this.signedPart = signedPart;
        this.signature = signature;
    }

    /** Empty for no cookie, a foreign one or anything malformed. Never throws on bad input. */
    public static Optional<SignedTicket> parse(byte[] cookie) {
        if (cookie == null || cookie.length == 0 || cookie.length > TicketFormat.MAX_BYTES) {
            return Optional.empty();
        }
        String text = new String(cookie, StandardCharsets.US_ASCII);
        if (!text.startsWith(TicketFormat.PREFIX)) {
            return Optional.empty();
        }
        int dot = text.lastIndexOf('.');
        if (dot <= TicketFormat.PREFIX.length()) {
            return Optional.empty();
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(
                    decoder.decode(text.substring(TicketFormat.PREFIX.length(), dot)), StandardCharsets.UTF_8);
            byte[] signature = decoder.decode(text.substring(dot + 1));
            String[] fields = payload.split("\n", -1);
            if (fields.length != 3) {
                return Optional.empty();
            }
            ReturnTicket ticket =
                    new ReturnTicket(fields[0], fields[1], Instant.ofEpochSecond(Long.parseLong(fields[2])));
            byte[] signedPart = text.substring(0, dot).getBytes(StandardCharsets.US_ASCII);
            return Optional.of(new SignedTicket(ticket, signedPart, signature));
        } catch (IllegalArgumentException | DateTimeException e) {
            // Bad base64, a non-number time (NumberFormatException is an IllegalArgumentException) or bad fields.
            return Optional.empty();
        }
    }

    /** The claimed contents, only for looking up the sender's key. Check before using anything else. */
    public ReturnTicket unverified() {
        return ticket;
    }

    boolean signedBy(PublicKey key) {
        try {
            Signature check = Signature.getInstance(TicketFormat.ALGORITHM);
            check.initVerify(key);
            check.update(signedPart);
            return check.verify(signature);
        } catch (GeneralSecurityException e) {
            // A key of another type, or a signature of the wrong length.
            return false;
        }
    }
}
