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
 * Minigames: Spleef, TNT Run, Duels and Parkour. Runs on the hub server, arenas and courses live
 * in their own worlds; the hub's gates and compass join the queues through {@link
 * MinigameService}.
 */
public final class MinigryPlugin extends JavaPlugin {

    private static final long SECOND = 20L;
    private static final long TNT_RUN_TICKS = 2L;
    private static final long FAST_TICKS = 2L;

    private ArenaStore arenas;
    private ParkourStore courses;
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
        courses = new ParkourStore(this);
        courses.load();
        loadArenaWorlds();

        Lobby lobby = new Lobby(this);
        ParkourManager parkour = new ParkourManager(this, profiles, courses, lobby, clock);
        engine = new MinigameEngine(this, profiles, arenas, lobby, parkour, clock);
        getServer().getServicesManager().register(MinigameService.class, engine, this, ServicePriority.Normal);

        TntRun tntRun = new TntRun(this, engine, clock);
        getServer().getPluginManager().registerEvents(new ArenaListener(engine, clock), this);
        getServer().getPluginManager().registerEvents(tntRun, this);
        getServer().getPluginManager().registerEvents(new DuelListener(engine, clock), this);
        getServer().getPluginManager().registerEvents(new ParkourListener(parkour), this);
        getServer().getPluginManager().registerEvents(new ChoiceMenu.Clicks(), this);
        getServer().getScheduler().runTaskTimer(this, engine::tickSecond, SECOND, SECOND);
        getServer().getScheduler().runTaskTimer(this, tntRun::tick, TNT_RUN_TICKS, TNT_RUN_TICKS);
        getServer().getScheduler().runTaskTimer(this, engine::tickFast, FAST_TICKS, FAST_TICKS);

        MinigryCommands commands = new MinigryCommands(engine, arenas);
        ParkourCommands parkourCommands = new ParkourCommands(engine);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(commands.play(), "Dołącza do kolejki minigry");
            event.registrar().register(commands.leave(), "Wychodzi z kolejki albo z meczu");
            event.registrar().register(commands.admin(), "Ustawienia aren (dla obsługi)");
            event.registrar().register(parkourCommands.play(), "Trasy parkouru i rekordy");
            event.registrar().register(parkourCommands.admin(), "Ustawienia tras parkouru (dla obsługi)");
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
        if (courses != null) {
            courses.close();
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
