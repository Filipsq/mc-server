package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatKeyTest {

    @Test
    void idJoinsModeAndName() {
        assertEquals("spleef.wins", Stats.wins(Stats.SPLEEF).id());
        assertEquals("parkour.time_las", Stats.bestTimeMillis("las").id());
    }

    @Test
    void equalByIdOnly() {
        assertEquals(StatKey.counter("duels", "x"), StatKey.highest("duels", "x"));
    }

    @Test
    void rejectsBadNames() {
        assertThrows(IllegalArgumentException.class, () -> StatKey.counter("Spleef", "wins"));
        assertThrows(IllegalArgumentException.class, () -> StatKey.counter("spleef", "wins.today"));
        assertThrows(IllegalArgumentException.class, () -> StatKey.counter("", "wins"));
    }

    @Test
    void validatesStoredIds() {
        assertTrue(StatKey.isValidId("tntrun.games"));
        assertFalse(StatKey.isValidId("tntrun"));
        assertFalse(StatKey.isValidId(".games"));
        assertFalse(StatKey.isValidId("a.b.c"));
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(
                IllegalArgumentException.class, () -> Stats.wins(Stats.SPLEEF).merge(null, -1));
    }
}
