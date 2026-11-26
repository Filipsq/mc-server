package pl.mchosting.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.Reward;

class FileProfileStoreTest {

    @TempDir
    Path folder;

    private final UUID id = UUID.randomUUID();

    @Test
    void unknownPlayerHasNoAccount() throws IOException {
        assertEquals(Optional.empty(), new FileProfileStore(folder.resolve("profiles")).load(id));
    }

    @Test
    void savedAccountCanBeLoaded() throws IOException {
        FileProfileStore store = new FileProfileStore(folder.resolve("profiles"));
        PlayerProfile profile = PlayerProfile.fresh(id, Instant.EPOCH).withReward(new Reward(500, 20));
        store.save(profile);
        store.save(profile.withCoinsChanged(5));
        assertEquals(Optional.of(profile.withCoinsChanged(5)), store.load(id));
        try (var files = Files.list(folder.resolve("profiles"))) {
            assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp")));
        }
    }

    @Test
    void damagedFileIsAnErrorNotAnEmptyAccount() throws IOException {
        FileProfileStore store = new FileProfileStore(folder);
        Files.writeString(folder.resolve(id + ".properties"), "schema=1\nxp=oops\n");
        assertThrows(IOException.class, () -> store.load(id));
    }
}
