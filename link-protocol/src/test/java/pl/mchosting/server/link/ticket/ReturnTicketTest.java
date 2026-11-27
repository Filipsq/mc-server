package pl.mchosting.server.link.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReturnTicketTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final String NODE_PUBLIC_KEY = "MCowBQYDK2VwAyEASER8/yYsGuydsmzOfkn/mkaUU2sYrmM44l+AyEqdJHY=";
    private static final String NODE_PRIVATE_KEY = "MC4CAQAwBQYDK2VwBCIEIO57VrdB3yEr2dpub/1clka5hG44PyIzM4fULhm3pFzb";

    @Test
    void signedTicketParsesAndVerifies() {
        KeyPair keys = LinkKeys.generate();
        ReturnTicket expected = new ReturnTicket("survival", "Fiq", NOW);

        SignedTicket ticket = parse(expected.sign(keys.getPrivate()));

        assertEquals(expected, ticket.unverified());
        assertEquals(TicketCheck.Verdict.VALID, TicketCheck.check(ticket, keys.getPublic(), "Fiq", NOW));
    }

    @Test
    void playerNameIsCaseInsensitive() {
        KeyPair keys = LinkKeys.generate();
        SignedTicket ticket = parse(new ReturnTicket("survival", "Fiq", NOW).sign(keys.getPrivate()));

        assertEquals(TicketCheck.Verdict.VALID, TicketCheck.check(ticket, keys.getPublic(), "fIQ", NOW));
    }

    @Test
    void ticketSignedByAnotherKeyIsRejected() {
        KeyPair signer = LinkKeys.generate();
        SignedTicket ticket = parse(new ReturnTicket("survival", "Fiq", NOW).sign(signer.getPrivate()));

        assertEquals(
                TicketCheck.Verdict.BAD_SIGNATURE,
                TicketCheck.check(ticket, LinkKeys.generate().getPublic(), "Fiq", NOW));
    }

    @Test
    void changedServerIdInvalidatesSignature() {
        KeyPair keys = LinkKeys.generate();
        ReturnTicket original = new ReturnTicket("survival", "Fiq", NOW);
        String signed = new String(original.sign(keys.getPrivate()), StandardCharsets.US_ASCII);
        String signature = signed.substring(signed.lastIndexOf('.'));
        String changed =
                TicketFormat.PREFIX + TicketFormat.encode(new ReturnTicket("inne", "Fiq", NOW).payload()) + signature;

        SignedTicket ticket = parse(changed.getBytes(StandardCharsets.US_ASCII));

        assertEquals(TicketCheck.Verdict.BAD_SIGNATURE, TicketCheck.check(ticket, keys.getPublic(), "Fiq", NOW));
    }

    @Test
    void ticketForAnotherPlayerIsRejected() {
        KeyPair keys = LinkKeys.generate();
        SignedTicket ticket = parse(new ReturnTicket("survival", "Fiq", NOW).sign(keys.getPrivate()));

        assertEquals(TicketCheck.Verdict.OTHER_PLAYER, TicketCheck.check(ticket, keys.getPublic(), "Other", NOW));
    }

    @Test
    void ticketAtMaximumAgeIsValidButOlderOneExpires() {
        KeyPair keys = LinkKeys.generate();
        ReturnTicket ticket = new ReturnTicket("survival", "Fiq", NOW.minus(TicketCheck.MAX_AGE));
        SignedTicket parsed = parse(ticket.sign(keys.getPrivate()));

        assertEquals(TicketCheck.Verdict.VALID, TicketCheck.check(parsed, keys.getPublic(), "Fiq", NOW));
        assertEquals(
                TicketCheck.Verdict.EXPIRED, TicketCheck.check(parsed, keys.getPublic(), "Fiq", NOW.plusSeconds(1)));
    }

    @Test
    void ticketAtClockSkewIsValidButFurtherFutureOneIsRejected() {
        KeyPair keys = LinkKeys.generate();
        ReturnTicket ticket = new ReturnTicket("survival", "Fiq", NOW.plus(TicketCheck.CLOCK_SKEW));
        SignedTicket parsed = parse(ticket.sign(keys.getPrivate()));

        assertEquals(TicketCheck.Verdict.VALID, TicketCheck.check(parsed, keys.getPublic(), "Fiq", NOW));
        assertEquals(
                TicketCheck.Verdict.FROM_FUTURE,
                TicketCheck.check(parsed, keys.getPublic(), "Fiq", NOW.minusSeconds(1)));
    }

    @Test
    void malformedCookiesAreIgnored() {
        byte[][] malformed = {
            null,
            new byte[0],
            new byte[2000],
            bytes("hello"),
            bytes("mchl1."),
            bytes("mchl1.not-base64!.AA"),
            bytes("mchl1." + TicketFormat.encode("server\nFiq") + ".AA"),
            bytes("mchl1." + TicketFormat.encode("server\nFiq\nnot-a-number") + ".AA"),
            bytes("mchl1." + TicketFormat.encode("server id\nFiq\n0") + ".AA"),
            bytes("mchl2." + TicketFormat.encode("server\nFiq\n0") + ".AA")
        };

        for (byte[] cookie : malformed) {
            assertTrue(SignedTicket.parse(cookie).isEmpty());
        }
    }

    @Test
    void shortSignatureIsBadSignatureInsteadOfAnException() {
        KeyPair keys = LinkKeys.generate();
        String cookie = "mchl1." + TicketFormat.encode("server\nFiq\n0") + ".AAAA";

        SignedTicket ticket = parse(bytes(cookie));

        assertEquals(TicketCheck.Verdict.BAD_SIGNATURE, TicketCheck.check(ticket, keys.getPublic(), "Fiq", NOW));
    }

    @Test
    void longestTicketFitsCookieLimit() {
        KeyPair keys = LinkKeys.generate();
        ReturnTicket ticket = new ReturnTicket("a".repeat(64), "b".repeat(32), NOW);

        assertTrue(ticket.sign(keys.getPrivate()).length < TicketFormat.MAX_BYTES);
    }

    @Test
    void returnTicketRejectsInvalidNames() {
        assertThrows(IllegalArgumentException.class, () -> new ReturnTicket("server\nother", "Fiq", NOW));
        assertThrows(IllegalArgumentException.class, () -> new ReturnTicket("", "Fiq", NOW));
        assertThrows(IllegalArgumentException.class, () -> new ReturnTicket("server", "Fiq<", NOW));
    }

    @Test
    void encodedKeysStillSignAndVerify() {
        KeyPair keys = LinkKeys.generate();
        SignedTicket ticket = parse(new ReturnTicket("survival", "Fiq", NOW).sign(keys.getPrivate()));

        assertEquals(
                TicketCheck.Verdict.VALID,
                TicketCheck.check(
                        ticket,
                        LinkKeys.publicKey(LinkKeys.encode(keys.getPublic())).orElseThrow(),
                        "Fiq",
                        NOW));
        assertTrue(LinkKeys.privateKey(LinkKeys.encode(keys.getPrivate())).isPresent());
    }

    @Test
    void nodePanelKeysSignAndVerify() {
        SignedTicket ticket = parse(new ReturnTicket("survival", "Fiq", NOW)
                .sign(LinkKeys.privateKey(NODE_PRIVATE_KEY).orElseThrow()));

        assertEquals(
                TicketCheck.Verdict.VALID,
                TicketCheck.check(ticket, LinkKeys.publicKey(NODE_PUBLIC_KEY).orElseThrow(), "Fiq", NOW));
    }

    @Test
    void invalidKeysAreRejected() {
        assertTrue(LinkKeys.publicKey(null).isEmpty());
        assertTrue(LinkKeys.publicKey("").isEmpty());
        assertTrue(LinkKeys.publicKey("not base64!").isEmpty());
        assertTrue(LinkKeys.privateKey("AAAA").isEmpty());
        assertTrue(LinkKeys.privateKey(NODE_PUBLIC_KEY).isEmpty());
    }

    private static SignedTicket parse(byte[] cookie) {
        return SignedTicket.parse(cookie).orElseThrow();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
