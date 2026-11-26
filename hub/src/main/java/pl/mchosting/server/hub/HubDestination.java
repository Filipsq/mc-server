package pl.mchosting.server.hub;

import java.util.List;

/**
 * What the compass menu offers. Modes are not running yet, so each entry takes the player to its
 * gate in the valley; when a mode is ready its entry will join the queue instead.
 */
enum HubDestination {
    SPLEEF(
            10,
            "Spleef",
            "DIAMOND_SHOVEL",
            HubPoint.MINIGRY,
            List.of("Niszcz śnieg pod przeciwnikami.", "Ostatni na planszy wygrywa.")),
    TNT_RUN(11, "TNT Run", "TNT", HubPoint.MINIGRY, List.of("Bloki znikają pod nogami.", "Ostatni wygrywa.")),
    PARKOUR(
            12,
            "Parkour",
            "FEATHER",
            HubPoint.MINIGRY,
            List.of("Trasy na czas i rekordy.", "10 tras, od łatwych do bardzo trudnych.")),
    DUELS(
            13,
            "Duels",
            "IRON_SWORD",
            HubPoint.MINIGRY,
            List.of("Walka 1 na 1 z gotowym zestawem.", "Mecze rankingowe.")),
    BOXPVP(
            15,
            "BoxPvP",
            "NETHERITE_PICKAXE",
            HubPoint.TRYBY,
            List.of("Kop, ulepszaj sprzęt i walcz.", "Najpopularniejszy tryb w Polsce.")),
    LIFESTEAL(
            16,
            "LifeSteal SMP",
            "RED_DYE",
            HubPoint.TRYBY,
            List.of("Survival, w którym zabierasz serca.", "Sezony po 6–8 tygodni.")),
    ALEJA(21, "Aleja serwerów", "OAK_SIGN", HubPoint.ALEJA, List.of("Portale na serwery graczy", "z mchosting.pl.")),
    AMFITEATR(
            22,
            "Amfiteatr",
            "JUKEBOX",
            HubPoint.AMFITEATR,
            List.of("Eventy na żywo, turnieje", "i ogłoszenia wyników.")),
    SPAWN(23, "Spawn", "BEACON", HubPoint.SPAWN, List.of("Powrót na środek doliny."));

    /** Rows of the compass menu. */
    static final int MENU_SIZE = 27;

    private final int slot;
    private final String displayName;
    private final String icon;
    private final HubPoint point;
    private final List<String> description;

    HubDestination(int slot, String displayName, String icon, HubPoint point, List<String> description) {
        this.slot = slot;
        this.displayName = displayName;
        this.icon = icon;
        this.point = point;
        this.description = description;
    }

    int slot() {
        return slot;
    }

    String displayName() {
        return displayName;
    }

    /** Bukkit material name, kept as text so this enum stays testable without a server. */
    String icon() {
        return icon;
    }

    HubPoint point() {
        return point;
    }

    List<String> description() {
        return description;
    }

    /** True for game modes (they will get a queue), false for places in the valley. */
    boolean isMode() {
        return point == HubPoint.MINIGRY || point == HubPoint.TRYBY;
    }
}
