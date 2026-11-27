package pl.mchosting.server.minigry;

/** Parkour times as players read them. */
final class RaceTime {

    private RaceTime() {}

    /** "1:05.250" for 65.25 seconds, "0:09.800" for 9.8 seconds. */
    static String format(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("negative time");
        }
        long minutes = millis / 60_000;
        long seconds = millis / 1000 % 60;
        return "%d:%02d.%03d".formatted(minutes, seconds, millis % 1000);
    }

    /** Shorter "1:05.2" for the action bar, updated many times a second. */
    static String formatShort(long millis) {
        String full = format(millis);
        return full.substring(0, full.length() - 2);
    }
}
