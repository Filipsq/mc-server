package pl.mchosting.server.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import pl.mchosting.server.api.DailyStreak;
import pl.mchosting.server.api.PlayerProfile;

/** Turns an account into simple {@code key=value} text and back. */
final class ProfileCodec {

    static final int SCHEMA = 1;

    private static final String STAT_PREFIX = "stat.";

    private ProfileCodec() {}

    static Properties encode(PlayerProfile profile) {
        Properties out = new Properties();
        out.setProperty("schema", String.valueOf(SCHEMA));
        out.setProperty("xp", String.valueOf(profile.totalXp()));
        out.setProperty("coins", String.valueOf(profile.coins()));
        out.setProperty("first-join", profile.firstJoin().toString());
        out.setProperty("tutorial-done", String.valueOf(profile.tutorialDone()));
        DailyStreak daily = profile.daily();
        if (daily.lastClaim() != null) {
            out.setProperty("daily.last", daily.lastClaim().toString());
            out.setProperty("daily.streak", String.valueOf(daily.streak()));
        }
        profile.stats().forEach((id, value) -> out.setProperty(STAT_PREFIX + id, String.valueOf(value)));
        return out;
    }

    /**
     * @throws IllegalArgumentException if the text is damaged or from a newer plugin version
     */
    static PlayerProfile decode(UUID playerId, Properties in) {
        int schema = Integer.parseInt(required(in, "schema"));
        if (schema > SCHEMA) {
            throw new IllegalArgumentException("profile schema " + schema + " is newer than " + SCHEMA);
        }
        String last = in.getProperty("daily.last");
        DailyStreak daily = last == null
                ? DailyStreak.NONE
                : new DailyStreak(LocalDate.parse(last), Integer.parseInt(required(in, "daily.streak")));
        Map<String, Long> stats = new HashMap<>();
        for (String key : in.stringPropertyNames()) {
            if (key.startsWith(STAT_PREFIX)) {
                stats.put(key.substring(STAT_PREFIX.length()), Long.parseLong(in.getProperty(key)));
            }
        }
        return new PlayerProfile(
                playerId,
                Long.parseLong(required(in, "xp")),
                Long.parseLong(required(in, "coins")),
                daily,
                stats,
                Instant.parse(required(in, "first-join")),
                Boolean.parseBoolean(in.getProperty("tutorial-done", "false")));
    }

    private static String required(Properties in, String key) {
        String value = in.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }
}
