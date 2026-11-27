package pl.mchosting.server.minigry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;
import pl.mchosting.server.api.Reward;
import pl.mchosting.server.api.StatKey;
import pl.mchosting.server.api.Stats;

/**
 * Parkour runs: one player, one course, no queue. Players stay in their minigame queue while they
 * run, so parkour is also something to do while waiting. The rules live in {@link ParkourRun};
 * this class moves players, shows the clock and saves records. Main thread only.
 */
final class ParkourManager {

    /** Hotbar slots of the run items. */
    static final int SLOT_CHECKPOINT = 0;

    static final int SLOT_RESTART = 4;
    static final int SLOT_LEAVE = 8;

    private static final Duration WARNING_GAP = Duration.ofSeconds(3);
    private static final Sound CHECKPOINT =
            Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1f, 1.2f);
    private static final Sound FINISH =
            Sound.sound(Key.key("ui.toast.challenge_complete"), Sound.Source.MASTER, 1f, 1f);
    private static final Sound START = Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1f, 2f);

    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final ParkourStore store;
    private final Lobby lobby;
    private final Clock clock;
    private final Map<UUID, ParkourRun> runs = new HashMap<>();
    private final Cooldown warnings;

    ParkourManager(JavaPlugin plugin, ProfileService profiles, ParkourStore store, Lobby lobby, Clock clock) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.store = store;
        this.lobby = lobby;
        this.clock = clock;
        this.warnings = new Cooldown(WARNING_GAP, clock);
    }

    ParkourStore store() {
        return store;
    }

    /** Courses players can run now: set up, switched on and in a loaded world. */
    List<ParkourCourse> readyCourses() {
        List<ParkourCourse> ready = new ArrayList<>();
        for (ParkourCourse course : store.all()) {
            if (course.isReady() && plugin.getServer().getWorld(course.world()) != null) {
                ready.add(course);
            }
        }
        return ready;
    }

    boolean hasCourses() {
        return !readyCourses().isEmpty();
    }

    boolean isRunning(UUID playerId) {
        return runs.containsKey(playerId);
    }

    Optional<ParkourRun> runOf(UUID playerId) {
        return Optional.ofNullable(runs.get(playerId));
    }

    int runners() {
        return runs.size();
    }

    /** The course menu: easiest first, with the player's best time and the course record. */
    void openMenu(Player player, Consumer<ParkourCourse> onPick) {
        List<ParkourCourse> courses = readyCourses();
        int rows = Math.clamp((courses.size() + 8) / 9, 1, 6);
        ChoiceMenu menu = new ChoiceMenu(rows, MiniText.text("<dark_green>Parkour: wybierz trasę"));
        Optional<PlayerProfile> profile = profiles.profile(player.getUniqueId());
        for (int i = 0; i < Math.min(courses.size(), rows * 9); i++) {
            ParkourCourse course = courses.get(i);
            menu.set(i, icon(course, profile), clicked -> onPick.accept(course));
        }
        menu.open(player);
    }

    /** Takes the player to the start of {@code course}; the clock starts when they leave it. */
    void start(Player player, ParkourCourse course) {
        World world = plugin.getServer().getWorld(course.world());
        if (world == null || !course.isReady()) {
            player.sendMessage(MiniText.text("<red>Ta trasa jest teraz wyłączona."));
            return;
        }
        player.closeInventory();
        ParkourRun run = new ParkourRun(course);
        runs.put(player.getUniqueId(), run);
        Lobby.prepare(player, GameMode.ADVENTURE);
        giveItems(player);
        player.teleport(location(world, course.start().spawn()));
        player.showTitle(Title.title(
                MiniText.text(course.level().color() + "<name>", Placeholder.unparsed("name", course.name())),
                MiniText.text(
                        "<gray><level> • punktów kontrolnych: <white><points>",
                        Placeholder.unparsed("level", course.level().displayName()),
                        Placeholder.unparsed(
                                "points", String.valueOf(course.checkpoints().size())))));
        List<Leaderboard.Entry> board = store.board(course.id()).entries();
        if (!board.isEmpty()) {
            Leaderboard.Entry best = board.getFirst();
            player.sendMessage(MiniText.text(
                    "<gray>Rekord trasy: <gold><time></gold> (<name>)",
                    Placeholder.unparsed("time", RaceTime.format(best.millis())),
                    Placeholder.unparsed("name", best.name())));
        }
        bestTime(player.getUniqueId(), course)
                .ifPresent(millis -> player.sendMessage(MiniText.text(
                        "<gray>Twój najlepszy czas: <white><time>",
                        Placeholder.unparsed("time", RaceTime.format(millis)))));
    }

    /** Ends the run without sending the player anywhere (a match took them, or they left). */
    void stop(Player player) {
        if (runs.remove(player.getUniqueId()) != null) {
            player.getInventory().clear();
            player.sendActionBar(Component.empty());
        }
    }

    void quit(UUID playerId) {
        runs.remove(playerId);
        warnings.forget(playerId);
    }

    /** Server stop: everyone back to the hub. */
    void stopAll() {
        for (UUID id : List.copyOf(runs.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            runs.remove(id);
            if (player != null) {
                lobby.send(player);
            }
        }
    }

    /** Leaves parkour for the hub ({@code /opusc}, the bed item). */
    void leave(Player player) {
        stop(player);
        lobby.send(player);
    }

    /** A runner moved: start the clock, reach checkpoints, finish, or go back after a fall. */
    void moved(Player player, Location to) {
        ParkourRun run = runs.get(player.getUniqueId());
        if (run == null || !to.getWorld().getName().equals(run.course().world())) {
            return;
        }
        if (run.fellAt(to.getY())) {
            backToCheckpoint(player, run);
            return;
        }
        BlockPos feet = new BlockPos(to.getBlockX(), to.getBlockY(), to.getBlockZ());
        BlockPos standing = null;
        boolean onTop = to.getY() - Math.floor(to.getY()) < 0.01;
        BlockPos below = ParkourCourse.standingBlock(to.getX(), to.getY(), to.getZ());
        if (onTop && to.getWorld().getBlockAt(below.x(), below.y(), below.z()).isSolid()) {
            standing = below;
        }
        Instant now = clock.instant();
        switch (run.moved(feet, standing, now)) {
            case NOTHING, RESET -> {}
            case STARTED -> player.playSound(START);
            case CHECKPOINT -> {
                player.playSound(CHECKPOINT);
                player.showTitle(Title.title(
                        Component.empty(),
                        MiniText.text(
                                "<green>Punkt kontrolny <white><reached>/<total></white> <gray>• <time>",
                                Placeholder.unparsed("reached", String.valueOf(run.reached())),
                                Placeholder.unparsed(
                                        "total",
                                        String.valueOf(
                                                run.course().checkpoints().size())),
                                Placeholder.unparsed(
                                        "time", RaceTime.format(run.elapsed(now).toMillis()))),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            }
            case SKIPPED, FINISH_MISSING_CHECKPOINTS -> {
                if (warnings.tryUse(player.getUniqueId())) {
                    player.sendMessage(MiniText.text(
                            "<yellow>Pominięto punkt kontrolny <white><next></white>. Wróć po niego, inaczej meta się"
                                    + " nie liczy.",
                            Placeholder.unparsed("next", String.valueOf(run.reached() + 1))));
                }
            }
            case FINISHED -> finish(player, run, now);
        }
    }

    /** Back to the last checkpoint (or start), for a fall or the item. */
    void backToCheckpoint(Player player, ParkourRun run) {
        World world = plugin.getServer().getWorld(run.course().world());
        if (world == null) {
            leave(player);
            return;
        }
        if (run.finished()) {
            BlockPos finish = run.course().finish();
            player.teleport(new Location(world, finish.x() + 0.5, finish.y() + 1, finish.z() + 0.5));
        } else {
            player.teleport(location(world, run.fall().spawn()));
        }
        player.setFallDistance(0);
        player.setFireTicks(0);
    }

    void restart(Player player, ParkourRun run) {
        World world = plugin.getServer().getWorld(run.course().world());
        if (world == null) {
            leave(player);
            return;
        }
        run.restart();
        player.teleport(location(world, run.course().start().spawn()));
        player.setFallDistance(0);
        player.sendActionBar(MiniText.text("<gray>Od nowa! Czas ruszy, gdy zejdziesz ze startu."));
    }

    /** One of the run items in the hotbar was used. */
    void useItem(Player player, int slot) {
        ParkourRun run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        switch (slot) {
            case SLOT_CHECKPOINT -> backToCheckpoint(player, run);
            case SLOT_RESTART -> restart(player, run);
            case SLOT_LEAVE -> leave(player);
            default -> {}
        }
    }

    /** Every 2 ticks: the clock and progress on the action bar. */
    void tick(Function<UUID, Optional<QueueId>> queueOf) {
        Instant now = clock.instant();
        for (Map.Entry<UUID, ParkourRun> entry : runs.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            ParkourRun run = entry.getValue();
            String queue = queueOf.apply(entry.getKey())
                    .map(id -> " <gray>• kolejka: <white>" + id.displayName())
                    .orElse("");
            String state;
            if (run.finished()) {
                state = "<gold>Meta! <white>" + RaceTime.format(run.elapsed(now).toMillis());
            } else if (!run.started()) {
                state = "<gray>Zejdź ze startu, aby włączyć czas";
            } else {
                state = "<white>" + RaceTime.formatShort(run.elapsed(now).toMillis()) + " <gray>• punkt <white>"
                        + run.reached() + "/" + run.course().checkpoints().size();
            }
            player.sendActionBar(MiniText.text(
                    "<green><name></green> <gray>•</gray> " + state + "<queue>",
                    Placeholder.unparsed("name", run.course().name()),
                    Placeholder.parsed("queue", queue)));
        }
    }

    /** {@code /parkour top}: the record board of one course. */
    void showTop(CommandSender sender, ParkourCourse course) {
        List<Leaderboard.Entry> board = store.board(course.id()).entries();
        sender.sendMessage(MiniText.text(
                "<gold>Rekordy trasy <white><name></white>:", Placeholder.unparsed("name", course.name())));
        if (board.isEmpty()) {
            sender.sendMessage(MiniText.text("<gray>Nikt jeszcze nie ukończył tej trasy. Bądź pierwszy!"));
        }
        for (int i = 0; i < board.size(); i++) {
            Leaderboard.Entry entry = board.get(i);
            sender.sendMessage(MiniText.text(
                    "<gray><place>. <white><name></white> <yellow><time>",
                    Placeholder.unparsed("place", String.valueOf(i + 1)),
                    Placeholder.unparsed("name", entry.name()),
                    Placeholder.unparsed("time", RaceTime.format(entry.millis()))));
        }
    }

    private void finish(Player player, ParkourRun run, Instant now) {
        UUID id = player.getUniqueId();
        ParkourCourse course = run.course();
        long millis = Math.max(1, run.elapsed(now).toMillis());
        Optional<Long> previous = bestTime(id, course);
        player.playSound(FINISH);
        player.showTitle(Title.title(
                MiniText.text("<gold>Meta!"),
                MiniText.text(
                        "<white><time> <gray>• upadki: <white><falls>",
                        Placeholder.unparsed("time", RaceTime.format(millis)),
                        Placeholder.unparsed("falls", String.valueOf(run.falls())))));

        if (profiles.profile(id).isPresent()) {
            StatKey best = Stats.bestTimeMillis(course.id());
            profiles.recordStat(id, best, millis);
            profiles.recordStat(id, Stats.gamesPlayed(Stats.PARKOUR), 1);
            if (previous.isEmpty()) {
                // "Wins" of parkour are courses finished at least once.
                profiles.recordStat(id, Stats.wins(Stats.PARKOUR), 1);
                Reward reward = ParkourRewards.firstFinish(course.difficulty());
                profiles.grant(id, reward);
                player.sendMessage(MiniText.text(
                        "<green>Pierwsze ukończenie trasy! Nagroda: <aqua>+<xp> XP</aqua>, <yellow>+<coins> monet",
                        Placeholder.unparsed("xp", String.valueOf(reward.xp())),
                        Placeholder.unparsed("coins", String.valueOf(reward.coins()))));
            } else if (millis < previous.get()) {
                player.sendMessage(MiniText.text(
                        "<green>Nowy rekord osobisty! <gray>Poprzedni: <white><time>",
                        Placeholder.unparsed("time", RaceTime.format(previous.get()))));
            }
        }

        int place = store.board(course.id())
                .submit(new Leaderboard.Entry(id, player.getName(), millis, now.toEpochMilli()));
        if (place > 0) {
            store.save();
        }
        if (place == 1) {
            plugin.getServer()
                    .broadcast(MiniText.text(
                            "<gold>★ <white><name></white> ustanawia rekord trasy <white><course></white>: <yellow><time>",
                            Placeholder.unparsed("name", player.getName()),
                            Placeholder.unparsed("course", course.name()),
                            Placeholder.unparsed("time", RaceTime.format(millis))));
        } else if (place > 1) {
            player.sendMessage(MiniText.text(
                    "<yellow>Miejsce <white><place></white> w rekordach trasy!",
                    Placeholder.unparsed("place", String.valueOf(place))));
        }
        // Course ids only have letters, digits and _, so they are safe inside the click tag.
        player.sendMessage(MiniText.text("<click:run_command:'/parkour " + course.id()
                + "'><green>[Jeszcze raz]</green></click>  "
                + "<click:run_command:'/parkour'><aqua>[Inne trasy]</aqua></click>  "
                + "<click:run_command:'/opusc'><gray>[Wróć do huba]</gray></click>"));
    }

    private Optional<Long> bestTime(UUID playerId, ParkourCourse course) {
        StatKey key = Stats.bestTimeMillis(course.id());
        return profiles.profile(playerId).filter(p -> p.hasStat(key)).map(p -> p.stat(key));
    }

    private ItemStack icon(ParkourCourse course, Optional<PlayerProfile> profile) {
        ItemStack stack = ItemStack.of(blockFor(course.level()));
        StatKey key = Stats.bestTimeMillis(course.id());
        Optional<Long> best = profile.filter(p -> p.hasStat(key)).map(p -> p.stat(key));
        List<Leaderboard.Entry> board = store.board(course.id()).entries();
        stack.editMeta(meta -> {
            meta.customName(
                    MiniText.item(course.level().color() + "<name>", Placeholder.unparsed("name", course.name())));
            List<Component> lore = new ArrayList<>();
            lore.add(MiniText.item(
                    "<gray>Trudność: " + course.level().color() + "<level> <gray>(<difficulty>/10)",
                    Placeholder.unparsed("level", course.level().displayName()),
                    Placeholder.unparsed("difficulty", String.valueOf(course.difficulty()))));
            lore.add(MiniText.item(
                    "<gray>Punkty kontrolne: <white><points>",
                    Placeholder.unparsed(
                            "points", String.valueOf(course.checkpoints().size()))));
            lore.add(Component.empty());
            if (board.isEmpty()) {
                lore.add(MiniText.item("<gray>Rekord trasy: <white>brak, bądź pierwszy!"));
            } else {
                lore.add(MiniText.item(
                        "<gray>Rekord trasy: <gold><time></gold> <gray>(<name>)",
                        Placeholder.unparsed(
                                "time", RaceTime.format(board.getFirst().millis())),
                        Placeholder.unparsed("name", board.getFirst().name())));
            }
            if (best.isPresent()) {
                lore.add(MiniText.item(
                        "<gray>Twój czas: <white><time> <green>✔",
                        Placeholder.unparsed("time", RaceTime.format(best.get()))));
            } else {
                Reward reward = ParkourRewards.firstFinish(course.difficulty());
                lore.add(MiniText.item(
                        "<gray>Za pierwsze ukończenie: <aqua>+<xp> XP</aqua>, <yellow>+<coins> monet",
                        Placeholder.unparsed("xp", String.valueOf(reward.xp())),
                        Placeholder.unparsed("coins", String.valueOf(reward.coins()))));
            }
            lore.add(Component.empty());
            lore.add(MiniText.item("<green>Kliknij, aby zacząć"));
            meta.lore(lore);
        });
        return stack;
    }

    /** Colour of a difficulty group, for the menu and the generated jump blocks. */
    static Material blockFor(ParkourCourse.Difficulty level) {
        return switch (level) {
            case LATWA -> Material.LIME_CONCRETE;
            case SREDNIA -> Material.YELLOW_CONCRETE;
            case TRUDNA -> Material.ORANGE_CONCRETE;
            case BARDZO_TRUDNA -> Material.RED_CONCRETE;
        };
    }

    private static void giveItems(Player player) {
        player.getInventory().setItem(SLOT_CHECKPOINT, item(Material.SLIME_BALL, "<green>Wróć do punktu kontrolnego"));
        player.getInventory().setItem(SLOT_RESTART, item(Material.CLOCK, "<yellow>Zacznij od nowa"));
        player.getInventory().setItem(SLOT_LEAVE, item(Material.RED_BED, "<red>Wyjdź z parkouru"));
        player.getInventory().setHeldItemSlot(SLOT_CHECKPOINT);
    }

    private static ItemStack item(Material material, String name) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> meta.customName(MiniText.item(name + " <gray>(prawy klik)")));
        return stack;
    }

    private static Location location(World world, SpawnPoint spawn) {
        return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }
}
