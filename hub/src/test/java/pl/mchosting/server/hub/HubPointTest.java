package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HubPointTest {

    @Test
    void everyIdIsTheLowercaseEnumName() {
        for (HubPoint point : HubPoint.values()) {
            assertEquals(point.name().toLowerCase(Locale.ROOT), point.id());
            assertTrue(point.id().matches("[a-z]+"), point.id());
        }
    }

    @Test
    void everyPointCanBeFoundByItsId() {
        for (HubPoint point : HubPoint.values()) {
            assertSame(point, HubPoint.byId(point.id()).orElseThrow());
        }
    }

    @Test
    void emptyIdHasNoPoint() {
        assertFalse(HubPoint.byId("").isPresent());
    }

    @Test
    void displayNamesAreNonEmptyAndUnique() {
        Set<String> names = new HashSet<>();
        for (HubPoint point : HubPoint.values()) {
            assertFalse(point.displayName().isEmpty(), point.name());
            assertTrue(names.add(point.displayName()), point.displayName());
        }
    }
}
