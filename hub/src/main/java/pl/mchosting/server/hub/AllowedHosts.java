package pl.mchosting.server.hub;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where /wroc may send players: only addresses of mchosting.pl servers. The panel already only lists
 * those; this is a second lock in case the panel or its answer is ever wrong.
 */
final class AllowedHosts {

    private static final Pattern HOST =
            Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

    private final List<String> domains;

    /** @param domains e.g. {@code mchosting.pl}: it and every address under it are allowed */
    AllowedHosts(List<String> domains) {
        this.domains = domains.stream()
                .map(d -> d.strip().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .toList();
    }

    boolean allows(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.length() > 253 || !HOST.matcher(h).matches()) {
            return false;
        }
        return domains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d));
    }
}
