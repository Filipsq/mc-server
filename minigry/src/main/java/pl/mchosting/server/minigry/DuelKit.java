package pl.mchosting.server.minigry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The five Duels kits. Both players always get the same kit, so nothing can be bought that helps
 * in a fight. The items themselves are in {@link DuelKits}; this enum holds the rules.
 */
enum DuelKit {
    MIECZ(
            "miecz",
            "Miecz",
            Duration.ofMinutes(5),
            false,
            true,
            List.of("Diamentowy miecz i zbroja.", "Klasyczna walka wręcz.")),
    LUK(
            "luk",
            "Łuk",
            Duration.ofMinutes(5),
            false,
            true,
            List.of("Łuk z nieskończonymi strzałami.", "Celność wygrywa.")),
    MACE(
            "mace",
            "Mace",
            Duration.ofMinutes(5),
            false,
            true,
            List.of("Buława i ładunki wiatru.", "Wyskocz i uderz z góry.")),
    NETHERYT(
            "netheryt",
            "Netheryt",
            Duration.ofMinutes(6),
            false,
            true,
            List.of("Pełny netheryt z zaklęciami.", "Złote jabłka i perły.")),
    UHC(
            "uhc",
            "UHC",
            Duration.ofMinutes(10),
            true,
            false,
            List.of("Bez naturalnej regeneracji.", "Budowanie, wiadra wody i lawy."));

    private final String id;
    private final String displayName;
    private final Duration timeLimit;
    private final boolean building;
    private final boolean naturalRegeneration;
    private final List<String> description;

    DuelKit(
            String id,
            String displayName,
            Duration timeLimit,
            boolean building,
            boolean naturalRegeneration,
            List<String> description) {
        this.id = id;
        this.displayName = displayName;
        this.timeLimit = timeLimit;
        this.building = building;
        this.naturalRegeneration = naturalRegeneration;
        this.description = description;
    }

    /** Id used in commands and stats, for example {@code netheryt}. */
    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    /** A duel still running after this long is a draw. */
    Duration timeLimit() {
        return timeLimit;
    }

    /** Whether players may place blocks and empty buckets inside the arena. */
    boolean allowsBuilding() {
        return building;
    }

    /** Whether health comes back by itself (off in UHC: only golden apples heal). */
    boolean naturalRegeneration() {
        return naturalRegeneration;
    }

    /** Lines shown in the kit menu. */
    List<String> description() {
        return description;
    }

    static Optional<DuelKit> byId(String id) {
        for (DuelKit kit : values()) {
            if (kit.id.equals(id)) {
                return Optional.of(kit);
            }
        }
        return Optional.empty();
    }
}
