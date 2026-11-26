package pl.mchosting.server.api;

/**
 * How one player finished one minigame match.
 *
 * @param players how many players started the match
 * @param placement final place, 1 = winner
 * @param kills players this player eliminated
 */
public record MatchResult(int players, int placement, int kills) {

    public MatchResult {
        if (players < 1 || placement < 1 || placement > players || kills < 0) {
            throw new IllegalArgumentException(
                    "bad match result: players=" + players + " placement=" + placement + " kills=" + kills);
        }
    }

    public boolean won() {
        return placement == 1;
    }
}
