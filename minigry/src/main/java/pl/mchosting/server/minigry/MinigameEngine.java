package pl.mchosting.server.minigry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
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
 * this class moves players, changes blocks, shows messages and pays rewards. Parkour has no queue,
 * so it is run by {@link ParkourManager} and only reached through here. Main thread only.
 */
final class MinigameEngine implements MinigameService {

    private static final Sound MATCH_FOUND =
            Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1f, 1.5f);
    private static final Sound TICK = Sound.sound(Key.key("block.note_block.hat"), Sound.Source.MASTER, 1f, 1f);
    private static final Sound GO = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 1f, 1f);

    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final ArenaStore arenas;
    private final Lobby lobby;
    private final ParkourManager parkour;
    private final Clock clock;
    private final ArenaPool pool = new ArenaPool();
    private final Map<QueueId, Matchmaker> queues = new LinkedHashMap<>();
    private final Map<UUID, QueueId> queuedFor = new HashMap<>();
    private final Map<UUID, ActiveMatch> matchOf = new HashMap<>();
    private final Set<ActiveMatch> matches = new LinkedHashSet<>();

    MinigameEngine(
            JavaPlugin plugin,
            ProfileService profiles,
            ArenaStore arenas,
            Lobby lobby,
            ParkourManager parkour,
            Clock clock) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.arenas = arenas;
        this.lobby = lobby;
        this.parkour = parkour;
        this.clock = clock;
        for (QueueId queue : QueueId.ALL) {
            GameType type = queue.type();
            queues.put(queue, new Matchmaker(type.minPlayers(), type.maxPlayers(), MatchmakingRules.DEFAULT));
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
        if (parkour.hasCourses()) {
            games.add(Stats.PARKOUR);
        }
        return games;
    }

    @Override
    public QueueJoin join(UUID playerId, String gameId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("player " + playerId + " is not online");
        }
        if (gameId.equals(Stats.PARKOUR)) {
            if (inMatch(player)) {
                return QueueJoin.IN_MATCH;
            }
            if (!parkour.hasCourses()) {
                player.sendMessage(MiniText.text("<yellow>Parkour jeszcze nie działa, trasy są w budowie."));
                return QueueJoin.NO_ARENA;
            }
            parkour.openMenu(player, course -> startParkour(player, course));
            return QueueJoin.MENU_OPENED;
        }
        Optional<GameType> found = GameType.byId(gameId);
        if (found.isEmpty()) {
            player.sendMessage(MiniText.text("<red>Nie ma takiej gry."));
            return QueueJoin.UNKNOWN_GAME;
        }
        GameType type = found.get();
        if (type == GameType.DUELS) {
            if (inMatch(player)) {
                return QueueJoin.IN_MATCH;
            }
            if (!pool.hasArena(type)) {
                return joinQueue(player, QueueId.duel(DuelKit.MIECZ, false));
            }
            DuelMenu.open(this, profiles, player);
            return QueueJoin.MENU_OPENED;
        }
        return joinQueue(player, QueueId.of(type));
    }

    /** Puts the player in one queue (a game, or a Duels kit), leaving any other queue first. */
    QueueJoin joinQueue(Player player, QueueId queue) {
        UUID playerId = player.getUniqueId();
        GameType type = queue.type();
        if (inMatch(player)) {
            return QueueJoin.IN_MATCH;
        }
        if (!pool.hasArena(type)) {
            player.sendMessage(MiniText.text(
                    "<yellow><game> jeszcze nie działa, areny są w budowie. Zajrzyj wkrótce!",
                    Placeholder.unparsed("game", type.displayName())));
            return QueueJoin.NO_ARENA;
        }
        QueueId previous = queuedFor.get(playerId);
        if (queue.equals(previous)) {
            player.sendMessage(MiniText.text(
                    "<yellow>Już czekasz w kolejce do <game>.", Placeholder.unparsed("game", queue.displayName())));
            return QueueJoin.ALREADY_QUEUED;
        }
        if (previous != null) {
            queues.get(previous).remove(playerId);
        }
        queues.get(queue).add(entryFor(playerId, queue));
        queuedFor.put(playerId, queue);
        player.sendMessage(MiniText.text(
                "<green>Jesteś w kolejce do <white><game></white>. Możesz chodzić po hubie albo pobiegać na"
                        + " parkourze, damy znać, gdy mecz będzie gotowy. <gray>Wyjście z kolejki: <white>/opusc",
                Placeholder.unparsed("game", queue.displayName())));
        return previous == null ? QueueJoin.JOINED : QueueJoin.SWITCHED;
    }

    /** Starts a parkour run unless the player is in a match; they stay in their queue meanwhile. */
    void startParkour(Player player, ParkourCourse course) {
        if (!inMatch(player)) {
            parkour.start(player, course);
        }
    }

    ParkourManager parkour() {
        return parkour;
    }

    /** True (and tells the player) while they play a match. */
    private boolean inMatch(Player player) {
        ActiveMatch current = matchOf.get(player.getUniqueId());
        if (current != null && current.match.isAlive(player.getUniqueId())) {
            player.sendMessage(MiniText.text("<red>Najpierw skończ obecny mecz albo wpisz <white>/opusc</white>."));
            return true;
        }
        return false;
    }

    @Override
    public boolean leave(UUID playerId) {
        QueueId queue = queuedFor.remove(playerId);
        if (queue == null) {
            return false;
        }
        queues.get(queue).remove(playerId);
        return true;
    }

    @Override
    public Optional<String> queuedGame(UUID playerId) {
        return Optional.ofNullable(queuedFor.get(playerId))
                .map(queue -> queue.type().id());
    }

    /** The queue the player waits in, for the parkour action bar. */
    Optional<QueueId> queueOf(UUID playerId) {
        return Optional.ofNullable(queuedFor.get(playerId));
    }

    @Override
    public int queued(String gameId) {
        int queued = 0;
        for (Map.Entry<QueueId, Matchmaker> queue : queues.entrySet()) {
            if (queue.getKey().type().id().equals(gameId)) {
                queued += queue.getValue().size();
            }
        }
        return queued;
    }

    /** Players waiting in one queue, for the Duels menu. */
    int queued(QueueId queue) {
        return queues.get(queue).size();
    }

    @Override
    public int playing(String gameId) {
        if (gameId.equals(Stats.PARKOUR)) {
            return parkour.runners();
        }
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
        if (parkour.isRunning(id)) {
            parkour.stop(player);
            lobby.send(player);
            player.sendMessage(
                    MiniText.text(wasQueued ? "<gray>Opuszczono parkour i kolejkę." : "<gray>Opuszczono parkour."));
        } else if (active != null) {
            removeFromMatch(active, id);
            lobby.send(player);
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
        parkour.quit(playerId);
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
        for (QueueId queue : QueueId.ALL) {
            formMatches(queue, now);
        }
        for (ActiveMatch active : List.copyOf(matches)) {
            tickMatch(active, now);
        }
        showQueueStatus(now);
    }

    /** Runs every 2 ticks: the parkour clock on the action bar. */
    void tickFast() {
        parkour.tick(this::queueOf);
    }

    /** Puts every arena back and sends players to the hub when the server stops. */
    void shutdown() {
        parkour.stopAll();
        for (ActiveMatch active : List.copyOf(matches)) {
            cleanUp(active);
        }
    }

    // ---- internals ----

    private QueueEntry entryFor(UUID playerId, QueueId queue) {
        Optional<PlayerProfile> profile = profiles.profile(playerId);
        if (queue.ranked()) {
            // Ranked duels pair players by ranking points; newcomers are those with few ranked duels.
            int points = profile.map(p -> DuelStats.rating(p, queue.kit())).orElse(EloRating.START);
            long ranked =
                    profile.map(p -> p.stat(DuelStats.rankedGames(queue.kit()))).orElse(0L);
            return new QueueEntry(playerId, points, SkillRating.isNewcomer(ranked), clock.instant());
        }
        GameType type = queue.type();
        long games = profile.map(p -> p.stat(Stats.gamesPlayed(type.id()))).orElse(0L);
        long points = profile.map(p -> p.stat(Stats.skillPoints(type.id()))).orElse(0L);
        int rating = SkillRating.rating(games, Math.min(points, games * SkillRating.MAX));
        return new QueueEntry(playerId, rating, SkillRating.isNewcomer(games), clock.instant());
    }

    private void formMatches(QueueId queueId, Instant now) {
        GameType type = queueId.type();
        Matchmaker queue = queues.get(queueId);
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
                requeue(queueId, online);
                return;
            }
            String arenaId = pool.claim(type).orElseThrow();
            ArenaConfig arena = arenas.get(arenaId).orElseThrow();
            World world = plugin.getServer().getWorld(arena.world());
            if (world == null) {
                pool.setReady(arenaId, type, false);
                pool.release(arenaId);
                requeue(queueId, online);
                continue;
            }
            start(new ActiveMatch(queueId, arena, world, online));
        }
    }

    private void requeue(QueueId queue, List<QueueEntry> entries) {
        for (QueueEntry entry : entries) {
            if (plugin.getServer().getPlayer(entry.playerId()) != null) {
                queues.get(queue).add(entry);
                queuedFor.put(entry.playerId(), queue);
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
            parkour.stop(player);
            Lobby.prepare(player, GameMode.ADVENTURE);
            player.teleport(active.spawn(index++));
            player.playSound(MATCH_FOUND);
            player.showTitle(Title.title(
                    MiniText.text("<green>Mecz znaleziony!"),
                    MiniText.text(
                            "<gray><game>, start za chwilę",
                            Placeholder.unparsed("game", active.queue.displayName()))));
        }
        if (active.type == GameType.DUELS) {
            announceOpponents(active);
        }
        plugin.getLogger()
                .info("Match of " + active.type.id() + " started in " + active.arena.id() + " with "
                        + active.match.roster().size() + " players");
    }

    private void tickMatch(ActiveMatch active, Instant now) {
        switch (active.match.phase()) {
            case COUNTDOWN -> tickCountdown(active, now);
            case RUNNING -> {
                if (Duration.between(active.startedAt, now).compareTo(active.queue.timeLimit()) >= 0) {
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
            equip(active, player);
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
        requeue(active.queue, back);
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
        // The command is built from our own ids only, never from player text.
        player.sendMessage(MiniText.text("<click:run_command:'" + active.queue.command()
                + "'><green>[Zagraj ponownie]</green></click>  "
                + "<click:run_command:'/opusc'><gray>[Wróć do huba]</gray></click>"));

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
            if (winner != null && active.type == GameType.DUELS) {
                String hearts = String.format(Locale.ROOT, "%.1f", winner.getHealth() / 2);
                broadcast(
                        active,
                        MiniText.text(
                                "<gold><name></gold> <gray>wygrywa, zostało <red><hearts> ❤",
                                Placeholder.unparsed("name", name),
                                Placeholder.unparsed("hearts", hearts)));
            }
            if (winner != null) {
                winner.setAllowFlight(false);
                winner.getInventory().clear();
            }
        });
        for (Map.Entry<UUID, MatchResult> result : active.match.results().entrySet()) {
            pay(active, result.getKey(), result.getValue());
        }
        settleRanked(active);
    }

    /** Ranking points of a ranked duel: both players must still have their accounts loaded. */
    private void settleRanked(ActiveMatch active) {
        Map<UUID, MatchResult> results = active.match.results();
        if (!active.queue.ranked() || results.size() != 2) {
            return;
        }
        DuelKit kit = active.kit();
        List<UUID> ids = new ArrayList<>(results.keySet());
        UUID first = ids.get(0);
        UUID second = ids.get(1);
        Optional<PlayerProfile> firstProfile = profiles.profile(first);
        Optional<PlayerProfile> secondProfile = profiles.profile(second);
        if (firstProfile.isEmpty() || secondProfile.isEmpty()) {
            return;
        }
        int firstPoints = DuelStats.rating(firstProfile.get(), kit);
        int secondPoints = DuelStats.rating(secondProfile.get(), kit);
        int firstPlace = results.get(first).placement();
        int secondPlace = results.get(second).placement();
        double firstScore = firstPlace < secondPlace ? 1 : firstPlace == secondPlace ? 0.5 : 0;
        rank(first, kit, firstPoints, secondPoints, firstScore, firstProfile.get());
        rank(second, kit, secondPoints, firstPoints, 1 - firstScore, secondProfile.get());
    }

    private void rank(UUID id, DuelKit kit, int points, int opponent, double score, PlayerProfile profile) {
        long rankedGames = profile.stat(DuelStats.rankedGames(kit));
        int change = EloRating.change(points, opponent, score, rankedGames);
        if (change > 0) {
            profiles.recordStat(id, DuelStats.pointsGained(kit), change);
        } else if (change < 0) {
            profiles.recordStat(id, DuelStats.pointsLost(kit), -change);
        }
        profiles.recordStat(id, DuelStats.rankedGames(kit), 1);
        if (score == 1) {
            profiles.recordStat(id, DuelStats.rankedWins(kit), 1);
        }
        Player player = plugin.getServer().getPlayer(id);
        if (player != null) {
            int now = points + change;
            DuelRank rank = DuelRank.of(now);
            player.sendMessage(MiniText.text(
                    "<gray>Ranking <kit>: <white><points></white> (<change>) • " + rank.color() + "<rank>",
                    Placeholder.unparsed("kit", kit.displayName()),
                    Placeholder.unparsed("points", String.valueOf(now)),
                    Placeholder.component(
                            "change", MiniText.text(change >= 0 ? "<green>+" + change : "<red>" + change)),
                    Placeholder.unparsed("rank", rank.displayName())));
        }
    }

    /** Tells both duelists who they fight, with ranking points in ranked duels. */
    private void announceOpponents(ActiveMatch active) {
        List<Player> players = online(active);
        if (players.size() != 2) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            Player player = players.get(i);
            Player opponent = players.get(1 - i);
            String points = "";
            if (active.queue.ranked()) {
                points = profiles.profile(opponent.getUniqueId())
                        .map(profile -> " (" + DuelStats.rating(profile, active.kit()) + " pkt)")
                        .orElse("");
            }
            player.sendMessage(MiniText.text(
                    "<gray>Przeciwnik: <white><name></white><points> • zestaw <white><kit>",
                    Placeholder.unparsed("name", opponent.getName()),
                    Placeholder.unparsed("points", points),
                    Placeholder.unparsed("kit", active.kit().displayName())));
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
            lobby.send(player);
        }
        for (UUID id : active.match.roster()) {
            matchOf.remove(id, active);
        }
        active.changes.restore(active.world);
        active.fading.clear();
        active.placed.clear();
        removeLeftovers(active);
        matches.remove(active);
        pool.release(active.arena.id());
        arenas.get(active.arena.id()).ifPresentOrElse(this::refreshArena, () -> forgetArena(active.arena));
    }

    /** Arrows, snowballs and dropped items left in the arena after the match. */
    private static void removeLeftovers(ActiveMatch active) {
        BlockBox region = active.arena.region();
        if (region == null) {
            return;
        }
        BoundingBox box = new BoundingBox(
                region.min().x(),
                region.min().y(),
                region.min().z(),
                region.max().x() + 1,
                region.max().y() + 1,
                region.max().z() + 1);
        for (Entity entity : active.world.getNearbyEntities(box)) {
            if (entity instanceof Item || entity instanceof Projectile) {
                entity.remove();
            }
        }
    }

    private void showQueueStatus(Instant now) {
        for (Map.Entry<UUID, QueueId> queued : queuedFor.entrySet()) {
            Player player = plugin.getServer().getPlayer(queued.getKey());
            Matchmaker queue = queues.get(queued.getValue());
            // Players on a parkour course see the queue on the parkour bar instead.
            if (player == null || parkour.isRunning(player.getUniqueId())) {
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

    private static void equip(ActiveMatch active, Player player) {
        switch (active.type) {
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
            case DUELS -> DuelKits.give(active.kit(), player);
        }
    }

    private static Title.Times times() {
        return Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200));
    }
}
