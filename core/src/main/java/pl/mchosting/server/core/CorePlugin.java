package pl.mchosting.server.core;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.ProfileService;

public final class CorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InMemoryProfileService profiles = new InMemoryProfileService();
        getServer().getServicesManager().register(ProfileService.class, profiles, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new ProfileLifecycleListener(profiles), this);
        getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> event.registrar()
                                .register(ProfileCommand.create(profiles), "Pokazuje Twój poziom i monety"));
    }
}
