package dev.revere.alley.feature.match.handler.impl;

import dev.revere.alley.common.logger.Logger;
import dev.revere.alley.common.text.CC;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.feature.cosmetic.CosmeticService;
import dev.revere.alley.feature.cosmetic.internal.repository.KillEffectRepository;
import dev.revere.alley.feature.cosmetic.internal.repository.SoundEffectRepository;
import dev.revere.alley.feature.cosmetic.internal.repository.impl.killeffect.BaseKillEffect;
import dev.revere.alley.feature.cosmetic.internal.repository.impl.soundeffect.BaseSoundEffect;
import dev.revere.alley.feature.cosmetic.model.CosmeticType;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.handler.MatchRewardHandler;
import dev.revere.alley.feature.match.model.MatchGamePlayerData;
import org.bukkit.entity.Player;

/**
 * Default reward handler that calculates performance-based coin rewards
 * and applies cosmetic death effects from the killer's equipped loadout.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class DefaultMatchRewardHandler implements MatchRewardHandler {

    private final Match match;

    public DefaultMatchRewardHandler(Match match) {
        this.match = match;
    }

    @Override
    public void calculateCoinReward(Player player) {
        ProfileService profileService = match.getPlugin().getService(ProfileService.class);
        Profile profile = profileService.getProfile(player.getUniqueId());

        MatchGamePlayerData data = match.getGamePlayer(player).getData();
        int kills = data.getKills();
        int deaths = data.getDeaths();
        int missedPotions = data.getMissedPotions();

        double score = kills * 15.0 - deaths * 10.0;
        int excessPotions = Math.max(missedPotions - 20, 0);
        score -= excessPotions * 1.5;

        int performanceScore = (int) Math.max(0, Math.min(100, score));
        profile.getProfileData().incrementCoins(performanceScore);
    }

    @Override
    public void sendRewardMessage(Player player) {
        ProfileService profileService = match.getPlugin().getService(ProfileService.class);
        Profile profile = profileService.getProfile(player.getUniqueId());
        int coins = profile.getProfileData().getCoins();
        player.sendMessage(CC.translate(" &7(&a+&6" + coins + "&f&7)"));
    }

    @Override
    public void applyDeathEffects(Player victim, Player killer) {
        ProfileService profileService = match.getPlugin().getService(ProfileService.class);
        Profile profile = profileService.getProfile(killer.getUniqueId());

        String killEffect = profile.getProfileData().getCosmeticData().getSelectedKillEffect();
        String soundEffect = profile.getProfileData().getCosmeticData().getSelectedSoundEffect();

        this.applyCosmetic(CosmeticType.KILL_EFFECT, killEffect, victim);
        this.applyCosmetic(CosmeticType.SOUND_EFFECT, soundEffect, killer);
    }

    private void applyCosmetic(CosmeticType type, String name, Player target) {
        if (name == null || name.equalsIgnoreCase("None")) return;

        CosmeticService cosmeticService = match.getPlugin().getService(CosmeticService.class);
        if (cosmeticService == null) return;

        switch (type) {
            case KILL_EFFECT:
                KillEffectRepository killRepo = cosmeticService.getRepository(type, KillEffectRepository.class);
                if (killRepo != null) {
                    BaseKillEffect effect = killRepo.getCosmetic(name);
                    if (effect != null) effect.execute(target);
                }
                break;
            case SOUND_EFFECT:
                SoundEffectRepository soundRepo = cosmeticService.getRepository(type, SoundEffectRepository.class);
                if (soundRepo != null) {
                    BaseSoundEffect effect = soundRepo.getCosmetic(name);
                    if (effect != null) effect.execute(target);
                }
                break;
            default:
                Logger.warn("Cosmetic type " + type.name() + " does not support execution");
                break;
        }
    }
}
