package pl.mchosting.server.core;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import pl.mchosting.server.api.PlayerProfile;

/**
 * Where accounts are kept between sessions. Calls block, so they must never run on the main
 * server thread. Today this is a folder of files; later MariaDB (see docs/architektura.md).
 */
interface ProfileStore {

    /** Stored account, or empty for a player who never played. */
    Optional<PlayerProfile> load(UUID playerId) throws IOException;

    void save(PlayerProfile profile) throws IOException;
}
