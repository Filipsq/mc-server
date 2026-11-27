package pl.mchosting.server.link;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Clock;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class LinkPlugin extends JavaPlugin implements Listener {

    private Cooldown cooldown;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration file = getConfig();
        LinkConfig config = LinkConfig.from(
                file.getString("hub.host"),
                file.getInt("hub.port", LinkConfig.DEFAULT_PORT),
                file.getString("server-id"),
                file.getString("private-key"),
                file.getInt("cooldown-seconds", (int) LinkConfig.DEFAULT_COOLDOWN.toSeconds()),
                problem -> getLogger().warning(problem));

        Clock clock = Clock.systemUTC();
        cooldown = new Cooldown(config.cooldown(), clock);
        HubCommand hub = new HubCommand(config, new TicketIssuer(config.signing(), clock), cooldown);
        getServer().getPluginManager().registerEvents(this, this);

        // Only /hub: owners often have their own /lobby or /spawn, and those stay theirs.
        getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> event.registrar().register(hub.node("hub"), "Przenosi do huba sieci mchosting.pl"));
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        cooldown.forget(event.getPlayer().getUniqueId());
    }
}
