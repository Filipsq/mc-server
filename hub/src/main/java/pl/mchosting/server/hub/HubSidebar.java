package pl.mchosting.server.hub;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import pl.mchosting.server.api.DailyReward;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;

/** Sidebar with level, coins and players online. Each player has their own board. */
final class HubSidebar {

    private static final String OBJECTIVE = "hub";

    private record Board(Objective objective, List<String> shown) {}

    private final Server server;
    private final ProfileService profiles;
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();

    HubSidebar(Server server, ProfileService profiles) {
        this.server = server;
        this.profiles = profiles;
    }

    void show(Player player) {
        Scoreboard scoreboard = server.getScoreboardManager().getNewScoreboard();
        Objective objective =
                scoreboard.registerNewObjective(OBJECTIVE, Criteria.DUMMY, HubText.text(SidebarContent.TITLE));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        player.setScoreboard(scoreboard);
        boards.put(player.getUniqueId(), new Board(objective, new ArrayList<>()));
        update(player);
    }

    void updateAll() {
        for (Player player : server.getOnlinePlayers()) {
            update(player);
        }
    }

    void remove(UUID playerId) {
        boards.remove(playerId);
    }

    private void update(Player player) {
        Board board = boards.get(player.getUniqueId());
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (board == null || profile == null) {
            return;
        }
        int streak = profile.daily().currentStreak(LocalDate.now(DailyReward.NETWORK_ZONE));
        List<String> lines = SidebarContent.lines(
                profile.levelProgress(),
                profile.coins(),
                streak,
                server.getOnlinePlayers().size());
        List<String> shown = board.shown();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i < shown.size() && shown.get(i).equals(line)) {
                continue; // unchanged lines are not sent again
            }
            Score score = board.objective().getScore("line" + i);
            score.setScore(lines.size() - i);
            score.customName(HubText.text(line));
            if (i < shown.size()) {
                shown.set(i, line);
            } else {
                shown.add(line);
            }
        }
    }
}
