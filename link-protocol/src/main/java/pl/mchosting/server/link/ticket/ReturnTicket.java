package pl.mchosting.server.link.ticket;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * "This player left server {@code serverId} for the hub at {@code issuedAt}". A customer's server
 * signs it with its own key and stores it as a cookie in the player's game before {@code /hub}; the
 * hub reads it back and, once the signature checks out, offers a way back.
 *
 * <p>The ticket never says where to send the player back: the hub looks the address up by {@code
 * serverId} in the panel, so a forged or edited cookie can't send anyone to a server of its choosing.
 */
public record ReturnTicket(String serverId, String playerName, Instant issuedAt) {

    /** The cookie's key in the player's game: {@code mchosting:return}. */
    public static final String COOKIE_NAMESPACE = "mchosting";

    public static final String COOKIE_KEY = "return";

    /** Panel server ids (cuid) and anything similar; nothing that could break the format. */
    static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** Java names, plus the dot Floodgate puts before Bedrock names. */
    static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.]{1,32}");

    public ReturnTicket {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (!SERVER_ID.matcher(serverId).matches()) {
            throw new IllegalArgumentException("invalid server id");
        }
        if (!PLAYER_NAME.matcher(playerName).matches()) {
            throw new IllegalArgumentException("invalid player name");
        }
        issuedAt = Instant.ofEpochSecond(issuedAt.getEpochSecond());
    }

    /** The cookie's bytes: {@code mchl1.<payload>.<signature>}, both parts base64url. */
    public byte[] sign(PrivateKey key) {
        String signed = TicketFormat.PREFIX + TicketFormat.encode(payload());
        try {
            Signature signature = Signature.getInstance(TicketFormat.ALGORITHM);
            signature.initSign(key);
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
            return (signed + "." + sig).getBytes(StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("not an Ed25519 private key", e);
        }
    }

    String payload() {
        return serverId + "\n" + playerName + "\n" + issuedAt.getEpochSecond();
    }
}
