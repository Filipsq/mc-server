package pl.mchosting.server.hub;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import pl.mchosting.server.link.ticket.ReturnTicket;
import pl.mchosting.server.link.ticket.SignedTicket;
import pl.mchosting.server.link.ticket.TicketCheck;

/**
 * Reads the return ticket a customer's server (mchosting-link) left in the player's game and, once
 * it checks out, gives them the "back" item and {@code /wroc}. Everything slow (the cookie, the
 * panel, the signature) happens off the main thread.
 */
final class ReturnListener implements Listener {

    static final NamespacedKey COOKIE = Objects.requireNonNull(
            NamespacedKey.fromString(ReturnTicket.COOKIE_NAMESPACE + ":" + ReturnTicket.COOKIE_KEY));

    /** Bedrock players (Geyser) never answer a cookie request. */
    private static final Duration COOKIE_WAIT = Duration.ofSeconds(5);

    private final Plugin plugin;
    private final ReturnDirectory directory;
    private final HubItems items;
    private final Clock clock;
    private final Logger logger;
    private final Map<UUID, ReturnServer> targets = new ConcurrentHashMap<>();

    ReturnListener(Plugin plugin, ReturnDirectory directory, HubItems items, Clock clock) {
        this.plugin = plugin;
        this.directory = directory;
        this.items = items;
        this.clock = clock;
        this.logger = plugin.getLogger();
    }

    /** Where /wroc sends this player, if they came from a linked server. */
    Optional<ReturnServer> target(UUID playerId) {
        return Optional.ofNullable(targets.get(playerId));
    }

    // After HubJoinListener, which clears the inventory.
    @EventHandler(priority = EventPriority.MONITOR)
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        String name = player.getName();
        player.retrieveCookie(COOKIE)
                .orTimeout(COOKIE_WAIT.toMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(cookie -> SignedTicket.parse(cookie)
                        .map(ticket -> check(ticket, name))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .exceptionally(e -> Optional.empty())
                .thenAccept(found -> found.ifPresent(
                        server -> plugin.getServer().getScheduler().runTask(plugin, () -> offer(id, server))));
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        targets.remove(event.getPlayer().getUniqueId());
    }

    private CompletableFuture<Optional<ReturnServer>> check(SignedTicket ticket, String playerName) {
        String serverId = ticket.unverified().serverId();
        return directory
                .lookup(serverId)
                .thenApply(entry -> entry.flatMap(e -> {
                    TicketCheck.Verdict verdict = TicketCheck.check(ticket, e.key(), playerName, clock.instant());
                    if (verdict == TicketCheck.Verdict.VALID) {
                        return Optional.of(e.server());
                    }
                    if (verdict == TicketCheck.Verdict.BAD_SIGNATURE || verdict == TicketCheck.Verdict.OTHER_PLAYER) {
                        // Not an accident: someone edited a cookie or copied another player's.
                        logger.warning(
                                "Refused return ticket of " + playerName + " for server " + serverId + ": " + verdict);
                    }
                    return Optional.empty();
                }));
    }

    private void offer(UUID playerId, ReturnServer server) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        targets.put(playerId, server);
        items.giveReturnItem(player, server);
        player.sendMessage(HubText.text(
                "<gray>Wpisz <yellow>/wroc</yellow> albo użyj drzwi w ekwipunku, aby wrócić na <white><server></white>.",
                Placeholder.unparsed("server", server.name())));
    }
}
