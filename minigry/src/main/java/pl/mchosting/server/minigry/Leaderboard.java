package pl.mchosting.server.minigry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Best times on one course: one entry per player, fastest first, at most {@link #SIZE}. */
final class Leaderboard {

    static final int SIZE = 10;

    /**
     * One time on the board.
     *
     * @param playerId who ran it
     * @param name the player's name when they set it, for showing the board
     * @param millis the time
     * @param setAt when, in epoch milliseconds (an earlier equal time stays ahead)
     */
    record Entry(UUID playerId, String name, long millis, long setAt) {

        Entry {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(name, "name");
            if (millis <= 0) {
                throw new IllegalArgumentException("time must be positive");
            }
        }
    }

    private static final Comparator<Entry> ORDER =
            Comparator.comparingLong(Entry::millis).thenComparingLong(Entry::setAt);

    private final List<Entry> entries = new ArrayList<>();

    Leaderboard(List<Entry> entries) {
        entries.forEach(this::submit);
    }

    /**
     * Adds a time if it makes the board and beats the player's own entry.
     *
     * @return the place it got (1 = course record), or 0 if it did not change the board
     */
    int submit(Entry entry) {
        for (Entry existing : entries) {
            if (existing.playerId().equals(entry.playerId()) && ORDER.compare(existing, entry) <= 0) {
                return 0;
            }
        }
        entries.removeIf(existing -> existing.playerId().equals(entry.playerId()));
        entries.add(entry);
        entries.sort(ORDER);
        int place = entries.indexOf(entry) + 1;
        while (entries.size() > SIZE) {
            entries.removeLast();
        }
        return place <= SIZE ? place : 0;
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Whether {@code millis} would get onto the board (players are told before they finish). */
    boolean wouldPlace(long millis) {
        return entries.size() < SIZE || millis < entries.getLast().millis();
    }
}
