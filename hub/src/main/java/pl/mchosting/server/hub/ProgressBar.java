package pl.mchosting.server.hub;

/** Text progress bar like {@code ■■■■■□□□□□} for level progress. */
final class ProgressBar {

    static final char FULL = '■';
    static final char EMPTY = '□';

    private ProgressBar() {}

    /** Number of filled cells out of {@code width} for {@code fraction} (0.0 to 1.0). */
    static int filledCells(double fraction, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width must be positive");
        }
        double clamped = Math.clamp(fraction, 0.0, 1.0);
        return (int) Math.floor(clamped * width);
    }

    /** MiniMessage text of the bar: filled cells in green, the rest in dark gray. */
    static String render(double fraction, int width) {
        int filled = filledCells(fraction, width);
        return "<green>" + String.valueOf(FULL).repeat(filled) + "</green><dark_gray>"
                + String.valueOf(EMPTY).repeat(width - filled) + "</dark_gray>";
    }
}
