package pl.mchosting.server.minigry;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * TNT Run: blocks vanish shortly after a player steps on them (with the TNT under them), and
 * every 10 seconds a player may double jump by pressing jump twice.
 */
final class TntRun implements Listener {

    /** Ticks between stepping on a block and the block vanishing. */
    static final long FADE_TICKS = 8;

    static final Duration DOUBLE_JUMP_COOLDOWN = Duration.ofSeconds(10);

    private static final Sound JUMP = Sound.sound(Key.key("entity.breeze.jump"), Sound.Source.MASTER, 1f, 1f);

    private final Plugin plugin;
    private final MinigameEngine engine;
    private final Cooldown doubleJump;

    TntRun(Plugin plugin, MinigameEngine engine, Clock clock) {
        this.plugin = plugin;
        this.engine = engine;
        this.doubleJump = new Cooldown(DOUBLE_JUMP_COOLDOWN, clock);
    }

    /** Runs every 2 ticks: marks blocks under running players to vanish, gives the double jump back. */
    void tick() {
        for (ActiveMatch active : engine.matches()) {
            if (active.type != GameType.TNT_RUN || !active.isRunning()) {
                continue;
            }
            for (UUID id : active.match.alive()) {
                Player player = plugin.getServer().getPlayer(id);
                if (player == null || !player.getWorld().equals(active.world)) {
                    continue;
                }
                Location at = player.getLocation();
                for (BlockPos pos : Footprint.under(at.getX(), at.getY(), at.getZ())) {
                    fade(active, pos);
                }
                if (!player.getAllowFlight() && doubleJump.isReady(id)) {
                    player.setAllowFlight(true);
                }
            }
        }
    }

    private void fade(ActiveMatch active, BlockPos pos) {
        if (!active.inRegion(pos) || active.fading.contains(pos)) {
            return;
        }
        Block block = active.world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getType().isAir()) {
            return;
        }
        active.fading.add(pos);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> remove(active, pos), FADE_TICKS);
    }

    private void remove(ActiveMatch active, BlockPos pos) {
        // The match may have ended and the arena been restored in the meantime.
        if (!active.isRunning() || !active.fading.contains(pos)) {
            return;
        }
        removeBlock(active, pos);
        BlockPos below = pos.below();
        if (active.inRegion(below)
                && active.world.getBlockAt(below.x(), below.y(), below.z()).getType() == Material.TNT) {
            removeBlock(active, below);
        }
    }

    private static void removeBlock(ActiveMatch active, BlockPos pos) {
        Block block = active.world.getBlockAt(pos.x(), pos.y(), pos.z());
        active.changes.remember(block);
        block.setType(Material.AIR, false);
    }

    /** Pressing jump twice in the air asks to fly; in TNT Run it is a double jump instead. */
    @EventHandler(ignoreCancelled = true)
    void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying()
                || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        boolean inTntRun = engine.runningFor(player.getUniqueId())
                .filter(active -> active.type == GameType.TNT_RUN)
                .isPresent();
        if (!inTntRun) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        if (doubleJump.tryUse(player.getUniqueId())) {
            Vector push = player.getLocation().getDirection().setY(0);
            // Looking straight up or down there is no direction to push in.
            push = push.lengthSquared() < 1.0e-4
                    ? new Vector()
                    : push.normalize().multiply(0.6);
            player.setVelocity(push.setY(0.9));
            player.playSound(JUMP);
        }
    }
}
