package pl.mchosting.server.minigry;

import java.time.Clock;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * Duels rules: the two duelists may hurt each other (and only each other); a blow that would kill
 * ends the duel instead, so nobody really dies or drops items. In UHC there is no natural
 * regeneration and players may build inside the arena; everything they change is put back after
 * the duel.
 */
final class DuelListener implements Listener {

    private final MinigameEngine engine;
    private final Clock clock;

    DuelListener(MinigameEngine engine, Clock clock) {
        this.engine = engine;
        this.clock = clock;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Optional<ActiveMatch> fight = fightOf(victim);
        if (fight.isEmpty()) {
            return;
        }
        ActiveMatch active = fight.get();
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attackerOf(byEntity.getDamager());
            if (attacker != null && !attacker.equals(victim)) {
                if (!active.isFighting(attacker.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                active.credit.hit(victim.getUniqueId(), attacker.getUniqueId(), clock.instant());
            }
        }
        if (event.getFinalDamage() >= victim.getHealth()) {
            event.setCancelled(true);
            engine.eliminate(victim);
        }
    }

    /** UHC: health only comes back from golden apples. */
    @EventHandler(ignoreCancelled = true)
    void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        boolean natural = reason == EntityRegainHealthEvent.RegainReason.SATIATED
                || reason == EntityRegainHealthEvent.RegainReason.REGEN;
        if (natural
                && fightOf(player)
                        .filter(active -> !active.kit().naturalRegeneration())
                        .isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Remembers placed blocks (the arena listener lets builders through). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        buildMatch(engine, event.getPlayer(), block).ifPresent(active -> {
            BlockPos pos = BlockChanges.pos(block);
            active.changes.remember(pos, event.getBlockReplacedState().getBlockData());
            active.placed.add(pos);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onEmptyBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (engine.matchOf(player.getUniqueId()).isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        Optional<ActiveMatch> build = buildMatch(engine, player, block);
        if (build.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        build.get().changes.remember(block);
        build.get().placed.add(BlockChanges.pos(block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onFillBucket(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (engine.matchOf(player.getUniqueId()).isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        Optional<ActiveMatch> build =
                buildMatch(engine, player, block).filter(active -> active.placed.contains(BlockChanges.pos(block)));
        if (build.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        build.get().changes.remember(block);
    }

    /** Water and lava from a duel stay inside its arena, and are cleaned up after it. */
    @EventHandler(ignoreCancelled = true)
    void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        if (!engine.isArenaWorld(from.getWorld())) {
            return;
        }
        BlockPos source = BlockChanges.pos(from);
        BlockPos target = BlockChanges.pos(to);
        for (ActiveMatch active : engine.matches()) {
            if (!active.world.equals(from.getWorld()) || !active.inRegion(source)) {
                continue;
            }
            if (!active.inRegion(target)) {
                event.setCancelled(true);
                return;
            }
            active.changes.remember(to);
            active.placed.add(target);
            return;
        }
    }

    /** Cobblestone and obsidian made by water meeting lava. */
    @EventHandler(ignoreCancelled = true)
    void onForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (!engine.isArenaWorld(block.getWorld())) {
            return;
        }
        BlockPos pos = BlockChanges.pos(block);
        for (ActiveMatch active : engine.matches()) {
            if (active.world.equals(block.getWorld()) && active.inRegion(pos)) {
                active.changes.remember(block);
                active.placed.add(pos);
                return;
            }
        }
    }

    /** Lava never sets arenas on fire. */
    @EventHandler(ignoreCancelled = true)
    void onIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        if ((cause == BlockIgniteEvent.IgniteCause.LAVA || cause == BlockIgniteEvent.IgniteCause.SPREAD)
                && engine.isArenaWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** The running duel in which {@code player} may build at {@code block} (UHC, inside the arena). */
    static Optional<ActiveMatch> buildMatch(MinigameEngine engine, Player player, Block block) {
        return engine.runningFor(player.getUniqueId())
                .filter(active -> active.kit() != null && active.kit().allowsBuilding())
                .filter(active -> block.getWorld().equals(active.world))
                .filter(active -> active.inRegion(BlockChanges.pos(block)));
    }

    private Optional<ActiveMatch> fightOf(Player player) {
        return engine.matchOf(player.getUniqueId()).filter(active -> active.isFighting(player.getUniqueId()));
    }

    /** The player behind a hit: the one who swung, or who shot the arrow or threw the charge. */
    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
