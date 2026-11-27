package pl.mchosting.server.hub;

import java.util.List;
import java.util.Optional;
import pl.mchosting.server.api.Stats;

/**
 * What the compass menu offers. A minigame with a ready arena joins its queue; everything else
 * (and minigames that are not running yet) takes the player to its place in the valley.
 */
enum HubDestination {
    SPLEEF(
            Stats.SPLEEF,
            10,
            "Spleef",
            "DIAMOND_SHOVEL",
            HubPoint.MINIGRY,
            List.of("Niszcz śnieg pod przeciwnikami.", "Ostatni na planszy wygrywa.")),
    TNT_RUN(
            Stats.TNT_RUN,
            11,
            "TNT Run",
            "TNT",
            HubPoint.MINIGRY,
            List.of("Bloki znikają pod nogami.", "Ostatni wygrywa.")),
    PARKOUR(
            Stats.PARKOUR,
            12,
            "Parkour",
            "FEATHER",
            HubPoint.MINIGRY,
            List.of("Trasy na czas i rekordy.", "10 tras, od łatwych do bardzo trudnych.")),
    DUELS(
            Stats.DUELS,
            13,
            "Duels",
            "IRON_SWORD",
            HubPoint.MINIGRY,
            List.of("Walka 1 na 1 z gotowym zestawem.", "Mecze rankingowe.")),
    BOXPVP(
            null,
            15,
            "BoxPvP",
            "NETHERITE_PICKAXE",
            HubPoint.TRYBY,
            List.of("Kop, ulepszaj sprzęt i walcz.", "Najpopularniejszy tryb w Polsce.")),
    LIFESTEAL(
            null,
            16,
            "LifeSteal SMP",
            "RED_DYE",
            HubPoint.TRYBY,
            List.of("Survival, w którym zabierasz serca.", "Sezony po 6–8 tygodni.")),
    ALEJA(
            null,
            21,
            "Aleja serwerów",
            "OAK_SIGN",
            HubPoint.ALEJA,
            List.of("Portale na serwery graczy", "z mchosting.pl.")),
    AMFITEATR(
            null,
            22,
            "Amfiteatr",
            "JUKEBOX",
            HubPoint.AMFITEATR,
            List.of("Eventy na żywo, turnieje", "i ogłoszenia wyników.")),
    SPAWN(null, 23, "Spawn", "BEACON", HubPoint.SPAWN, List.of("Powrót na środek doliny."));

    /** Rows of the compass menu. */
    static final int MENU_SIZE = 27;

    private final String gameId;
    private final int slot;
    private final String displayName;
    private final String icon;
    private final HubPoint point;
    private final List<String> description;

    HubDestination(String gameId, int slot, String displayName, String icon, HubPoint point, List<String> description) {
        this.gameId = gameId;
        this.slot = slot;
        this.displayName = displayName;
        this.icon = icon;
        this.point = point;
        this.description = description;
    }

    /** Minigame id for the queue (like {@link Stats#SPLEEF}), empty for modes and places. */
    Optional<String> gameId() {
        return Optional.ofNullable(gameId);
    }

    /** Destination of a minigame by its id. */
    static Optional<HubDestination> byGameId(String id) {
        for (HubDestination destination : values()) {
            if (id.equals(destination.gameId)) {
                return Optional.of(destination);
            }
        }
        return Optional.empty();
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
