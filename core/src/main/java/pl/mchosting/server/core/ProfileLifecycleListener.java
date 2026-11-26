package pl.mchosting.server.core;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads the account while the player is still connecting (off the main thread), makes it live on
 * join and saves it on quit.
 */
final class ProfileLifecycleListener implements Listener {

    private static final Component LOAD_FAILED =
            Component.text("Nie udało się wczytać Twojego konta. Spróbuj ponownie za chwilę.", NamedTextColor.RED);

    private final ProfileManager profiles;
    private final Logger logger;

    ProfileLifecycleListener(ProfileManager profiles, Logger logger) {
        this.profiles = profiles;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            profiles.preload(event.getUniqueId());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Refusing join of " + event.getName() + ": account not loaded", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, LOAD_FAILED);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onJoin(PlayerJoinEvent event) {
        if (!profiles.activate(event.getPlayer().getUniqueId())) {
            event.getPlayer().kick(LOAD_FAILED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onQuit(PlayerQuitEvent event) {
        profiles.unload(event.getPlayer().getUniqueId());
    }
}
