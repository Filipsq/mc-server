package pl.mchosting.server.minigry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** MiniMessage helpers. Player names and typed text must always come in as {@code Placeholder.unparsed}. */
final class MiniText {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private MiniText() {}

    static Component text(String miniMessage, TagResolver... resolvers) {
        return MINI.deserialize(miniMessage, resolvers);
    }

    static Component item(String miniMessage, TagResolver... resolvers) {
        return MINI.deserialize("<!italic>" + miniMessage, resolvers);
    }

    /** "1:05" for 65 seconds. */
    static String clock(long seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
