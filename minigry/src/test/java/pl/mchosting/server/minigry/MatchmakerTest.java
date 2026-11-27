package pl.mchosting.server.minigry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MatchmakerTest {

    private static final Instant T0 = Instant.parse("2026-11-27T18:00:00Z");
    private static final MatchmakingRules RULES = MatchmakingRules.DEFAULT;

    private static QueueEntry player(int rating, boolean newcomer, int joinedSecond) {
        return new QueueEntry(UUID.randomUUID(), rating, newcomer, T0.plusSeconds(joinedSecond));
    }

    private static Set<UUID> ids(List<QueueEntry> entries) {
        return entries.stream().map(QueueEntry::playerId).collect(Collectors.toSet());
    }

    @Test
    void fullGroupStartsAtOnce() {
        Matchmaker queue = new Matchmaker(2, 4, RULES);
        for (int i = 0; i < 4; i++) {
            queue.add(player(500, false, 0));
        }
        assertEquals(4, queue.nextMatch(T0).orElseThrow().size());
        assertEquals(0, queue.size());
    }

    @Test
    void smallGroupWaitsForMorePlayersFirst() {
        Matchmaker queue = new Matchmaker(2, 16, RULES);
        queue.add(player(500, false, 0));
        queue.add(player(500, false, 1));
        assertTrue(queue.nextMatch(T0.plusSeconds(5)).isEmpty());
        assertEquals(2, queue.nextMatch(T0.plus(RULES.fillWait())).orElseThrow().size());
    }

    @Test
    void neverMoreThanMaxPlayers() {
        Matchmaker queue = new Matchmaker(2, 3, RULES);
        for (int i = 0; i < 5; i++) {
            queue.add(player(500, false, i));
        }
        assertEquals(3, queue.nextMatch(T0.plusSeconds(5)).orElseThrow().size());
        assertEquals(2, queue.size());
    }

    @Test
    void notEnoughPlayersNoMatch() {
        Matchmaker queue = new Matchmaker(4, 24, RULES);
        for (int i = 0; i < 3; i++) {
            queue.add(player(500, false, 0));
        }
        assertTrue(queue.nextMatch(T0.plusSeconds(600)).isEmpty());
    }

    @Test
    void newcomersPlayWithNewcomers() {
        Matchmaker queue = new Matchmaker(2, 2, RULES);
        QueueEntry newcomer = player(500, true, 0);
        QueueEntry veteran = player(500, false, 0);
        queue.add(newcomer);
        queue.add(veteran);
        assertTrue(queue.nextMatch(T0.plusSeconds(20)).isEmpty());

        QueueEntry otherNewcomer = player(500, true, 20);
        queue.add(otherNewcomer);
        assertEquals(
                Set.of(newcomer.playerId(), otherNewcomer.playerId()),
                ids(queue.nextMatch(T0.plusSeconds(20)).orElseThrow()));
    }

    @Test
    void newcomerWhoWaitedLongMeetsAnyone() {
        Matchmaker queue = new Matchmaker(2, 2, RULES);
        queue.add(player(500, true, 0));
        queue.add(player(500, false, 0));
        assertTrue(queue.nextMatch(T0.plus(RULES.mixNewcomersAfter()).minusSeconds(1))
                .isEmpty());
        assertEquals(
                2,
                queue.nextMatch(T0.plus(RULES.mixNewcomersAfter()))
                        .orElseThrow()
                        .size());
    }

    @Test
    void ratingsFarApartWaitUntilTheRangeGrows() {
        Matchmaker queue = new Matchmaker(2, 2, RULES);
        queue.add(player(200, false, 0));
        queue.add(player(600, false, 0));
        assertTrue(queue.nextMatch(T0.plusSeconds(30)).isEmpty());
        assertEquals(
                2, queue.nextMatch(T0.plus(RULES.anyoneAfter())).orElseThrow().size());
    }

    @Test
    void closestRatingsArePickedFirst() {
        Matchmaker queue = new Matchmaker(2, 2, RULES);
        QueueEntry anchor = player(500, false, 0);
        QueueEntry close = player(520, false, 2);
        queue.add(anchor);
        queue.add(player(620, false, 1));
        queue.add(close);
        assertEquals(
                Set.of(anchor.playerId(), close.playerId()),
                ids(queue.nextMatch(T0.plusSeconds(3)).orElseThrow()));
    }

    @Test
    void longestWaitingPlayerIsServedFirst() {
        Matchmaker queue = new Matchmaker(2, 2, RULES);
        QueueEntry oldest = player(500, false, 0);
        queue.add(player(500, false, 5));
        queue.add(oldest);
        queue.add(player(500, false, 6));
        assertTrue(ids(queue.nextMatch(T0.plusSeconds(10)).orElseThrow()).contains(oldest.playerId()));
    }

    @Test
    void addAndRemove() {
        Matchmaker queue = new Matchmaker(2, 2, RULES);
        QueueEntry entry = player(500, false, 0);
        assertTrue(queue.add(entry));
        assertFalse(queue.add(entry));
        assertTrue(queue.contains(entry.playerId()));
        assertTrue(queue.remove(entry.playerId()));
        assertFalse(queue.remove(entry.playerId()));
        assertEquals(0, queue.size());
    }

    @Test
    void rejectsBadLimits() {
        assertThrows(IllegalArgumentException.class, () -> new Matchmaker(1, 4, RULES));
        assertThrows(IllegalArgumentException.class, () -> new Matchmaker(5, 4, RULES));
    }

    @Test
    void windowGrowsWithWaiting() {
        assertEquals(RULES.baseWindow(), RULES.window(Duration.ZERO));
        assertEquals(RULES.baseWindow() + RULES.windowGrowth(), RULES.window(RULES.growthStep()));
        assertEquals(Integer.MAX_VALUE, RULES.window(RULES.anyoneAfter()));
    }
}
