package pl.mchosting.server.minigry;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.Stats;

/** {@code /graj}, {@code /opusc} and {@code /minigryadmin}. */
final class MinigryCommands {

    private static final String ARENA = "arena";
    private static final String GAME = "gra";
    private static final String KIT = "zestaw";

    private final MinigameEngine engine;
    private final ArenaStore arenas;
    /** First corner of a region, per arena, until staff set the second one. */
    private final Map<String, BlockPos> firstCorner = new HashMap<>();

    MinigryCommands(MinigameEngine engine, ArenaStore arenas) {
        this.engine = engine;
        this.arenas = arenas;
    }

    LiteralCommandNode<CommandSourceStack> play() {
        return Commands.literal("graj")
                .requires(source -> source.getExecutor() instanceof Player)
                .then(Commands.argument(GAME, StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (GameType type : GameType.values()) {
                                builder.suggest(type.id());
                            }
                            builder.suggest(Stats.PARKOUR);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player player = (Player) context.getSource().getExecutor();
                            engine.join(player.getUniqueId(), StringArgumentType.getString(context, GAME));
                            return Command.SINGLE_SUCCESS;
                        })
                        // /graj duels <zestaw> [ranking]
                        .then(Commands.argument(KIT, StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (DuelKit kit : DuelKit.values()) {
                                        builder.suggest(kit.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> joinDuel(context, false))
                                .then(Commands.literal("ranking").executes(context -> joinDuel(context, true)))))
                .build();
    }

    private int joinDuel(CommandContext<CommandSourceStack> context, boolean ranked) {
        Player player = (Player) context.getSource().getExecutor();
        String game = StringArgumentType.getString(context, GAME);
        String kitId = StringArgumentType.getString(context, KIT);
        DuelKit kit = DuelKit.byId(kitId).orElse(null);
        if (!game.equals(GameType.DUELS.id()) || kit == null) {
            player.sendMessage(MiniText.text(
                    "<red>Nie ma takiego zestawu. Zestawy: <white><kits>",
                    Placeholder.unparsed(
                            "kits",
                            String.join(
                                    ", ",
                                    Arrays.stream(DuelKit.values())
                                            .map(DuelKit::id)
                                            .toList()))));
            return 0;
        }
        engine.joinQueue(player, QueueId.duel(kit, ranked));
        return Command.SINGLE_SUCCESS;
    }

    LiteralCommandNode<CommandSourceStack> leave() {
        return Commands.literal("opusc")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    engine.leaveEverything((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    LiteralCommandNode<CommandSourceStack> admin() {
        return Commands.literal("minigryadmin")
                .requires(source -> source.getSender().hasPermission(MinigryPermissions.ADMIN))
                .then(Commands.literal("swiat")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .then(Commands.argument("nazwa", StringArgumentType.word())
                                .executes(this::createWorld)))
                .then(Commands.literal(ARENA)
                        .then(Commands.literal("lista").executes(this::list))
                        .then(Commands.literal("utworz")
                                .requires(source -> source.getExecutor() instanceof Player)
                                .then(Commands.argument(ARENA, StringArgumentType.word())
                                        .then(Commands.argument(GAME, StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (GameType type : GameType.values()) {
                                                        builder.suggest(type.id());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(this::create))))
                        .then(edit("spawn", true, (arena, player) -> {
                            Location at = player.getLocation();
                            return arena.withSpawnAdded(
                                    new SpawnPoint(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()));
                        }))
                        .then(edit("czyscspawny", false, (arena, player) -> arena.withoutSpawns()))
                        .then(edit("rog1", true, (arena, player) -> {
                            firstCorner.put(arena.id(), blockAt(player));
                            player.sendMessage(
                                    MiniText.text(
                                            "<gray>Pierwszy róg zapisany. Stań w przeciwległym rogu i wpisz <white>rog2</white>."));
                            return arena;
                        }))
                        .then(edit("rog2", true, (arena, player) -> {
                            BlockPos first = firstCorner.remove(arena.id());
                            if (first == null) {
                                player.sendMessage(MiniText.text("<red>Najpierw ustaw pierwszy róg: rog1."));
                                return arena;
                            }
                            return arena.withRegion(BlockBox.between(first, blockAt(player)));
                        }))
                        .then(edit(
                                "wysokosc",
                                true,
                                (arena, player) -> arena.withEliminationY(
                                        player.getLocation().getBlockY())))
                        .then(edit("wlacz", false, (arena, player) -> arena.withEnabled(true)))
                        .then(edit("wylacz", false, (arena, player) -> arena.withEnabled(false)))
                        .then(Commands.literal("usun").then(arenaArgument().executes(this::remove))))
                .build();
    }

    /**
     * {@code /minigryadmin arena <action> <id>}: changes one arena. Actions that use the player's
     * position only work in the arena's world.
     */
    private LiteralArgumentBuilder<CommandSourceStack> edit(
            String action, boolean usesPosition, BiFunction<ArenaConfig, Player, ArenaConfig> change) {
        return Commands.literal(action)
                .requires(source -> source.getExecutor() instanceof Player)
                .then(arenaArgument().executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    String id = StringArgumentType.getString(context, ARENA);
                    ArenaConfig arena = arenas.get(id).orElse(null);
                    if (arena == null) {
                        player.sendMessage(MiniText.text(
                                "<red>Nie ma areny <white><id></white>.", Placeholder.unparsed("id", id)));
                        return 0;
                    }
                    if (engine.isArenaBusy(id)) {
                        player.sendMessage(MiniText.text("<red>Na tej arenie trwa mecz. Spróbuj po jego końcu."));
                        return 0;
                    }
                    if (usesPosition && !player.getWorld().getName().equals(arena.world())) {
                        player.sendMessage(MiniText.text(
                                "<red>Ta arena jest w świecie <white><world></white>.",
                                Placeholder.unparsed("world", arena.world())));
                        return 0;
                    }
                    ArenaConfig changed = change.apply(arena, player);
                    if (!changed.equals(arena)) {
                        arenas.put(changed);
                        engine.refreshArena(changed);
                        describe(player, changed);
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> arenaArgument() {
        return Commands.argument(ARENA, StringArgumentType.word()).suggests((context, builder) -> {
            arenas.all().forEach(arena -> builder.suggest(arena.id()));
            return builder.buildFuture();
        });
    }

    private int create(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String id = StringArgumentType.getString(context, ARENA);
        String gameId = StringArgumentType.getString(context, GAME);
        GameType type = GameType.byId(gameId).orElse(null);
        if (type == null) {
            player.sendMessage(
                    MiniText.text("<red>Nie ma gry <white><game></white>.", Placeholder.unparsed("game", gameId)));
            return 0;
        }
        if (!ArenaConfig.ID.matcher(id).matches()) {
            player.sendMessage(MiniText.text("<red>Nazwa areny: małe litery, cyfry i _, np. <white>spleef1</white>."));
            return 0;
        }
        if (arenas.get(id).isPresent()) {
            player.sendMessage(
                    MiniText.text("<red>Arena <white><id></white> już jest.", Placeholder.unparsed("id", id)));
            return 0;
        }
        World world = player.getWorld();
        if (world.equals(player.getServer().getWorlds().getFirst())) {
            player.sendMessage(
                    MiniText.text(
                            "<red>Areny muszą być w osobnym świecie, nie w hubie. Utwórz go: <white>/minigryadmin swiat minigry"));
            return 0;
        }
        ArenaConfig arena = ArenaConfig.create(id, type, world.getName());
        arenas.put(arena);
        engine.refreshArena(arena);
        describe(player, arena);
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = StringArgumentType.getString(context, ARENA);
        ArenaConfig arena = arenas.get(id).orElse(null);
        if (arena == null || engine.isArenaBusy(id)) {
            sender.sendMessage(MiniText.text("<red>Nie można usunąć tej areny teraz."));
            return 0;
        }
        engine.forgetArena(arena);
        arenas.remove(id);
        sender.sendMessage(MiniText.text("<gray>Usunięto arenę <white><id></white>.", Placeholder.unparsed("id", id)));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (arenas.all().isEmpty()) {
            sender.sendMessage(
                    MiniText.text("<gray>Nie ma jeszcze aren. Zacznij od <white>/minigryadmin swiat minigry"));
        }
        for (ArenaConfig arena : arenas.all()) {
            describe(sender, arena);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Creates (or loads) an empty world for building arenas and takes the player there. */
    private int createWorld(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String name = StringArgumentType.getString(context, "nazwa");
        if (!ArenaConfig.ID.matcher(name).matches()) {
            player.sendMessage(MiniText.text("<red>Nazwa świata: małe litery, cyfry i _."));
            return 0;
        }
        World world = player.getServer().getWorld(name);
        if (world == null) {
            // Bukkit only creates worlds on the main thread; staff do this rarely, before players come.
            world = new WorldCreator(name).generator(new VoidGenerator()).createWorld();
        }
        if (world == null) {
            player.sendMessage(MiniText.text("<red>Nie udało się utworzyć świata."));
            return 0;
        }
        arenas.addWorld(name);
        world.getBlockAt(0, 64, 0).setType(Material.GLASS);
        world.setSpawnLocation(0, 65, 0);
        player.teleport(new Location(world, 0.5, 65, 0.5));
        player.sendMessage(MiniText.text(
                "<green>Jesteś w pustym świecie <white><name></white>. Zbuduj arenę i wpisz"
                        + " <white>/minigryadmin arena utworz <nazwa> <gra></white>.",
                Placeholder.unparsed("name", name)));
        return Command.SINGLE_SUCCESS;
    }

    private void describe(CommandSender sender, ArenaConfig arena) {
        List<String> problems = arena.problems();
        String state;
        if (engine.isArenaBusy(arena.id())) {
            state = "<gold>trwa mecz";
        } else if (arena.isReady()) {
            state = "<green>gotowa";
        } else if (problems.isEmpty()) {
            state = "<yellow>wyłączona (/minigryadmin arena wlacz " + arena.id() + ")";
        } else {
            state = "<red>niegotowa";
        }
        sender.sendMessage(MiniText.text(
                "<white><id></white> <gray>(<game>, świat <world>, spawnów: <spawns>):</gray> " + state,
                Placeholder.unparsed("id", arena.id()),
                Placeholder.unparsed("game", arena.type().displayName()),
                Placeholder.unparsed("world", arena.world()),
                Placeholder.unparsed("spawns", String.valueOf(arena.spawns().size()))));
        for (String problem : problems) {
            sender.sendMessage(MiniText.text("<gray>  • <problem>", Placeholder.unparsed("problem", problem)));
        }
    }

    private static BlockPos blockAt(Player player) {
        Location at = player.getLocation();
        return new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }
}
