package pl.mchosting.server.hub;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks the panel about linked servers ({@code GET <panel>/api/link/servers/<id>}), off the main
 * thread, and remembers answers for a while so a busy server's players don't each cost a request.
 */
final class ReturnDirectory {

    private static final Duration CACHE_FOR = Duration.ofMinutes(10);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CACHED = 5000;

    private record Cached(CompletableFuture<Optional<ReturnEntry>> entry, Instant until) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final String panelUrl;
    private final AllowedHosts allowed;
    private final Clock clock;
    private final Logger logger;

    ReturnDirectory(String panelUrl, AllowedHosts allowed, Clock clock, Logger logger) {
        this.panelUrl = panelUrl.endsWith("/") ? panelUrl.substring(0, panelUrl.length() - 1) : panelUrl;
        this.allowed = allowed;
        this.clock = clock;
        this.logger = logger;
    }

    /** Never completes exceptionally: a panel that can't be reached means "no way back" for now. */
    CompletableFuture<Optional<ReturnEntry>> lookup(String serverId) {
        Instant now = clock.instant();
        Cached cached = cache.get(serverId);
        if (cached != null && now.isBefore(cached.until())) {
            return cached.entry();
        }
        if (cache.size() > MAX_CACHED) {
            cache.values().removeIf(c -> !now.isBefore(c.until()));
        }
        CompletableFuture<Optional<ReturnEntry>> entry = fetch(serverId);
        cache.put(serverId, new Cached(entry, now.plus(CACHE_FOR)));
        // A failed request is not remembered, so the next player tries again.
        entry.thenAccept(found -> {
            if (found.isEmpty()) {
                cache.remove(serverId);
            }
        });
        return entry;
    }

    private CompletableFuture<Optional<ReturnEntry>> fetch(String serverId) {
        URI uri = URI.create(panelUrl + "/api/link/servers/" + URLEncoder.encode(serverId, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200
                        ? ReturnEntry.parse(response.body(), allowed)
                        : Optional.<ReturnEntry>empty())
                .exceptionally(e -> {
                    logger.log(Level.WARNING, "Could not ask the panel about server " + serverId, e);
                    return Optional.empty();
                });
    }
}
