package pl.mchosting.server.core;

import java.util.UUID;

/** Told when a player reaches a new level. May be called from any thread. */
@FunctionalInterface
interface LevelUpNotifier {

    void levelUp(UUID playerId, int newLevel);
}
