package pl.mchosting.server.api;

import java.util.UUID;

/**
 * Sends players back to the hub. The hub plugin provides it; modes call it when a match ends or
 * a player leaves. On the hub server it means the spawn with a fresh hotbar.
 */
public interface LobbyService {

    /** Sends an online player to the hub spawn and gives them the hub hotbar again. */
    void sendToLobby(UUID playerId);
}
