package pl.mchosting.server.hub;

import java.util.List;
import pl.mchosting.server.api.LevelProgress;

/**
 * Lines of the hub sidebar, top to bottom, as MiniMessage text. Only numbers from our own data
 * go in, never text typed by players.
 */
final class SidebarContent {

    static final String TITLE = "<gradient:#3fb950:#58a6ff><bold>mchosting.pl</bold></gradient>";
    static final int BAR_WIDTH = 10;

    private SidebarContent() {}

    static List<String> lines(LevelProgress progress, long coins, int dailyStreak, int online) {
        String xp = progress.isMaxLevel()
                ? "<gold>MAX</gold>"
                : "<white>" + progress.xpIntoLevel() + "</white><gray>/" + progress.xpForLevel() + "</gray>";
        return List.of(
                "",
                "<gray>Poziom: <white>" + progress.level(),
                "<gray>XP: " + xp,
                ProgressBar.render(progress.fraction(), BAR_WIDTH),
                "",
                "<gray>Monety: <gold>" + coins,
                "<gray>Seria nagród: <white>" + dailyStreak + " " + days(dailyStreak),
                "",
                "<gray>Online: <green>" + online,
                "",
                "<yellow>mchosting.pl");
    }

    /** Polish plural of "dzień". */
    static String days(int count) {
        return count == 1 ? "dzień" : "dni";
    }
}
