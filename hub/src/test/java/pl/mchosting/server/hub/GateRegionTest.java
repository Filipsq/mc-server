package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GateRegionTest {

    @Test
    void betweenOrdersCorners() {
        GateRegion gate = GateRegion.between("world", 5, 70, -3, -5, 60, 3);

        assertEquals(-5, gate.minX());
        assertEquals(60, gate.minY());
        assertEquals(-3, gate.minZ());
        assertEquals(5, gate.maxX());
        assertEquals(70, gate.maxY());
        assertEquals(3, gate.maxZ());
    }

    @Test
    void containsCornersAndCenterButNotOutsideOrOtherWorld() {
        GateRegion gate = GateRegion.between("world", -5, 60, -3, 5, 70, 3);

        assertTrue(gate.contains("world", -5, 60, -3));
        assertTrue(gate.contains("world", 5, 70, 3));
        assertTrue(gate.contains("world", 0, 65, 0));
        assertFalse(gate.contains("world", -6, 65, 0));
        assertFalse(gate.contains("world", 6, 65, 0));
        assertFalse(gate.contains("world", 0, 59, 0));
        assertFalse(gate.contains("world", 0, 71, 0));
        assertFalse(gate.contains("world", 0, 65, -4));
        assertFalse(gate.contains("world", 0, 65, 4));
        assertFalse(gate.contains("world_nether", 0, 65, 0));
    }

    @Test
    void singleBlockGateContainsOnlyThatBlock() {
        GateRegion gate = GateRegion.between("w", 1, 2, 3, 1, 2, 3);

        assertTrue(gate.contains("w", 1, 2, 3));
        assertFalse(gate.contains("w", 2, 2, 3));
    }

    @Test
    void constructorRejectsInvalidBoundsAndNullWorld() {
        assertThrows(IllegalArgumentException.class, () -> new GateRegion("w", 1, 0, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new GateRegion(null, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void encodedGateDecodesWithNegativeCoordinatesAndWhitespace() {
        GateRegion gate = GateRegion.between("w", -1, -2, -3, 4, 5, 6);

        assertEquals(gate, GateRegion.decode(gate.encode()));
        assertEquals(GateRegion.between("w", 1, 2, 3, 4, 5, 6), GateRegion.decode("  w 1 2 3   4 5 6 "));
    }

    @Test
    void decodeRejectsBrokenText() {
        for (String text : new String[] {"", "w 1 2 3", "w 1 2 3 4 5 6 7", "w a 2 3 4 5 6"}) {
            assertThrows(IllegalArgumentException.class, () -> GateRegion.decode(text));
        }
    }
}
