package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HubDestinationTest {

    @Test
    void everyEntryHasItsOwnSlotInsideTheMenu() {
        Set<Integer> slots = new HashSet<>();
        for (HubDestination destination : HubDestination.values()) {
            assertTrue(destination.slot() >= 0 && destination.slot() < HubDestination.MENU_SIZE, destination.name());
            assertTrue(slots.add(destination.slot()), "two entries in slot " + destination.slot());
        }
    }

    @Test
    void menuSizeIsWholeRows() {
        assertEquals(0, HubDestination.MENU_SIZE % 9);
    }

    @Test
    void fourMinigamesFromThePlan() {
        long minigames = Arrays.stream(HubDestination.values())
                .filter(destination -> destination.point() == HubPoint.MINIGRY)
                .count();
        assertEquals(4, minigames);
    }

    @Test
    void everyDistrictCanBeReached() {
        Set<HubPoint> reachable = new HashSet<>();
        for (HubDestination destination : HubDestination.values()) {
            reachable.add(destination.point());
            assertFalse(destination.description().isEmpty(), destination.name());
        }
        assertEquals(Set.of(HubPoint.values()), reachable);
    }

    @Test
    void iconsAreUpperCaseMaterialNames() {
        for (HubDestination destination : HubDestination.values()) {
            assertTrue(destination.icon().matches("[A-Z_]+"), destination.icon());
        }
    }

    @Test
    void pointsAreFoundByCommandName() {
        assertEquals(Optional.of(HubPoint.ALEJA), HubPoint.byId("aleja"));
        assertEquals(Optional.empty(), HubPoint.byId("ALEJA"));
        assertEquals(Optional.empty(), HubPoint.byId("nieznany"));
    }

    @Test
    void minigamesAreFoundByTheirGameId() {
        assertEquals(Optional.of(HubDestination.SPLEEF), HubDestination.byGameId("spleef"));
        assertEquals(Optional.of(HubDestination.TNT_RUN), HubDestination.byGameId("tntrun"));
        assertEquals(Optional.empty(), HubDestination.byGameId("boxpvp"));
        assertEquals(Optional.empty(), HubDestination.byGameId("SPLEEF"));
    }

    @Test
    void onlyMinigamesHaveUniqueGameIds() {
        Set<String> ids = new HashSet<>();
        for (HubDestination destination : HubDestination.values()) {
            if (destination.point() == HubPoint.MINIGRY) {
                assertTrue(destination.gameId().isPresent(), destination.name());
                assertTrue(ids.add(destination.gameId().orElseThrow()), destination.name());
            } else {
                assertTrue(destination.gameId().isEmpty(), destination.name());
            }
        }
    }
}
