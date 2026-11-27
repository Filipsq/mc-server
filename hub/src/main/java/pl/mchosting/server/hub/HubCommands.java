package pl.mchosting.server.hub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.ProfileService;

/** {@code /spawn}, {@code /nagroda}, {@code /wroc} and {@code /hubadmin}. */
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

    static LiteralCommandNode<CommandSourceStack> goBack(ReturnListener returns) {
        return Commands.literal("wroc")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    ReturnAction.send((Player) context.getSource().getExecutor(), returns);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    /** {@code /hubadmin ustaw <punkt>} saves where the player stands; {@code /hubadmin punkty} lists them. */
    static LiteralCommandNode<CommandSourceStack> admin(HubSettings settings) {
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
