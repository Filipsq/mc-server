package pl.mchosting.server.minigry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.LobbyService;
import pl.mchosting.server.api.MatchResult;
import pl.mchosting.server.api.MatchRewards;
import pl.mchosting.server.api.MinigameService;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;
import pl.mchosting.server.api.QueueJoin;
import pl.mchosting.server.api.Reward;
import pl.mchosting.server.api.Stats;

/**
 * Runs queues and matches on this server. The rules live in {@link Matchmaker} and {@link Match};
 * this class moves players, changes blocks, shows messages and pays rewards. Main thread only.
 */
final class MinigameEngine implements MinigameService {

    private static final Sound MATCH_FOUND =
            Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1f, 1.5f);
    private static final Sound TICK = Sound.sound(Key.key("block.note_block.hat"), Sound.Source.MASTER, 1f, 1f);
    private static final Sound GO = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 1f, 1f);

    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final ArenaStore arenas;
    private final Clock clock;
    private final ArenaPool pool = new ArenaPool();
    private final Map<GameType, Matchmaker> queues = new EnumMap<>(GameType.class);
    private final Map<UUID, GameType> queuedFor = new HashMap<>();
    private final Map<UUID, ActiveMatch> matchOf = new HashMap<>();
    private final Set<ActiveMatch> matches = new LinkedHashSet<>();

    MinigameEngine(JavaPlugin plugin, ProfileService profiles, ArenaStore arenas, Clock clock) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.arenas = arenas;
        this.clock = clock;
        for (GameType type : GameType.values()) {
            queues.put(type, new Matchmaker(type.minPlayers(), type.maxPlayers(), MatchmakingRules.DEFAULT));
        }
        arenas.all().forEach(this::refreshArena);
    }

    // ---- MinigameService (called by the hub) ----

    @Override
    public Set<String> availableGames() {
        Set<String> games = new LinkedHashSet<>();
        for (GameType type : GameType.values()) {
            if (pool.hasArena(type)) {
                games.add(type.id());
            }
        }
        return games;
    }

    @Override
    public QueueJoin join(UUID playerId, String gameId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("player " + playerId + " is not online");
        }
        Optional<GameType> found = GameType.byId(gameId);
        if (found.isEmpty()) {
            player.sendMessage(MiniText.text("<red>Nie ma takiej gry."));
            return QueueJoin.UNKNOWN_GAME;
        }
        GameType type = found.get();
        ActiveMatch current = matchOf.get(playerId);
        if (current != null && current.match.isAlive(playerId)) {
            player.sendMessage(MiniText.text("<red>Najpierw skończ obecny mecz albo wpisz <white>/opusc</white>."));
            return QueueJoin.IN_MATCH;
        }
        if (!pool.hasArena(type)) {
            player.sendMessage(MiniText.text(
                    "<yellow><game> jeszcze nie działa, areny są w budowie. Zajrzyj wkrótce!",
                    Placeholder.unparsed("game", type.displayName())));
            return QueueJoin.NO_ARENA;
        }
        GameType previous = queuedFor.get(playerId);
        if (previous == type) {
            player.sendMessage(MiniText.text(
                    "<yellow>Już czekasz w kolejce do <game>.", Placeholder.unparsed("game", type.displayName())));
            return QueueJoin.ALREADY_QUEUED;
        }
        if (previous != null) {
            queues.get(previous).remove(playerId);
        }
        queues.get(type).add(entryFor(playerId, type));
        queuedFor.put(playerId, type);
        player.sendMessage(MiniText.text(
                "<green>Jesteś w kolejce do <white><game></white>. Możesz chodzić po hubie, damy znać, gdy mecz"
                        + " będzie gotowy. <gray>Wyjście z kolejki: <white>/opusc",
                Placeholder.unparsed("game", type.displayName())));
        return previous == null ? QueueJoin.JOINED : QueueJoin.SWITCHED;
    }

    @Override
    public boolean leave(UUID playerId) {
        GameType type = queuedFor.remove(playerId);
        if (type == null) {
            return false;
        }
        queues.get(type).remove(playerId);
        return true;
    }

    @Override
    public Optional<String> queuedGame(UUID playerId) {
        return Optional.ofNullable(queuedFor.get(playerId)).map(GameType::id);
    }

    @Override
    public int queued(String gameId) {
        return GameType.byId(gameId).map(type -> queues.get(type).size()).orElse(0);
    }

    @Override
    public int playing(String gameId) {
        int playing = 0;
        for (ActiveMatch active : matches) {
            if (active.type.id().equals(gameId)) {
                playing += active.match.alive().size();
            }
        }
        return playing;
    }

    // ---- used by listeners and commands ----

    Optional<ActiveMatch> matchOf(UUID playerId) {
        return Optional.ofNullable(matchOf.get(playerId));
    }

    /** The match the player is alive in, while it is running. */
    Optional<ActiveMatch> runningFor(UUID playerId) {
        return matchOf(playerId).filter(active -> active.isRunning() && active.match.isAlive(playerId));
    }

    Set<ActiveMatch> matches() {
        return Set.copyOf(matches);
    }

    boolean isArenaWorld(World world) {
        for (ArenaConfig arena : arenas.all()) {
            if (arena.world().equals(world.getName())) {
                return true;
            }
        }
        return arenas.worlds().contains(world.getName());
    }

    boolean isArenaBusy(String arenaId) {
        return pool.isBusy(arenaId);
    }

    /** Re-reads an arena after staff changed it; it hosts matches only when fully set up. */
    void refreshArena(ArenaConfig arena) {
        boolean worldLoaded = plugin.getServer().getWorld(arena.world()) != null;
        pool.setReady(arena.id(), arena.type(), arena.isReady() && worldLoaded);
    }

    void forgetArena(ArenaConfig arena) {
        pool.setReady(arena.id(), arena.type(), false);
    }

    /** {@code /opusc}: leaves the queue, or the match (counts as a loss once it started). */
    void leaveEverything(Player player) {
        UUID id = player.getUniqueId();
        boolean wasQueued = leave(id);
        ActiveMatch active = matchOf.get(id);
        if (active != null) {
            removeFromMatch(active, id);
            sendToLobby(player);
            player.sendMessage(MiniText.text("<gray>Opuszczono mecz."));
        } else if (wasQueued) {
            player.sendActionBar(Component.empty());
            player.sendMessage(MiniText.text("<gray>Opuszczono kolejkę."));
        } else {
            player.sendMessage(MiniText.text("<gray>Nie czekasz w żadnej kolejce."));
        }
    }

    /** A player left the server: out of the queue and out of the match, without a reward. */
    void quit(UUID playerId) {
        leave(playerId);
        ActiveMatch active = matchOf.get(playerId);
        if (active != null) {
            removeFromMatch(active, playerId);
        }
    }

    /** A player fell, left the arena world or otherwise lost. */
    void eliminate(Player player) {
        ActiveMatch active = matchOf.get(player.getUniqueId());
        if (active == null || !active.match.isAlive(player.getUniqueId())) {
            return;
        }
        switch (active.match.phase()) {
            // Fell off before the start: back to the spawn, nothing lost.
            case COUNTDOWN -> player.teleport(active.spawn(0));
            case RUNNING -> knockOut(active, player);
            // The winner fell during the celebration.
            case ENDED -> player.teleport(active.spectatorPoint());
        }
    }

    // ---- the clock ----

    /** Runs once a second: forms matches, counts down, ends matches, shows queue status. */
    void tickSecond() {
        Instant now = clock.instant();
        for (GameType type : GameType.values()) {
            formMatches(type, now);
        }
        for (ActiveMatch active : List.copyOf(matches)) {
            tickMatch(active, now);
        }
        showQueueStatus(now);
    }

    /** Puts every arena back and sends players to the hub when the server stops. */
    void shutdown() {
        for (ActiveMatch active : List.copyOf(matches)) {
            cleanUp(active);
        }
    }

    // ---- internals ----

    private QueueEntry entryFor(UUID playerId, GameType type) {
        Optional<PlayerProfile> profile = profiles.profile(playerId);
        long games = profile.map(p -> p.stat(Stats.gamesPlayed(type.id()))).orElse(0L);
        long points = profile.map(p -> p.stat(Stats.skillPoints(type.id()))).orElse(0L);
        int rating = SkillRating.rating(games, Math.min(points, games * SkillRating.MAX));
        return new QueueEntry(playerId, rating, SkillRating.isNewcomer(games), clock.instant());
    }

    private void formMatches(GameType type, Instant now) {
        Matchmaker queue = queues.get(type);
        while (pool.free(type) > 0) {
            Optional<List<QueueEntry>> group = queue.nextMatch(now);
            if (group.isEmpty()) {
                return;
            }
            List<QueueEntry> online = new ArrayList<>();
            for (QueueEntry entry : group.get()) {
                queuedFor.remove(entry.playerId());
                if (plugin.getServer().getPlayer(entry.playerId()) != null) {
                    online.add(entry);
                }
            }
            if (online.size() < type.minPlayers()) {
                requeue(type, online);
                return;
            }
            String arenaId = pool.claim(type).orElseThrow();
            ArenaConfig arena = arenas.get(arenaId).orElseThrow();
            World world = plugin.getServer().getWorld(arena.world());
            if (world == null) {
                pool.setReady(arenaId, type, false);
                pool.release(arenaId);
                requeue(type, online);
                continue;
            }
            start(new ActiveMatch(type, arena, world, online));
        }
    }

    private void requeue(GameType type, List<QueueEntry> entries) {
        for (QueueEntry entry : entries) {
            if (plugin.getServer().getPlayer(entry.playerId()) != null) {
                queues.get(type).add(entry);
                queuedFor.put(entry.playerId(), type);
            }
        }
    }

    private void start(ActiveMatch active) {
        matches.add(active);
        int index = 0;
        for (UUID id : active.match.roster()) {
            Player player = plugin.getServer().getPlayer(id);
            ActiveMatch old = matchOf.put(id, active);
            if (old != null && old != active) {
                old.match.forget(id);
            }
            if (player == null) {
                continue;
            }
            preparePlayer(player, GameMode.ADVENTURE);
            player.teleport(active.spawn(index++));
            player.playSound(MATCH_FOUND);
            player.showTitle(Title.title(
                    MiniText.text("<green>Mecz znaleziony!"),
                    MiniText.text(
                            "<gray><game>, start za chwilę", Placeholder.unparsed("game", active.type.displayName()))));
        }
        plugin.getLogger()
                .info("Match of " + active.type.id() + " started in " + active.arena.id() + " with "
                        + active.match.roster().size() + " players");
    }

    private void tickMatch(ActiveMatch active, Instant now) {
        switch (active.match.phase()) {
            case COUNTDOWN -> tickCountdown(active, now);
            case RUNNING -> {
                if (Duration.between(active.startedAt, now).compareTo(active.type.timeLimit()) >= 0) {
                    active.match.endByTimeout();
                    broadcast(active, MiniText.text("<yellow>Czas minął! Remis, nikt nie wygrywa."));
                    finish(active);
                }
            }
            case ENDED -> {
                active.endingLeft--;
                if (active.endingLeft <= 0) {
                    cleanUp(active);
                }
            }
        }
    }

    private void tickCountdown(ActiveMatch active, Instant now) {
        if (!active.match.canStart()) {
            callOff(active);
            return;
        }
        int left = active.match.tickCountdown();
        if (left > 0) {
            if (left <= 5) {
                for (Player player : online(active)) {
                    player.showTitle(Title.title(MiniText.text("<yellow>" + left), Component.empty(), times()));
                    player.playSound(TICK);
                }
            }
            return;
        }
        active.startedAt = now;
        for (Player player : online(active)) {
            equip(active.type, player);
            player.showTitle(Title.title(MiniText.text("<green>Start!"), Component.empty(), times()));
            player.playSound(GO);
        }
    }

    /** Too many players left during the countdown: everyone goes back to the front of the queue. */
    private void callOff(ActiveMatch active) {
        List<QueueEntry> back = new ArrayList<>();
        for (UUID id : active.match.roster()) {
            back.add(active.entries.get(id));
        }
        List<Player> players = online(active);
        cleanUp(active);
        requeue(active.type, back);
        for (Player player : players) {
            player.sendMessage(MiniText.text("<yellow>Za mało graczy, mecz odwołany. Wracasz na początek kolejki."));
        }
    }

    private void removeFromMatch(ActiveMatch active, UUID id) {
        if (active.match.isAlive(id) && active.isRunning()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                knockOut(active, player);
            } else {
                active.match.eliminate(
                        id, active.credit.killer(id, clock.instant()).orElse(null));
                if (active.match.phase() == Match.Phase.ENDED) {
                    finish(active);
                }
            }
        } else if (active.match.phase() == Match.Phase.COUNTDOWN) {
            active.match.eliminate(id, null);
        }
        if (!active.match.isAlive(id)) {
            active.match.forget(id);
        }
        matchOf.remove(id);
    }

    private void knockOut(ActiveMatch active, Player player) {
        UUID id = player.getUniqueId();
        Optional<UUID> killer = active.credit.killer(id, clock.instant());
        OptionalInt place = active.match.eliminate(id, killer.orElse(null));
        if (place.isEmpty()) {
            return;
        }
        active.credit.forget(id);
        int left = active.match.alive().size();
        Optional<Player> killerPlayer = killer.map(plugin.getServer()::getPlayer);
        Component line = killerPlayer
                .map(k -> MiniText.text(
                        "<red><name></red> <gray>odpada przez <white><killer></white>. Zostało: <white><left>",
                        Placeholder.unparsed("name", player.getName()),
                        Placeholder.unparsed("killer", k.getName()),
                        Placeholder.unparsed("left", String.valueOf(left))))
                .orElseGet(() -> MiniText.text(
                        "<red><name></red> <gray>odpada. Zostało: <white><left>",
                        Placeholder.unparsed("name", player.getName()),
                        Placeholder.unparsed("left", String.valueOf(left))));
        broadcast(active, line);

        player.setAllowFlight(false);
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(active.spectatorPoint());
        player.showTitle(Title.title(
                MiniText.text("<red>Odpadasz!"),
                MiniText.text(
                        "<gray>Miejsce <white><place>",
                        Placeholder.unparsed("place", String.valueOf(place.getAsInt())))));
        player.sendMessage(MiniText.text(
                "<click:run_command:'/graj <game>'><green>[Zagraj ponownie]</green></click>  "
                        + "<click:run_command:'/opusc'><gray>[Wróć do huba]</gray></click>",
                Placeholder.unparsed("game", active.type.id())));

        if (active.match.phase() == Match.Phase.ENDED) {
            finish(active);
        }
    }

    /** Announces the winner and pays everyone who is still online. */
    private void finish(ActiveMatch active) {
        active.endingLeft = ActiveMatch.ENDING_SECONDS;
        active.match.winner().ifPresent(winnerId -> {
            Player winner = plugin.getServer().getPlayer(winnerId);
            String name = winner == null ? "?" : winner.getName();
            for (Player player : online(active)) {
                player.showTitle(Title.title(
                        MiniText.text("<gold><name>", Placeholder.unparsed("name", name)),
                        MiniText.text("<yellow>wygrywa!")));
            }
            if (winner != null) {
                winner.setAllowFlight(false);
                winner.getInventory().clear();
            }
        });
        for (Map.Entry<UUID, MatchResult> result : active.match.results().entrySet()) {
            pay(active, result.getKey(), result.getValue());
        }
    }

    private void pay(ActiveMatch active, UUID id, MatchResult result) {
        Player player = plugin.getServer().getPlayer(id);
        if (player == null || profiles.profile(id).isEmpty()) {
            return;
        }
        String mode = active.type.id();
        Reward reward = MatchRewards.rewardFor(result);
        profiles.grant(id, reward);
        profiles.recordStat(id, Stats.gamesPlayed(mode), 1);
        profiles.recordStat(id, Stats.skillPoints(mode), SkillRating.matchPoints(result.players(), result.placement()));
        if (result.won()) {
            profiles.recordStat(id, Stats.wins(mode), 1);
        }
        if (result.kills() > 0) {
            profiles.recordStat(id, Stats.kills(mode), result.kills());
        }
        if (!active.match.isAlive(id)) {
            profiles.recordStat(id, Stats.deaths(mode), 1);
        }
        player.sendMessage(MiniText.text(
                "<gray>Miejsce <white><place>/<players></white>. Nagroda: <aqua>+<xp> XP</aqua>, <yellow>+<coins> monet",
                Placeholder.unparsed("place", String.valueOf(result.placement())),
                Placeholder.unparsed("players", String.valueOf(result.players())),
                Placeholder.unparsed("xp", String.valueOf(reward.xp())),
                Placeholder.unparsed("coins", String.valueOf(reward.coins()))));
    }

    /** Restores the arena, sends everyone back and frees the arena for the next match. */
    private void cleanUp(ActiveMatch active) {
        for (Player player : online(active)) {
            matchOf.remove(player.getUniqueId());
            sendToLobby(player);
        }
        for (UUID id : active.match.roster()) {
            matchOf.remove(id, active);
        }
        active.changes.restore(active.world);
        active.fading.clear();
        matches.remove(active);
        pool.release(active.arena.id());
        arenas.get(active.arena.id()).ifPresentOrElse(this::refreshArena, () -> forgetArena(active.arena));
    }

    private void showQueueStatus(Instant now) {
        for (Map.Entry<UUID, GameType> queued : queuedFor.entrySet()) {
            Player player = plugin.getServer().getPlayer(queued.getKey());
            Matchmaker queue = queues.get(queued.getValue());
            if (player == null) {
                continue;
            }
            long waited = queue.entry(player.getUniqueId())
                    .map(entry -> entry.waited(now).toSeconds())
                    .orElse(0L);
            player.sendActionBar(MiniText.text(
                    "<green><game></green> <gray>• w kolejce: <white><size></white> • czekasz <white><time></white> •"
                            + " /opusc",
                    Placeholder.unparsed("game", queued.getValue().displayName()),
                    Placeholder.unparsed("size", String.valueOf(queue.size())),
                    Placeholder.unparsed("time", MiniText.clock(waited))));
        }
    }

    private List<Player> online(ActiveMatch active) {
        List<Player> players = new ArrayList<>();
        for (UUID id : active.match.roster()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && matchOf.get(id) == active) {
                players.add(player);
            }
        }
        return players;
    }

    private void broadcast(ActiveMatch active, Component message) {
        online(active).forEach(player -> player.sendMessage(message));
    }

    private static void preparePlayer(Player player, GameMode mode) {
        player.getInventory().clear();
        player.setGameMode(mode);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFoodLevel(20);
        player.clearActivePotionEffects();
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private static void equip(GameType type, Player player) {
        switch (type) {
            case SPLEEF -> {
                player.setGameMode(GameMode.SURVIVAL);
                ItemStack shovel = ItemStack.of(Material.DIAMOND_SHOVEL);
                shovel.editMeta(meta -> {
                    meta.customName(MiniText.item("<aqua>Łopata do Spleefa"));
                    meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                    meta.setUnbreakable(true);
                });
                player.getInventory().setItem(0, shovel);
            }
            case TNT_RUN -> player.setAllowFlight(true);
        }
    }

    private void sendToLobby(Player player) {
        preparePlayer(player, GameMode.ADVENTURE);
        player.sendActionBar(Component.empty());
        LobbyService lobby = plugin.getServer().getServicesManager().load(LobbyService.class);
        if (lobby != null) {
            lobby.sendToLobby(player.getUniqueId());
        } else {
            Location spawn = plugin.getServer().getWorlds().getFirst().getSpawnLocation();
            player.teleport(spawn);
        }
    }

    private static Title.Times times() {
        return Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200));
    }
}
