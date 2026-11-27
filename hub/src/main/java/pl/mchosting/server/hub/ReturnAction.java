package pl.mchosting.server.hub;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** Sends a player back to the linked server they came from ({@code /wroc} and the door item). */
final class ReturnAction {

    private ReturnAction() {}

    static void send(Player player, ReturnListener returns) {
        returns.target(player.getUniqueId())
                .ifPresentOrElse(
                        server -> {
                            // Used up: the next server the player visits must give them a new one.
                            player.storeCookie(ReturnListener.COOKIE, new byte[0]);
                            player.sendMessage(HubText.text(
                                    "<green>Wracasz na <white><server></white>...",
                                    Placeholder.unparsed("server", server.name())));
                            player.transfer(server.host(), server.port());
                        },
                        () -> player.sendMessage(HubText.text(
                                "<gray>/wroc działa po przejściu do huba komendą /hub z serwera z mchosting.pl.")));
    }
}
