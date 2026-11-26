package pl.mchosting.server.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;

/**
 * {@code /konto <gracz>} for staff: shows another player's account, {@code xp}/{@code monety}
 * change it (for tests and fixing mistakes, never for selling).
 */
final class AccountAdminCommand {

    static final String PERMISSION = "mchosting.core.admin";

    private AccountAdminCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(ProfileService profiles) {
        RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> target =
                Commands.argument("gracz", ArgumentTypes.player());
        return Commands.literal("konto")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(target.executes(context -> show(context, profiles))
                        .then(Commands.literal("xp")
                                .then(Commands.argument("ilosc", LongArgumentType.longArg(1))
                                        .executes(context -> {
                                            Player player = target(context);
                                            long amount = LongArgumentType.getLong(context, "ilosc");
                                            return reply(context, profiles.addXp(player.getUniqueId(), amount));
                                        })))
                        .then(Commands.literal("monety")
                                .then(Commands.argument("zmiana", LongArgumentType.longArg())
                                        .executes(context -> {
                                            Player player = target(context);
                                            long delta = LongArgumentType.getLong(context, "zmiana");
                                            try {
                                                return reply(
                                                        context, profiles.changeCoins(player.getUniqueId(), delta));
                                            } catch (IllegalStateException e) {
                                                context.getSource()
                                                        .getSender()
                                                        .sendMessage(Component.text(
                                                                "Gracz nie ma tylu monet.", NamedTextColor.RED));
                                                return 0;
                                            }
                                        }))))
                .build();
    }

    private static int show(CommandContext<CommandSourceStack> context, ProfileService profiles)
            throws CommandSyntaxException {
        Player player = target(context);
        return profiles.profile(player.getUniqueId())
                .map(profile -> reply(context, profile))
                .orElse(0);
    }

    private static int reply(CommandContext<CommandSourceStack> context, PlayerProfile profile) {
        context.getSource().getSender().sendMessage(ProfileCommand.describe(profile));
        return Command.SINGLE_SUCCESS;
    }

    private static Player target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("gracz", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource())
                .getFirst();
    }
}
