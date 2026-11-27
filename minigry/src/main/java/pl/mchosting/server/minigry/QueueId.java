package pl.mchosting.server.minigry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One queue: a game, and for Duels also the kit and whether the duel is ranked. Spleef and TNT
 * Run have one queue each; Duels has two per kit (normal and ranked).
 *
 * @param type the game
 * @param kit the Duels kit, null for other games
 * @param ranked whether the duel changes the players' ranking points
 */
record QueueId(GameType type, DuelKit kit, boolean ranked) {

    /** Every queue, in the order they are checked for new matches. */
    static final List<QueueId> ALL = all();

    QueueId {
        Objects.requireNonNull(type, "type");
        if ((type == GameType.DUELS) != (kit != null)) {
            throw new IllegalArgumentException("a kit is needed for Duels and only for Duels");
        }
        if (ranked && kit == null) {
            throw new IllegalArgumentException("only duels can be ranked");
        }
    }

    static QueueId of(GameType type) {
        return new QueueId(type, null, false);
    }

    static QueueId duel(DuelKit kit, boolean ranked) {
        return new QueueId(GameType.DUELS, kit, ranked);
    }

    /** What the player sees, for example "Duels: Łuk (rankingowy)". */
    String displayName() {
        if (kit == null) {
            return type.displayName();
        }
        return type.displayName() + ": " + kit.displayName() + (ranked ? " (rankingowy)" : "");
    }

    /** The command that joins this queue again, for the "play again" button. */
    String command() {
        if (kit == null) {
            return "/graj " + type.id();
        }
        return "/graj " + type.id() + " " + kit.id() + (ranked ? " ranking" : "");
    }

    /** A match still running after this long ends without a winner. */
    Duration timeLimit() {
        return kit == null ? type.timeLimit() : kit.timeLimit();
    }

    private static List<QueueId> all() {
        List<QueueId> all = new ArrayList<>();
        for (GameType type : GameType.values()) {
            if (type == GameType.DUELS) {
                for (DuelKit kit : DuelKit.values()) {
                    all.add(duel(kit, false));
                    all.add(duel(kit, true));
                }
            } else {
                all.add(of(type));
            }
        }
        return List.copyOf(all);
    }
}
