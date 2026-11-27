package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardTest {

    @Test
    void plusAddsXpAndCoinsSeparately() {
        assertEquals(new Reward(13, 7), new Reward(10, 5).plus(new Reward(3, 2)));
    }

    @Test
    void plusWithNoneKeepsTheSameValue() {
        Reward reward = new Reward(10, 5);
        assertEquals(reward, reward.plus(Reward.NONE));
    }

    @Test
    void isEmptyOnlyForZeroXpAndCoins() {
        assertTrue(Reward.NONE.isEmpty());
        assertTrue(new Reward(0, 0).isEmpty());
        assertFalse(new Reward(1, 0).isEmpty());
        assertFalse(new Reward(0, 1).isEmpty());
    }

    @Test
    void rejectsNegativeXpAndCoins() {
        assertThrows(IllegalArgumentException.class, () -> new Reward(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Reward(0, -1));
    }

    @Test
    void plusRejectsOverflow() {
        assertThrows(ArithmeticException.class, () -> new Reward(Long.MAX_VALUE, 0).plus(new Reward(1, 0)));
    }
}
