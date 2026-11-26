package pl.mchosting.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.api.LevelCurve;

class SidebarContentTest {

    @Test
    void showsAccountAndOnlineCount() {
        List<String> lines = SidebarContent.lines(LevelCurve.progress(350), 120, 3, 42);
        String all = String.join("\n", lines);
        assertTrue(all.contains("Poziom: <white>3"), all);
        assertTrue(all.contains("<white>50</white><gray>/300"), all);
        assertTrue(all.contains("Monety: <gold>120"), all);
        assertTrue(all.contains("Seria nagród: <white>3 dni"), all);
        assertTrue(all.contains("Online: <green>42"), all);
    }

    @Test
    void sidebarFitsMinecraftLimit() {
        assertTrue(SidebarContent.lines(LevelCurve.progress(0), 0, 0, 0).size() <= 15);
    }

    @Test
    void maxLevelHasNoXpTarget() {
        String all = String.join(
                "\n", SidebarContent.lines(LevelCurve.progress(LevelCurve.totalXpFor(LevelCurve.MAX_LEVEL)), 0, 0, 1));
        assertTrue(all.contains("MAX"), all);
    }

    @Test
    void polishDays() {
        assertEquals("dzień", SidebarContent.days(1));
        assertEquals("dni", SidebarContent.days(2));
        assertEquals("dni", SidebarContent.days(5));
        assertEquals("dni", SidebarContent.days(0));
    }
}
