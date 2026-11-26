package pl.mchosting.server.hub;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import pl.mchosting.server.api.DailyClaim;
import pl.mchosting.server.api.ProfileService;

/** Claims the daily reward and tells the player what they got. Used by /nagroda and the profile menu. */
final class DailyRewardAction {

    private static final Sound CLAIM_SOUND =
            Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.7f, 1.4f);

    private DailyRewardAction() {}

    static void claim(Player player, ProfileService profiles) {
        if (profiles.profile(player.getUniqueId()).isEmpty()) {
            return;
        }
        switch (profiles.claimDaily(player.getUniqueId())) {
            case DailyClaim.Claimed claimed -> {
                player.sendMessage(HubText.text(
                        "<green>Nagroda dnia <day>: <gold>+<coins> monet</gold> i <white>+<xp> XP</white>."
                                + " Wróć jutro, seria rośnie!",
                        Placeholder.unparsed(
                                "day", String.valueOf(claimed.streak().streak())),
                        Placeholder.unparsed(
                                "coins", String.valueOf(claimed.reward().coins())),
                        Placeholder.unparsed(
                                "xp", String.valueOf(claimed.reward().xp()))));
                player.playSound(CLAIM_SOUND);
            }
            case DailyClaim.AlreadyClaimed already ->
                player.sendMessage(HubText.text(
                        "<yellow>Dzisiejsza nagroda jest już odebrana. Wróć jutro (seria: <streak>).",
                        Placeholder.unparsed(
                                "streak", String.valueOf(already.streak().streak()))));
        }
    }
}
