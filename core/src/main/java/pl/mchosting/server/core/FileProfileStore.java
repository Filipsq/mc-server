package pl.mchosting.server.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import pl.mchosting.server.api.PlayerProfile;

/**
 * One text file per player in the plugin folder. Good enough for the start and easy to read by
 * hand; replaced by MariaDB once more servers share accounts.
 */
final class FileProfileStore implements ProfileStore {

    private final Path folder;

    FileProfileStore(Path folder) {
        this.folder = folder;
    }

    @Override
    public Optional<PlayerProfile> load(UUID playerId) throws IOException {
        Path file = fileOf(playerId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        try {
            return Optional.of(ProfileCodec.decode(playerId, properties));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new IOException("damaged profile file " + file, e);
        }
    }

    @Override
    public void save(PlayerProfile profile) throws IOException {
        Files.createDirectories(folder);
        Path file = fileOf(profile.playerId());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            ProfileCodec.encode(profile).store(writer, "MchostingCore player account");
        }
        // Replace in one step so a crash never leaves a half-written account behind.
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path fileOf(UUID playerId) {
        return folder.resolve(playerId + ".properties");
    }
}
