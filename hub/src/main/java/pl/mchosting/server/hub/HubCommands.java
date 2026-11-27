package pl.mchosting.server.hub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.ProfileService;

/** {@code /spawn}, {@code /nagroda} and {@code /hubadmin}. */
final class HubCommands {

    private HubCommands() {}

    static LiteralCommandNode<CommandSourceStack> spawn(HubSettings settings) {
        return Commands.literal("spawn")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    ((Player) context.getSource().getExecutor()).teleport(settings.spawn());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    static LiteralCommandNode<CommandSourceStack> dailyReward(ProfileService profiles) {
        return Commands.literal("nagroda")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    DailyRewardAction.claim((Player) context.getSource().getExecutor(), profiles);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    /**
     * {@code /hubadmin ustaw <punkt>} saves where the player stands; {@code /hubadmin punkty} lists
     * them; {@code /hubadmin brama <gra> rog1|rog2} marks the inside of a minigame gate.
     */
    static LiteralCommandNode<CommandSourceStack> admin(HubSettings settings) {
        Map<String, Location> firstCorners = new HashMap<>();
        return Commands.literal("hubadmin")
                .requires(source -> source.getSender().hasPermission(HubPermissions.ADMIN))
                .then(Commands.literal("ustaw")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .then(Commands.argument("punkt", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (HubPoint point : HubPoint.values()) {
                                        builder.suggest(point.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    Player player = (Player) context.getSource().getExecutor();
                                    String id = StringArgumentType.getString(context, "punkt");
                                    HubPoint.byId(id)
                                            .ifPresentOrElse(
                                                    point -> {
                                                        settings.setPoint(point, player.getLocation());
                                                        player.sendMessage(HubText.text(
                                                                "<green>Ustawiono punkt <white><point></white> tutaj.",
                                                                Placeholder.unparsed("point", point.displayName())));
                                                    },
                                                    () -> player.sendMessage(HubText.text(
                                                            "<red>Nie ma punktu <white><id></white>.",
                                                            Placeholder.unparsed("id", id))));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("brama")
                        .requires(source -> source.getExecutor() instanceof Player)
                        .then(Commands.argument("gra", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (HubDestination destination : HubDestination.values()) {
                                        destination.gameId().ifPresent(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.literal("rog1").executes(context -> {
                                    Player player = (Player) context.getSource().getExecutor();
                                    String game = StringArgumentType.getString(context, "gra");
                                    if (HubDestination.byGameId(game).isEmpty()) {
                                        player.sendMessage(HubText.text(
                                                "<red>Nie ma minigry <white><game></white>.",
                                                Placeholder.unparsed("game", game)));
                                        return 0;
                                    }
                                    firstCorners.put(game, player.getLocation());
                                    player.sendMessage(HubText.text(
                                            "<gray>Pierwszy róg bramy zapisany. Stań w przeciwległym rogu i wpisz"
                                                    + " <white>rog2</white>."));
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.literal("rog2").executes(context -> {
                                    Player player = (Player) context.getSource().getExecutor();
                                    String game = StringArgumentType.getString(context, "gra");
                                    Location first = firstCorners.remove(game);
                                    Location second = player.getLocation();
                                    if (first == null || !first.getWorld().equals(second.getWorld())) {
                                        player.sendMessage(HubText.text("<red>Najpierw ustaw pierwszy róg: rog1."));
                                        return 0;
                                    }
                                    settings.setGate(
                                            game,
                                            GateRegion.between(
                                                    second.getWorld().getName(),
                                                    first.getBlockX(),
                                                    first.getBlockY(),
                                                    first.getBlockZ(),
                                                    second.getBlockX(),
                                                    second.getBlockY(),
                                                    second.getBlockZ()));
                                    player.sendMessage(HubText.text(
                                            "<green>Brama <white><game></white> ustawiona. Wejście w nią = dołączenie"
                                                    + " do kolejki.",
                                            Placeholder.unparsed("game", game)));
                                    return Command.SINGLE_SUCCESS;
                                }))))
                .then(Commands.literal("punkty").executes(context -> {
                    for (HubPoint point : HubPoint.values()) {
                        String where =
                                settings.point(point).map(HubCommands::describe).orElse("nie ustawiony");
                        context.getSource()
                                .getSender()
                                .sendMessage(HubText.text(
                                        "<gray><id>: <white><where>",
                                        Placeholder.unparsed("id", point.id()),
                                        Placeholder.unparsed("where", where)));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private static String describe(Location location) {
        return "%s %d %d %d"
                .formatted(
                        location.getWorld() == null ? "?" : location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ());
    }
}
