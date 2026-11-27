package pl.mchosting.server.minigry;

/** Ranking tiers shown next to ranked points, so players see their progress at a glance. */
enum DuelRank {
    BRAZ("Brąz", "<#cd7f32>", 0),
    SREBRO("Srebro", "<gray>", 900),
    ZLOTO("Złoto", "<gold>", 1100),
    DIAMENT("Diament", "<aqua>", 1300),
    MISTRZ("Mistrz", "<light_purple>", 1500);

    private final String displayName;
    private final String color;
    private final int from;

    DuelRank(String displayName, String color, int from) {
        this.displayName = displayName;
        this.color = color;
        this.from = from;
    }

    String displayName() {
        return displayName;
    }

    /** MiniMessage colour tag of the tier. */
    String color() {
        return color;
    }

    /** Lowest points of this tier. */
    int from() {
        return from;
    }

    static DuelRank of(int points) {
        DuelRank rank = BRAZ;
        for (DuelRank candidate : values()) {
            if (points >= candidate.from) {
                rank = candidate;
            }
        }
        return rank;
    }
}
