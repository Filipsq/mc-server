package pl.mchosting.server.hub;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** MiniMessage helpers. Player-typed text must always come in as {@code Placeholder.unparsed}. */
final class HubText {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private HubText() {}

    static Component text(String miniMessage, TagResolver... resolvers) {
        return MINI.deserialize(miniMessage, resolvers);
    }

    /** Item names and lore: same as {@link #text} but never in italics. */
    static Component item(String miniMessage, TagResolver... resolvers) {
        return MINI.deserialize("<!italic>" + miniMessage, resolvers);
    }

    static List<Component> lore(List<String> lines) {
        return lines.stream().map(line -> item("<gray>" + line)).toList();
    }
}
