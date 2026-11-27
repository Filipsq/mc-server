package pl.mchosting.server.minigry;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.Files;
import java.time.Clock;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.MinigameService;
import pl.mchosting.server.api.ProfileService;

/**
 * Minigames: Spleef and TNT Run for now. Runs on the hub server, arenas live in their own worlds;
 * the hub's gates and compass join the queues through {@link MinigameService}.
 */
public final class MinigryPlugin extends JavaPlugin {

    private static final long SECOND = 20L;
    private static final long TNT_RUN_TICKS = 2L;

    private ArenaStore arenas;
    private MinigameEngine engine;

    @Override
    public void onEnable() {
        ProfileService profiles = getServer().getServicesManager().load(ProfileService.class);
        if (profiles == null) {
            throw new IllegalStateException("MchostingCore did not register ProfileService");
        }
        Clock clock = Clock.systemUTC();
        arenas = new ArenaStore(this);
        arenas.load();
        loadArenaWorlds();

        engine = new MinigameEngine(this, profiles, arenas, clock);
        getServer().getServicesManager().register(MinigameService.class, engine, this, ServicePriority.Normal);

        TntRun tntRun = new TntRun(this, engine, clock);
        getServer().getPluginManager().registerEvents(new ArenaListener(engine, clock), this);
        getServer().getPluginManager().registerEvents(tntRun, this);
        getServer().getScheduler().runTaskTimer(this, engine::tickSecond, SECOND, SECOND);
        getServer().getScheduler().runTaskTimer(this, tntRun::tick, TNT_RUN_TICKS, TNT_RUN_TICKS);

        MinigryCommands commands = new MinigryCommands(engine, arenas);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(commands.play(), "Dołącza do kolejki minigry");
            event.registrar().register(commands.leave(), "Wychodzi z kolejki albo z meczu");
            event.registrar().register(commands.admin(), "Ustawienia aren (dla obsługi)");
        });
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.shutdown();
        }
        if (arenas != null) {
            arenas.close();
        }
    }

    /**
     * Loads arena worlds made with {@code /minigryadmin swiat}. Bukkit loads worlds only on the main
     * thread; this runs once at startup, before players can join.
     */
    private void loadArenaWorlds() {
        for (String name : arenas.worlds()) {
            if (getServer().getWorld(name) != null) {
                continue;
            }
            if (!Files.isDirectory(getServer().getWorldContainer().toPath().resolve(name))) {
                getLogger().warning("Arena world " + name + " is missing, its arenas stay off");
                continue;
            }
            new WorldCreator(name).generator(new VoidGenerator()).createWorld();
        }
    }
}
