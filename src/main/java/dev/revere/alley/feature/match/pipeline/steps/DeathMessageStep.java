package dev.revere.alley.feature.match.pipeline.steps;

import dev.revere.alley.AlleyPlugin;
import dev.revere.alley.common.text.CC;
import dev.revere.alley.core.locale.LocaleService;
import dev.revere.alley.core.locale.internal.impl.message.GameMessagesLocaleImpl;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.feature.cosmetic.CosmeticService;
import dev.revere.alley.feature.cosmetic.internal.repository.BaseCosmeticRepository;
import dev.revere.alley.feature.cosmetic.internal.repository.impl.killmessage.KillMessagePack;
import dev.revere.alley.feature.cosmetic.model.CosmeticType;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.match.pipeline.DeathContext;
import dev.revere.alley.feature.match.pipeline.DeathStep;
import dev.revere.alley.common.reflect.ReflectionService;
import dev.revere.alley.common.reflect.internal.types.ActionBarReflectionServiceImpl;
import org.bukkit.entity.Player;

/**
 * Handles sending death messages to all match participants and spectators.
 * Supports custom kill message cosmetics and falls back to default messages.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class DeathMessageStep implements DeathStep {

    @Override
    public void process(DeathContext context) {
        if (context.getGamePlayer().isDisconnected()) {
            return;
        }

        Player victim = context.getVictim();
        Player killer = context.getKiller();

        AlleyPlugin plugin = context.getMatch().getPlugin();
        ProfileService profileService = plugin.getService(ProfileService.class);
        Profile victimProfile = profileService.getProfile(victim.getUniqueId());

        if (killer == null) {
            sendDefaultDeathMessage(context, victim, victimProfile, null);
            return;
        }

        Profile killerProfile = profileService.getProfile(killer.getUniqueId());
        String selectedPackName = killerProfile.getProfileData().getCosmeticData().getSelected(CosmeticType.KILL_MESSAGE);

        if (selectedPackName == null || selectedPackName.equalsIgnoreCase("None")) {
            sendDefaultDeathMessage(context, victim, victimProfile, killerProfile);
            return;
        }

        CosmeticService cosmeticService = plugin.getService(CosmeticService.class);
        BaseCosmeticRepository<?> repository = cosmeticService.getRepository(CosmeticType.KILL_MESSAGE);
        KillMessagePack pack = (KillMessagePack) repository.getCosmetic(selectedPackName);

        if (pack == null) {
            sendDefaultDeathMessage(context, victim, victimProfile, killerProfile);
            return;
        }

        String messageTemplate = pack.getRandomMessage(context.getCause());
        if (messageTemplate == null) {
            sendDefaultDeathMessage(context, victim, victimProfile, killerProfile);
            return;
        }

        String finalMessage = messageTemplate
                .replace("{victim}", victimProfile.getNameColor() + victim.getName() + "&f")
                .replace("{killer}", killerProfile.getNameColor() + killer.getName() + "&f");

        LocaleService localeService = plugin.getService(LocaleService.class);
        context.getMatch().getMessenger().notifyAll(localeService.getString(GameMessagesLocaleImpl.MATCH_DEATH_MESSAGE_CUSTOM)
                .replace("{message}", CC.translate(finalMessage)));

        incrementKillerKills(context, killer);
    }

    /**
     * Sends a default death message (no custom kill message pack).
     */
    private void sendDefaultDeathMessage(DeathContext context, Player victim, Profile victimProfile, Profile killerProfile) {
        AlleyPlugin plugin = context.getMatch().getPlugin();
        LocaleService localeService = plugin.getService(LocaleService.class);
        Player killer = context.getKiller();

        if (killer == null) {
            context.getMatch().getMessenger().notifyAll(CC.translate(
                    localeService.getString(GameMessagesLocaleImpl.MATCH_DEATH_MESSAGE_GENERIC)
                            .replace("{player}", victimProfile.getName())
                            .replace("{name-color}", String.valueOf(victimProfile.getNameColor()))
            ));
        } else {
            incrementKillerKills(context, killer);

            plugin.getService(ReflectionService.class)
                    .getReflectionService(ActionBarReflectionServiceImpl.class)
                    .sendDeathMessage(killer, victim);

            context.getMatch().getMessenger().notifyAll(
                    localeService.getString(GameMessagesLocaleImpl.MATCH_DEATH_MESSAGE_GENERIC_KILLER)
                            .replace("{victim}", victimProfile.getNameColor() + victim.getName() + "&f")
                            .replace("{killer}", killerProfile.getNameColor() + killer.getName() + "&f")
                            .replace("{name-color}", String.valueOf(victimProfile.getNameColor()))
                            .replace("{killer-name-color}", String.valueOf(killerProfile.getNameColor()))
            );
        }
    }

    /**
     * Increments the kill count for the killer's participant.
     */
    private void incrementKillerKills(DeathContext context, Player killer) {
        GameParticipant<MatchGamePlayer> killerParticipant = context.getMatch().getParticipant(killer);
        if (killerParticipant != null) {
            killerParticipant.getLeader().getData().incrementKills();
        }
    }
}
