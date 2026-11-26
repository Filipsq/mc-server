package pl.mchosting.server.api;

/**
 * Mode ids and the statistics every mode should record the same way, so the profile menu and
 * rankings can show them without knowing the mode.
 */
public final class Stats {

    public static final String SPLEEF = "spleef";
    public static final String TNT_RUN = "tntrun";
    public static final String PARKOUR = "parkour";
    public static final String DUELS = "duels";

    private Stats() {}

    /** Matches the player took part in. */
    public static StatKey gamesPlayed(String mode) {
        return StatKey.counter(mode, "games");
    }

    /** Matches the player won. */
    public static StatKey wins(String mode) {
        return StatKey.counter(mode, "wins");
    }

    public static StatKey kills(String mode) {
        return StatKey.counter(mode, "kills");
    }

    public static StatKey deaths(String mode) {
        return StatKey.counter(mode, "deaths");
    }

    /** Most wins in a row. */
    public static StatKey bestWinStreak(String mode) {
        return StatKey.highest(mode, "best_streak");
    }

    /** Best time on one parkour course, in milliseconds. */
    public static StatKey bestTimeMillis(String course) {
        return StatKey.lowest(PARKOUR, "time_" + course);
    }
}
