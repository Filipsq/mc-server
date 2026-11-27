package pl.mchosting.server.minigry;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.Stats;

/** {@code /parkour} for players and {@code /parkouradmin} for staff. */
final class ParkourCommands {

    private static final String COURSE = "trasa";
    /** Default fall height of a hand-made point, below the block the admin stands on. */
    private static final int DEFAULT_FALL_DEPTH = 6;

    private final MinigameEngine engine;
    private final ParkourManager parkour;
    private final ParkourStore store;

    ParkourCommands(MinigameEngine engine) {
        this.engine = engine;
        this.parkour = engine.parkour();
        this.store = parkour.store();
    }

    LiteralCommandNode<CommandSourceStack> play() {
        return Commands.literal("parkour")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    engine.join(player.getUniqueId(), Stats.PARKOUR);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("top").then(readyCourse().executes(context -> {
                    ParkourCourse course = course(context);
                    if (course != null) {
                        parkour.showTop(context.getSource().getSender(), course);
                    }
                    return Command.SINGLE_SUCCESS;
                })))
                .then(readyCourse().executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    ParkourCourse course = course(context);
                    if (course != null && course.isReady()) {
                        engine.startParkour(player, course);
                    } else if (course != null) {
                        player.sendMessage(
                                MiniText.text("<red>Ta trasa jest teraz wyłączona. Lista tras: <white>/parkour"));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    LiteralCommandNode<CommandSourceStack> admin() {
        return Commands.literal("parkouradmin")
                .requires(source -> source.getSender().hasPermission(MinigryPermissions.ADMIN))
                .then(Commands.literal("lista").executes(this::list))
                .then(Commands.literal("generuj")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .executes(this::generate))
                .then(Commands.literal("utworz")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .then(Commands.argument(COURSE, StringArgumentType.word())
                                .then(Commands.argument(
                                                "trudnosc",
                                                IntegerArgumentType.integer(
                                                        ParkourCourse.MIN_DIFFICULTY, ParkourCourse.MAX_DIFFICULTY))
                                        .then(Commands.argument("nazwa", StringArgumentType.greedyString())
                                                .executes(this::create)))))
                .then(edit("start", true, (course, player) -> course.withStart(pointHere(player))))
                .then(edit("punkt", true, (course, player) -> course.withCheckpointAdded(pointHere(player))))
                .then(edit("czyscpunkty", false, (course, player) -> course.withoutCheckpoints()))
                .then(edit("meta", true, (course, player) -> {
                    Location at = player.getLocation();
                    return course.withFinish(ParkourCourse.standingBlock(at.getX(), at.getY(), at.getZ()));
                }))
                .then(edit("wysokosc", true, this::setFallHeight))
                .then(edit("wlacz", false, (course, player) -> course.withEnabled(true)))
                .then(edit("wylacz", false, (course, player) -> course.withEnabled(false)))
                .then(Commands.literal("usun").then(anyCourse().executes(this::remove)))
                .build();
    }

    /** {@code /parkouradmin <action> <course>}: changes one course. */
    private LiteralArgumentBuilder<CommandSourceStack> edit(
            String action, boolean usesPosition, BiFunction<ParkourCourse, Player, ParkourCourse> change) {
        return Commands.literal(action)
                .requires(source -> source.getExecutor() instanceof Player)
                .then(anyCourse().executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    ParkourCourse course = course(context);
                    if (course == null) {
                        return 0;
                    }
                    if (usesPosition && !player.getWorld().getName().equals(course.world())) {
                        player.sendMessage(MiniText.text(
                                "<red>Ta trasa jest w świecie <white><world></white>.",
                                Placeholder.unparsed("world", course.world())));
                        return 0;
                    }
                    ParkourCourse changed;
                    try {
                        changed = change.apply(course, player);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(MiniText.text(
                                "<red>Nie da się: <reason>", Placeholder.unparsed("reason", e.getMessage())));
                        return 0;
                    }
                    if (!changed.equals(course)) {
                        store.put(changed);
                        describe(player, changed);
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** {@code wysokosc}: the last point added (or the start) sends players back below this height. */
    private ParkourCourse setFallHeight(ParkourCourse course, Player player) {
        int y = player.getLocation().getBlockY();
        Checkpoint lastPoint = course.checkpoints().isEmpty()
                ? course.start()
                : course.checkpoints().getLast();
        if (lastPoint == null) {
            throw new IllegalArgumentException("najpierw ustaw start");
        }
        if (y >= lastPoint.spawn().y()) {
            throw new IllegalArgumentException("stań niżej niż ostatni punkt");
        }
        if (course.checkpoints().isEmpty()) {
            return course.withStart(new Checkpoint(course.start().spawn(), y));
        }
        List<Checkpoint> points = new ArrayList<>(course.checkpoints());
        Checkpoint last = points.removeLast();
        ParkourCourse without = course.withoutCheckpoints();
        for (Checkpoint point : points) {
            without = without.withCheckpointAdded(point);
        }
        return without.withCheckpointAdded(new Checkpoint(last.spawn(), y));
    }

    /** Builds the ten default courses in the admin's world and saves them, switched on. */
    private int generate(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        World world = player.getWorld();
        if (!engine.isArenaWorld(world)
                || world.equals(player.getServer().getWorlds().getFirst())) {
            player.sendMessage(MiniText.text(
                    "<red>Trasy budujemy w osobnym świecie. Utwórz go: <white>/minigryadmin swiat parkour</white>, a"
                            + " potem wpisz tam <white>/parkouradmin generuj</white>."));
            return 0;
        }
        int blocks = 0;
        for (DefaultCourses.Generated generated : DefaultCourses.generate(world.getName())) {
            ParkourCourse course = generated.course();
            Material jump = ParkourManager.blockFor(course.level());
            for (CourseGenerator.Piece piece : generated.blueprint().pieces()) {
                Material material = switch (piece.kind()) {
                    case START -> Material.EMERALD_BLOCK;
                    case CHECKPOINT -> Material.GOLD_BLOCK;
                    case FINISH -> Material.DIAMOND_BLOCK;
                    case JUMP -> jump;
                };
                world.getBlockAt(piece.pos().x(), piece.pos().y(), piece.pos().z())
                        .setType(material, false);
                blocks++;
            }
            store.put(course);
        }
        player.sendMessage(MiniText.text(
                "<green>Gotowe: <white><courses></white> tras, <white><blocks></white> bloków. Starty są na x=0, y=<y>,"
                        + " co <spacing> bloków w osi z. Gracze widzą je w <white>/parkour</white>.",
                Placeholder.unparsed("courses", String.valueOf(DefaultCourses.ALL.size())),
                Placeholder.unparsed("blocks", String.valueOf(blocks)),
                Placeholder.unparsed("y", String.valueOf(DefaultCourses.START_Y)),
                Placeholder.unparsed("spacing", String.valueOf(DefaultCourses.SPACING))));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String id = StringArgumentType.getString(context, COURSE);
        int difficulty = IntegerArgumentType.getInteger(context, "trudnosc");
        String name = StringArgumentType.getString(context, "nazwa").strip();
        if (!ParkourCourse.ID.matcher(id).matches()) {
            player.sendMessage(MiniText.text("<red>Nazwa trasy: małe litery, cyfry i _, do 24 znaków."));
            return 0;
        }
        if (store.get(id).isPresent()) {
            player.sendMessage(
                    MiniText.text("<red>Trasa <white><id></white> już jest.", Placeholder.unparsed("id", id)));
            return 0;
        }
        World world = player.getWorld();
        if (!engine.isArenaWorld(world)
                || world.equals(player.getServer().getWorlds().getFirst())) {
            player.sendMessage(MiniText.text(
                    "<red>Trasy muszą być w osobnym świecie. Utwórz go: <white>/minigryadmin swiat parkour"));
            return 0;
        }
        ParkourCourse course = ParkourCourse.create(id, name.isEmpty() ? id : name, difficulty, world.getName());
        store.put(course);
        describe(player, course);
        player.sendMessage(MiniText.text(
                "<gray>Teraz: <white>start</white> na starcie, <white>punkt</white> na każdym punkcie kontrolnym"
                        + " (po kolei), <white>meta</white> na mecie, <white>wlacz</white>."));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        ParkourCourse course = course(context);
        if (course == null) {
            return 0;
        }
        store.remove(course.id());
        context.getSource()
                .getSender()
                .sendMessage(MiniText.text(
                        "<gray>Usunięto trasę <white><id></white> i jej rekordy.",
                        Placeholder.unparsed("id", course.id())));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (store.all().isEmpty()) {
            sender.sendMessage(MiniText.text(
                    "<gray>Nie ma jeszcze tras. Najprościej: <white>/minigryadmin swiat parkour</white>, potem"
                            + " <white>/parkouradmin generuj</white>."));
        }
        for (ParkourCourse course : store.all()) {
            describe(sender, course);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void describe(CommandSender sender, ParkourCourse course) {
        List<String> problems = course.problems();
        String state;
        if (course.isReady()) {
            state = "<green>gotowa";
        } else if (problems.isEmpty()) {
            state = "<yellow>wyłączona (/parkouradmin wlacz " + course.id() + ")";
        } else {
            state = "<red>niegotowa";
        }
        sender.sendMessage(MiniText.text(
                "<white><id></white> <gray>(<name>, trudność <difficulty>, punktów: <points>, świat <world>):</gray> "
                        + state,
                Placeholder.unparsed("id", course.id()),
                Placeholder.unparsed("name", course.name()),
                Placeholder.unparsed("difficulty", String.valueOf(course.difficulty())),
                Placeholder.unparsed(
                        "points", String.valueOf(course.checkpoints().size())),
                Placeholder.unparsed("world", course.world())));
        for (String problem : problems) {
            sender.sendMessage(MiniText.text("<gray>  • <problem>", Placeholder.unparsed("problem", problem)));
        }
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> anyCourse() {
        return Commands.argument(COURSE, StringArgumentType.word()).suggests((context, builder) -> {
            store.all().forEach(course -> builder.suggest(course.id()));
            return builder.buildFuture();
        });
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> readyCourse() {
        return Commands.argument(COURSE, StringArgumentType.word()).suggests((context, builder) -> {
            parkour.readyCourses().forEach(course -> builder.suggest(course.id()));
            return builder.buildFuture();
        });
    }

    /** The course named in the command, or null after telling the sender it does not exist. */
    private ParkourCourse course(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, COURSE);
        ParkourCourse course = store.get(id).orElse(null);
        if (course == null) {
            context.getSource()
                    .getSender()
                    .sendMessage(
                            MiniText.text("<red>Nie ma trasy <white><id></white>.", Placeholder.unparsed("id", id)));
        }
        return course;
    }

    private static Checkpoint pointHere(Player player) {
        Location at = player.getLocation();
        SpawnPoint spawn = new SpawnPoint(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        return new Checkpoint(spawn, at.getBlockY() - DEFAULT_FALL_DEPTH);
    }
}
