package pl.mchosting.server.hub;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Clock;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.LobbyService;
import pl.mchosting.server.api.ProfileService;

public final class HubPlugin extends JavaPlugin {

    private static final long SIDEBAR_REFRESH_TICKS = 20L;

    @Override
    public void onEnable() {
        ProfileService profiles = getServer().getServicesManager().load(ProfileService.class);
        if (profiles == null) {
            throw new IllegalStateException("MchostingCore did not register ProfileService");
        }
        saveDefaultConfig();

        HubSettings settings = new HubSettings(this);
        HubItems items = new HubItems(this);
        HubSidebar sidebar = new HubSidebar(getServer(), profiles);
        PlayerVisibility visibility = new PlayerVisibility(this, items);
        HubMinigames minigames = new HubMinigames(getServer());
        ModeMenu modeMenu = new ModeMenu(settings, minigames);
        ProfileMenu profileMenu = new ProfileMenu(profiles);
        ReturnDirectory directory = new ReturnDirectory(
                settings.panelUrl(), new AllowedHosts(settings.returnDomains()), Clock.systemUTC(), getLogger());
        ReturnListener returns = new ReturnListener(this, directory, items, Clock.systemUTC());
        HubLobby lobby = new HubLobby(getServer(), settings, items, visibility, returns);

        getServer().getServicesManager().register(LobbyService.class, lobby, this, ServicePriority.Normal);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new HubProtectionListener(settings), this);
        plugins.registerEvents(new HubJoinListener(profiles, lobby, sidebar, visibility), this);
        plugins.registerEvents(new HubItemListener(items, modeMenu, profileMenu, visibility, returns, settings), this);
        plugins.registerEvents(returns, this);
        plugins.registerEvents(new MenuListener(), this);
        plugins.registerEvents(new GateListener(settings, minigames), this);

        getServer().getScheduler().runTaskTimer(this, sidebar::updateAll, SIDEBAR_REFRESH_TICKS, SIDEBAR_REFRESH_TICKS);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(HubCommands.spawn(settings), "Wraca na spawn huba");
            event.registrar().register(HubCommands.dailyReward(profiles), "Odbiera codzienną nagrodę");
            event.registrar().register(HubCommands.admin(settings), "Ustawienia huba (dla obsługi)");
            event.registrar().register(HubCommands.goBack(returns), "Powrót na poprzedni serwer z mchosting.pl");
        });
    }
}
