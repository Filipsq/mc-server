package pl.mchosting.server.minigry;

import java.time.Clock;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * What players may do in arena worlds: nothing gets broken or built except by the game itself
 * (and by staff with permission), nobody gets hurt, falling below the arena means you are out.
 * Also the Spleef rules: snow breaks instantly and gives snowballs.
 */
final class ArenaListener implements Listener {

    static final Material SPLEEF_BLOCK = Material.SNOW_BLOCK;
    static final int MAX_SNOWBALLS = 16;

    private final MinigameEngine engine;
    private final Clock clock;

    ArenaListener(MinigameEngine engine, Clock clock) {
        this.engine = engine;
        this.clock = clock;
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        engine.matchOf(player.getUniqueId())
                .filter(active -> active.match.isAlive(player.getUniqueId()))
                .filter(active ->
                        active.arena.eliminationY() != null && event.getTo().getY() < active.arena.eliminationY())
                .ifPresent(active -> engine.eliminate(player));
    }

    @EventHandler(ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || engine.matchOf(player.getUniqueId()).isEmpty()) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            engine.eliminate(player);
        }
        // Snowballs do no damage but push players; that push is the point of throwing them.
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setDamage(0);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && engine.matchOf(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (spleefBlock(player, event.getBlock()).isPresent()) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<ActiveMatch> spleef = spleefBlock(player, event.getBlock());
        if (spleef.isPresent()) {
            ActiveMatch active = spleef.get();
            active.changes.remember(event.getBlock());
            event.setDropItems(false);
            event.setExpToDrop(0);
            creditPlayersAbove(active, event.getBlock(), player);
            if (!player.getInventory().containsAtLeast(ItemStack.of(Material.SNOWBALL), MAX_SNOWBALLS)) {
                player.getInventory().addItem(ItemStack.of(Material.SNOWBALL));
            }
            return;
        }
        if (mustNotChange(player, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (engine.matchOf(event.getPlayer().getUniqueId()).isPresent()
                || mustNotChange(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onDrop(PlayerDropItemEvent event) {
        if (engine.matchOf(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** A snowball breaks the snow it hits and marks the thrower as the attacker of whoever it hits. */
    @EventHandler
    void onSnowball(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !(snowball.getShooter() instanceof Player thrower)) {
            return;
        }
        Block block = event.getHitBlock();
        if (block != null) {
            spleefBlock(thrower, block).ifPresent(active -> {
                active.changes.remember(block);
                creditPlayersAbove(active, block, thrower);
                block.setType(Material.AIR, false);
            });
        }
        if (event.getHitEntity() instanceof Player victim) {
            engine.runningFor(thrower.getUniqueId())
                    .filter(active -> active.match.isAlive(victim.getUniqueId()))
                    .ifPresent(
                            active -> active.credit.hit(victim.getUniqueId(), thrower.getUniqueId(), clock.instant()));
        }
    }

    @EventHandler
    void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        engine.matchOf(player.getUniqueId())
                .filter(active -> !active.world.equals(player.getWorld()))
                .ifPresent(active -> engine.quit(player.getUniqueId()));
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        engine.quit(event.getPlayer().getUniqueId());
    }

    /** The running Spleef match in which {@code player} may break {@code block}. */
    private Optional<ActiveMatch> spleefBlock(Player player, Block block) {
        return engine.runningFor(player.getUniqueId())
                .filter(active -> active.type == GameType.SPLEEF)
                .filter(active -> block.getType() == SPLEEF_BLOCK)
                .filter(active -> block.getWorld().equals(active.world))
                .filter(active -> active.inRegion(BlockChanges.pos(block)));
    }

    /** Whoever stands on a block that disappears was "pushed" by the player who broke it. */
    private void creditPlayersAbove(ActiveMatch active, Block block, Player attacker) {
        Location center = block.getLocation().add(0.5, 1, 0.5);
        for (Player victim : block.getWorld().getPlayers()) {
            if (active.match.isAlive(victim.getUniqueId())
                    && victim.getLocation().distanceSquared(center) <= 2.25) {
                active.credit.hit(victim.getUniqueId(), attacker.getUniqueId(), clock.instant());
            }
        }
    }

    private boolean mustNotChange(Player player, Block block) {
        return engine.isArenaWorld(block.getWorld()) && !player.hasPermission(MinigryPermissions.ADMIN);
    }
}
