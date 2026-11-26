package pl.mchosting.server.hub;

import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.ProfileService;

public final class HubPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        ProfileService profiles = getServer().getServicesManager().load(ProfileService.class);
        if (profiles == null) {
            throw new IllegalStateException("MchostingCore did not register ProfileService");
        }
        getServer().getPluginManager().registerEvents(new HubProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new HubJoinListener(), this);
    }
}
