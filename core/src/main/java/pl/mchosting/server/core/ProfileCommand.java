package pl.mchosting.server.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.LevelCurve;
import pl.mchosting.server.api.ProfileService;

/** {@code /profil}: shows the player's level, XP to the next level and coins. */
final class ProfileCommand {

    private static final String MESSAGE =
            "<green>Poziom <white><level></white> · do następnego: <white><next> XP</white>"
                    + " · monety: <gold><coins></gold>";

    private ProfileCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(ProfileService profiles) {
        return Commands.literal("profil")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    profiles.profile(player.getUniqueId()).ifPresent(profile -> {
                        long next = LevelCurve.totalXpFor(profile.level() + 1) - profile.totalXp();
                        player.sendMessage(MiniMessage.miniMessage()
                                .deserialize(
                                        MESSAGE,
                                        Placeholder.unparsed("level", String.valueOf(profile.level())),
                                        Placeholder.unparsed("next", String.valueOf(next)),
                                        Placeholder.unparsed("coins", String.valueOf(profile.coins()))));
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
