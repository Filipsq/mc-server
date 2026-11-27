package pl.mchosting.server.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.link.ticket.LinkKeys;

class LinkConfigTest {

    private static final String KEY = LinkKeys.encode(LinkKeys.generate().getPrivate());

    @Test
    void validSettingsAreKept() {
        List<String> problems = new ArrayList<>();

        LinkConfig config = LinkConfig.from("hub.mchosting.pl", 25565, "cm3abc", KEY, 3, problems::add);

        assertEquals("hub.mchosting.pl", config.hubHost());
        assertEquals(25565, config.hubPort());
        assertEquals("cm3abc", config.signing().orElseThrow().serverId());
        assertEquals(Duration.ofSeconds(3), config.cooldown());
        assertTrue(problems.isEmpty());
    }

    @Test
    void invalidHostsUseDefault() {
        for (String host : new String[] {"zly host", "a/b", "", null, "hub.pl:25565"}) {
            List<String> problems = new ArrayList<>();

            LinkConfig config = LinkConfig.from(host, 25565, "cm3abc", KEY, 3, problems::add);

            assertEquals(LinkConfig.DEFAULT_HUB_HOST, config.hubHost());
            assertEquals(1, problems.size());
        }
    }

    @Test
    void invalidPortsUseDefault() {
        for (int port : new int[] {0, 70000}) {
            List<String> problems = new ArrayList<>();

            LinkConfig config = LinkConfig.from("hub.mchosting.pl", port, "cm3abc", KEY, 3, problems::add);

            assertEquals(LinkConfig.DEFAULT_PORT, config.hubPort());
            assertEquals(1, problems.size());
        }
    }

    @Test
    void negativeCooldownUsesDefaultAndZeroIsAllowed() {
        List<String> problems = new ArrayList<>();

        LinkConfig negative = LinkConfig.from("hub.mchosting.pl", 25565, "cm3abc", KEY, -1, problems::add);
        LinkConfig zero = LinkConfig.from("hub.mchosting.pl", 25565, "cm3abc", KEY, 0, problems::add);

        assertEquals(LinkConfig.DEFAULT_COOLDOWN, negative.cooldown());
        assertEquals(Duration.ZERO, zero.cooldown());
    }

    @Test
    void missingOrInvalidSigningIsIgnoredWithoutChangingHub() {
        for (String[] signing : new String[][] {{"", ""}, {"a b", KEY}, {"cm3abc", "AAAA"}}) {
            List<String> problems = new ArrayList<>();

            LinkConfig config = LinkConfig.from("hub.mchosting.pl", 25565, signing[0], signing[1], 3, problems::add);

            assertEquals("hub.mchosting.pl", config.hubHost());
            assertEquals(25565, config.hubPort());
            assertFalse(config.signing().isPresent());
            assertEquals(1, problems.size());
        }
    }
}
