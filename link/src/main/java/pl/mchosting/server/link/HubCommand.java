package pl.mchosting.server.link;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import pl.mchosting.server.link.ticket.ReturnTicket;

/** {@code /hub}: stores the signed return ticket in the player's game, then transfers them. */
final class HubCommand {

    /** The same key on every server and in the hub, so it can't be a plugin's own namespace. */
    static final NamespacedKey COOKIE = Objects.requireNonNull(
            NamespacedKey.fromString(ReturnTicket.COOKIE_NAMESPACE + ":" + ReturnTicket.COOKIE_KEY));

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final LinkConfig config;
    private final TicketIssuer issuer;
    private final Cooldown cooldown;

    HubCommand(LinkConfig config, TicketIssuer issuer, Cooldown cooldown) {
        this.config = config;
        this.issuer = issuer;
        this.cooldown = cooldown;
    }

    LiteralCommandNode<CommandSourceStack> node(String name) {
        return Commands.literal(name)
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    send((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void send(Player player) {
        if (!cooldown.tryUse(player.getUniqueId())) {
            player.sendMessage(MINI.deserialize(
                    "<red>Poczekaj <seconds> s przed kolejnym /hub.",
                    Placeholder.unparsed("seconds", Long.toString(cooldown.secondsLeft(player.getUniqueId())))));
            return;
        }
        // Without a ticket the hub just has no "back" button; an old one from earlier is cleared
        // so the hub never offers a way back to a server the player didn't come from.
        player.storeCookie(COOKIE, issuer.issue(player.getName()).orElse(new byte[0]));
        player.sendMessage(MINI.deserialize("<green>Przenoszę Cię do huba <white>mchosting.pl</white>..."));
        player.transfer(config.hubHost(), config.hubPort());
    }
}
