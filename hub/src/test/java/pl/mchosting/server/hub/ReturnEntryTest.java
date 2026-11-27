package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.link.ticket.LinkKeys;

class ReturnEntryTest {

    private static final AllowedHosts ALLOWED = new AllowedHosts(List.of("mchost.pl"));
    private static final String KEY = LinkKeys.encode(LinkKeys.generate().getPublic());

    @Test
    void parsesValidPanelEntry() {
        ReturnEntry entry =
                parse("{\"name\":\"Ekipa\",\"host\":\"ekipa.mchost.pl\",\"port\":25565,\"publicKey\":\"" + KEY + "\"}");

        assertEquals("Ekipa", entry.server().name());
        assertEquals("ekipa.mchost.pl", entry.server().host());
        assertEquals(25565, entry.server().port());
        assertEquals(KEY, LinkKeys.encode(entry.key()));
    }

    @Test
    void missingPortAndNameUseDefaults() {
        ReturnEntry entry = parse("{\"host\":\"ekipa.mchost.pl\",\"publicKey\":\"" + KEY + "\"}");

        assertEquals("ekipa.mchost.pl", entry.server().name());
        assertEquals(25565, entry.server().port());
    }

    @Test
    void malformedOrUnsafePanelResponsesAreIgnored() {
        String valid = "{\"host\":\"ekipa.mchost.pl\",\"publicKey\":\"" + KEY + "\"}";
        String[] malformed = {
            valid.replace("ekipa.mchost.pl", "other.example"),
            "{\"publicKey\":\"" + KEY + "\"}",
            "{\"host\":\"ekipa.mchost.pl\"}",
            "{\"host\":\"ekipa.mchost.pl\",\"publicKey\":\"AAAA\"}",
            "{\"host\":\"ekipa.mchost.pl\",\"port\":0,\"publicKey\":\"" + KEY + "\"}",
            "{\"host\":\"ekipa.mchost.pl\",\"port\":70000,\"publicKey\":\"" + KEY + "\"}",
            "{\"host\":\"ekipa.mchost.pl\",\"port\":\"abc\",\"publicKey\":\"" + KEY + "\"}",
            "[]",
            "\"tekst\"",
            "{",
            "",
            "{\"host\":12,\"publicKey\":\"" + KEY + "\"}"
        };

        for (String json : malformed) {
            assertTrue(ReturnEntry.parse(json, ALLOWED).isEmpty(), json);
        }
    }

    private static ReturnEntry parse(String json) {
        return ReturnEntry.parse(json, ALLOWED).orElseThrow();
    }
}
