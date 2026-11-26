package pl.mchosting.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProfileTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void freshProfileStartsEmpty() {
        PlayerProfile profile = PlayerProfile.fresh(id);
        assertEquals(0, profile.totalXp());
        assertEquals(0, profile.coins());
        assertEquals(1, profile.level());
    }

    @Test
    void addingXpRaisesLevel() {
        assertEquals(3, PlayerProfile.fresh(id).withXpAdded(300).level());
    }

    @Test
    void coinsCannotGoNegative() {
        PlayerProfile profile = PlayerProfile.fresh(id).withCoinsChanged(10);
        assertThrows(IllegalArgumentException.class, () -> profile.withCoinsChanged(-11));
    }
}
