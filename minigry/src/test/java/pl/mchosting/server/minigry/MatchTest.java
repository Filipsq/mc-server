package pl.mchosting.server.minigry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.api.MatchResult;

class MatchTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    private Match running(UUID... players) {
        Match match = new Match(List.of(players), 2, 3);
        while (match.phase() == Match.Phase.COUNTDOWN) {
            match.tickCountdown();
        }
        return match;
    }

    @Test
    void countdownThenRunning() {
        Match match = new Match(List.of(a, b), 2, 3);
        assertEquals(2, match.tickCountdown());
        assertEquals(1, match.tickCountdown());
        assertEquals(Match.Phase.COUNTDOWN, match.phase());
        assertEquals(0, match.tickCountdown());
        assertEquals(Match.Phase.RUNNING, match.phase());
        assertEquals(2, match.starters());
    }

    @Test
    void leavingBeforeStartGivesNoPlace() {
        Match match = new Match(List.of(a, b, c), 2, 3);
        assertEquals(OptionalInt.empty(), match.eliminate(a, null));
        assertFalse(match.roster().contains(a));
        assertTrue(match.canStart());
        match.eliminate(b, null);
        assertFalse(match.canStart());
        assertThrows(IllegalStateException.class, match::tickCountdown);
    }

    @Test
    void lastStandingWins() {
        Match match = running(a, b, c, d);
        assertEquals(OptionalInt.of(4), match.eliminate(a, b));
        assertEquals(OptionalInt.of(3), match.eliminate(c, b));
        assertEquals(Match.Phase.RUNNING, match.phase());
        assertEquals(OptionalInt.of(2), match.eliminate(d, null));
        assertEquals(Match.Phase.ENDED, match.phase());
        assertEquals(Optional.of(b), match.winner());

        Map<UUID, MatchResult> results = match.results();
        assertEquals(new MatchResult(4, 1, 2), results.get(b));
        assertEquals(new MatchResult(4, 4, 0), results.get(a));
        assertEquals(new MatchResult(4, 2, 0), results.get(d));
    }

    @Test
    void outPlayerCannotBeOutTwice() {
        Match match = running(a, b, c);
        match.eliminate(a, null);
        assertEquals(OptionalInt.empty(), match.eliminate(a, null));
    }

    @Test
    void noEliminationsAfterTheEnd() {
        Match match = running(a, b);
        match.eliminate(a, null);
        assertEquals(OptionalInt.empty(), match.eliminate(b, null));
        assertEquals(Optional.of(b), match.winner());
    }

    @Test
    void selfAndOutsiderKillsDoNotCount() {
        Match match = running(a, b, c);
        match.eliminate(a, a);
        match.eliminate(b, UUID.randomUUID());
        assertEquals(0, match.kills(a));
        assertEquals(0, match.kills(c));
    }

    @Test
    void timeoutHasNoWinner() {
        Match match = running(a, b, c, d);
        match.eliminate(a, null);
        match.endByTimeout();
        assertEquals(Match.Phase.ENDED, match.phase());
        assertEquals(Optional.empty(), match.winner());
        assertEquals(3, match.results().get(b).placement());
        assertEquals(3, match.results().get(d).placement());
        assertEquals(4, match.results().get(a).placement());
    }

    @Test
    void forgottenSpectatorKeepsResult() {
        Match match = running(a, b, c);
        match.eliminate(a, null);
        match.forget(a);
        assertFalse(match.roster().contains(a));
        assertThrows(IllegalStateException.class, () -> match.forget(b));
        match.eliminate(b, null);
        assertEquals(3, match.results().get(a).placement());
    }

    @Test
    void rejectsTooFewOrDuplicatePlayers() {
        assertThrows(IllegalArgumentException.class, () -> new Match(List.of(a), 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new Match(List.of(a, a), 2, 3));
    }
}
