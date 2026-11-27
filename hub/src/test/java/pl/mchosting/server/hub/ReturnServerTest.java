package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReturnServerTest {

    @Test
    void nameIsTrimmed() {
        ReturnServer server = new ReturnServer("  Ekipa  ", "ekipa.mchost.pl", 25565);

        assertEquals("Ekipa", server.name());
    }

    @Test
    void longNameIsShortenedWithEllipsis() {
        ReturnServer server = new ReturnServer("a".repeat(ReturnServer.NAME_MAX + 1), "ekipa.mchost.pl", 25565);

        assertEquals(ReturnServer.NAME_MAX, server.name().length());
        assertEquals('…', server.name().charAt(ReturnServer.NAME_MAX - 1));
    }

    @Test
    void emptyNameUsesHost() {
        ReturnServer server = new ReturnServer("   ", "ekipa.mchost.pl", 25565);

        assertEquals("ekipa.mchost.pl", server.name());
    }
}
