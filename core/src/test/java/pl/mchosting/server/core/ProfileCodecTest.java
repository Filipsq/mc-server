package pl.mchosting.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.mchosting.server.api.DailyStreak;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.Reward;
import pl.mchosting.server.api.Stats;

class ProfileCodecTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void roundTripKeepsEverything() {
        PlayerProfile profile = PlayerProfile.fresh(id, Instant.parse("2026-11-27T10:15:30Z"))
                .withReward(new Reward(1234, 56))
                .withDaily(new DailyStreak(LocalDate.of(2026, 11, 26), 3))
                .withStat(Stats.wins(Stats.SPLEEF), 7)
                .withStat(Stats.bestTimeMillis("las"), 41_250)
                .withTutorialDone();
        assertEquals(profile, ProfileCodec.decode(id, ProfileCodec.encode(profile)));
    }

    @Test
    void roundTripOfFreshProfile() {
        PlayerProfile profile = PlayerProfile.fresh(id, Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(profile, ProfileCodec.decode(id, ProfileCodec.encode(profile)));
    }

    @Test
    void refusesNewerSchema() {
        Properties properties = ProfileCodec.encode(PlayerProfile.fresh(id, Instant.EPOCH));
        properties.setProperty("schema", String.valueOf(ProfileCodec.SCHEMA + 1));
        assertThrows(IllegalArgumentException.class, () -> ProfileCodec.decode(id, properties));
    }

    @Test
    void refusesMissingFields() {
        Properties properties = ProfileCodec.encode(PlayerProfile.fresh(id, Instant.EPOCH));
        properties.remove("coins");
        assertThrows(IllegalArgumentException.class, () -> ProfileCodec.decode(id, properties));
    }

    @Test
    void refusesDamagedNumbers() {
        Properties properties = ProfileCodec.encode(PlayerProfile.fresh(id, Instant.EPOCH));
        properties.setProperty("xp", "dużo");
        assertThrows(IllegalArgumentException.class, () -> ProfileCodec.decode(id, properties));
    }
}
