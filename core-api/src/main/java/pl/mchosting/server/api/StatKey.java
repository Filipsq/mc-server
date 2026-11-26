package pl.mchosting.server.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Name of one statistic kept on the player account, for example {@code spleef.wins}. The id is
 * stored, the kind only says how a new value is merged with the stored one.
 *
 * <p>Two keys are equal when their ids are equal.
 */
public final class StatKey {

    /** How a recorded value changes the stored one. */
    public enum Kind {
        /** Values are added up (wins, games played, kills). */
        COUNTER,
        /** The lowest value is kept (best parkour time). */
        LOWEST,
        /** The highest value is kept (longest win streak). */
        HIGHEST
    }

    private static final Pattern PART = Pattern.compile("[a-z0-9_]{1,32}");

    private final String id;
    private final Kind kind;

    private StatKey(String mode, String name, Kind kind) {
        if (!PART.matcher(mode).matches() || !PART.matcher(name).matches()) {
            throw new IllegalArgumentException("stat parts must match " + PART + ": " + mode + "." + name);
        }
        this.id = mode + "." + name;
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public static StatKey counter(String mode, String name) {
        return new StatKey(mode, name, Kind.COUNTER);
    }

    public static StatKey lowest(String mode, String name) {
        return new StatKey(mode, name, Kind.LOWEST);
    }

    public static StatKey highest(String mode, String name) {
        return new StatKey(mode, name, Kind.HIGHEST);
    }

    /** True if {@code id} has the {@code mode.name} shape of a stat id. */
    public static boolean isValidId(String id) {
        int dot = id.indexOf('.');
        return dot > 0
                && PART.matcher(id.substring(0, dot)).matches()
                && PART.matcher(id.substring(dot + 1)).matches();
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** New stored value after recording {@code value}; {@code stored} is null if nothing was stored yet. */
    long merge(Long stored, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("stat value must not be negative");
        }
        if (stored == null) {
            return value;
        }
        return switch (kind) {
            case COUNTER -> Math.addExact(stored, value);
            case LOWEST -> Math.min(stored, value);
            case HIGHEST -> Math.max(stored, value);
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StatKey key && key.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
