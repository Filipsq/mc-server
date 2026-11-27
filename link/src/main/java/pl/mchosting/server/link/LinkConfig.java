package pl.mchosting.server.link;

import java.security.PrivateKey;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import pl.mchosting.server.link.ticket.LinkKeys;

/**
 * Settings from config.yml, checked. Kept apart from Bukkit so the rules are testable.
 *
 * @param signing empty when the panel gave no id or key: /hub still works, without a way back
 */
record LinkConfig(String hubHost, int hubPort, Optional<Signing> signing, Duration cooldown) {

    /** Who this server is for the hub, and the key it signs return tickets with. */
    record Signing(String serverId, PrivateKey key) {}

    static final String DEFAULT_HUB_HOST = "hub.mchosting.pl";
    static final int DEFAULT_PORT = 25565;
    static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(3);

    /** A plain hostname: letters, digits, dots and dashes. No ports, paths or spaces. */
    private static final Pattern HOST =
            Pattern.compile("(?i)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");

    private static final Pattern SERVER_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /**
     * Builds the settings from raw config values, falling back to defaults for anything invalid so a
     * typo never stops /hub from working. {@code problems} gets a line for each value it ignored.
     */
    static LinkConfig from(
            String hubHost,
            int hubPort,
            String serverId,
            String privateKey,
            int cooldownSeconds,
            Consumer<String> problems) {
        String host = hubHost == null ? "" : hubHost.strip();
        if (host.length() > 253 || !HOST.matcher(host).matches()) {
            problems.accept("hub.host is not a hostname, using " + DEFAULT_HUB_HOST);
            host = DEFAULT_HUB_HOST;
        }
        int port = hubPort;
        if (port < 1 || port > 65535) {
            problems.accept("hub.port must be 1-65535, using " + DEFAULT_PORT);
            port = DEFAULT_PORT;
        }
        Duration cooldown = cooldownSeconds < 0 ? DEFAULT_COOLDOWN : Duration.ofSeconds(cooldownSeconds);
        return new LinkConfig(host, port, signing(serverId, privateKey, problems), cooldown);
    }

    private static Optional<Signing> signing(String serverId, String privateKey, Consumer<String> problems) {
        String id = serverId == null ? "" : serverId.strip();
        String key = privateKey == null ? "" : privateKey.strip();
        if (id.isEmpty() && key.isEmpty()) {
            problems.accept("no server-id and private-key: the hub will not offer a way back here");
            return Optional.empty();
        }
        if (!SERVER_ID.matcher(id).matches()) {
            problems.accept("server-id is invalid: the hub will not offer a way back here");
            return Optional.empty();
        }
        Optional<PrivateKey> parsed = LinkKeys.privateKey(key);
        if (parsed.isEmpty()) {
            problems.accept("private-key is not an Ed25519 key: the hub will not offer a way back here");
            return Optional.empty();
        }
        return Optional.of(new Signing(id, parsed.get()));
    }
}
