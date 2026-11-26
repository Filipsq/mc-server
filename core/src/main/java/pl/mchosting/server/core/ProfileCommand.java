package pl.mchosting.server.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.LocalDate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.DailyReward;
import pl.mchosting.server.api.LevelProgress;
import pl.mchosting.server.api.PlayerProfile;
import pl.mchosting.server.api.ProfileService;

/** {@code /profil}: shows the player's level, XP to the next level, coins and daily streak. */
final class ProfileCommand {

    private static final String MESSAGE =
            "<green>Poziom <white><level></white> · do następnego: <white><next> XP</white>"
                    + " · monety: <gold><coins></gold> · seria nagród: <white><streak></white>";

    private ProfileCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(ProfileService profiles) {
        return Commands.literal("profil")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    profiles.profile(player.getUniqueId()).ifPresent(profile -> player.sendMessage(describe(profile)));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    static Component describe(PlayerProfile profile) {
        LevelProgress progress = profile.levelProgress();
        LocalDate today = LocalDate.now(DailyReward.NETWORK_ZONE);
        return MiniMessage.miniMessage()
                .deserialize(
                        MESSAGE,
                        Placeholder.unparsed("level", String.valueOf(progress.level())),
                        Placeholder.unparsed("next", String.valueOf(progress.xpToNext())),
                        Placeholder.unparsed("coins", String.valueOf(profile.coins())),
                        Placeholder.unparsed(
                                "streak", String.valueOf(profile.daily().currentStreak(today))));
    }
}
