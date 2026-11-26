package pl.mchosting.server.core;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.ProfileService;

public final class CorePlugin extends JavaPlugin {

    private static final long AUTOSAVE_TICKS = 20L * 60 * 5;
    private static final Duration PENDING_MAX_AGE = Duration.ofMinutes(1);
    private static final Sound LEVEL_UP_SOUND =
            Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 1f, 1f);

    private ExecutorService io;
    private ProfileManager profiles;

    @Override
    public void onEnable() {
        io = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("mchosting-accounts").daemon().factory());
        profiles = new ProfileManager(
                new FileProfileStore(getDataPath().resolve("profiles")),
                io,
                Clock.systemUTC(),
                getLogger(),
                this::announceLevelUp);

        getServer().getServicesManager().register(ProfileService.class, profiles, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new ProfileLifecycleListener(profiles, getLogger()), this);
        getServer().getScheduler().runTaskTimer(this, this::autosave, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(ProfileCommand.create(profiles), "Pokazuje Twój poziom i monety");
            event.registrar().register(AccountAdminCommand.create(profiles), "Konto gracza (dla obsługi)");
        });
    }

    @Override
    public void onDisable() {
        if (profiles == null) {
            return;
        }
        // Server is stopping: the only moment we wait for the disk on the main thread.
        profiles.saveAll();
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                getLogger().severe("Saving accounts did not finish in 30 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void autosave() {
        profiles.saveChanged();
        profiles.forgetStalePending(PENDING_MAX_AGE);
    }

    private void announceLevelUp(UUID playerId, int level) {
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(playerId);
            if (player == null) {
                return;
            }
            String text = String.valueOf(level);
            player.showTitle(Title.title(
                    MiniMessage.miniMessage().deserialize("<gold>Poziom <level>!", Placeholder.unparsed("level", text)),
                    MiniMessage.miniMessage().deserialize("<gray>Gratulacje, grasz coraz lepiej")));
            player.playSound(LEVEL_UP_SOUND);
        });
    }
}
