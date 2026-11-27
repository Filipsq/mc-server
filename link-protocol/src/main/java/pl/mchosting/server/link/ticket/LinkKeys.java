package pl.mchosting.server.link.ticket;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * Ed25519 keys as the panel hands them out: base64 of DER, PKCS#8 for the private key (in the
 * customer server's config) and X.509 for the public one (served by the panel to the hub). Node's
 * {@code generateKeyPairSync("ed25519", {format: "der"})} writes exactly these.
 */
public final class LinkKeys {

    private LinkKeys() {}

    public static Optional<PrivateKey> privateKey(String base64) {
        try {
            return Optional.of(factory().generatePrivate(new PKCS8EncodedKeySpec(decode(base64))));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<PublicKey> publicKey(String base64) {
        try {
            return Optional.of(factory().generatePublic(new X509EncodedKeySpec(decode(base64))));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A new key pair; the panel makes the real ones, this is for tests and local servers. */
    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance(TicketFormat.ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Java without Ed25519", e);
        }
    }

    public static String encode(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encode(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static byte[] decode(String base64) {
        if (base64 == null) {
            throw new IllegalArgumentException("no key");
        }
        return Base64.getDecoder().decode(base64.strip());
    }

    private static KeyFactory factory() throws GeneralSecurityException {
        return KeyFactory.getInstance(TicketFormat.ALGORITHM);
    }
}
