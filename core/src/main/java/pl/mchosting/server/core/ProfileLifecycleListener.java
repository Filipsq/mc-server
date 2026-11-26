package pl.mchosting.server.core;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads a profile when a player joins and drops it when they leave. */
final class ProfileLifecycleListener implements Listener {

    private final InMemoryProfileService profiles;

    ProfileLifecycleListener(InMemoryProfileService profiles) {
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onJoin(PlayerJoinEvent event) {
        profiles.load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onQuit(PlayerQuitEvent event) {
        profiles.unload(event.getPlayer().getUniqueId());
    }
}
