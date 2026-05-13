package dev.revere.alley.feature.match.pipeline.steps;

import dev.revere.alley.AlleyPlugin;
import dev.revere.alley.common.logger.Logger;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.feature.cosmetic.CosmeticService;
import dev.revere.alley.feature.cosmetic.internal.repository.KillEffectRepository;
import dev.revere.alley.feature.cosmetic.internal.repository.SoundEffectRepository;
import dev.revere.alley.feature.cosmetic.internal.repository.impl.killeffect.BaseKillEffect;
import dev.revere.alley.feature.cosmetic.internal.repository.impl.soundeffect.BaseSoundEffect;
import dev.revere.alley.feature.cosmetic.model.CosmeticType;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.pipeline.DeathContext;
import dev.revere.alley.feature.match.pipeline.DeathStep;
import org.bukkit.entity.Player;

/**
 * Determines whether the dead player should become a spectator (eliminated from play)
 * or whether they should be queued for respawn. Also applies kill-related cosmetic
 * effects (kill effects, sound effects) when a player is confirmed eliminated.
 * <p>
 * The decision is delegated to the match's {@code determinePostDeathAction} method,
 * which allows each match type to define its own elimination logic.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class EliminationStep implements DeathStep {

    @Override
    public void process(DeathContext context) {
        Match match = context.getMatch();
        Player victim = context.getVictim();
        Player killer = context.getKiller();

        boolean shouldSpectate = match.shouldBecomeSpectator(context);

        if (shouldSpectate) {
            context.setShouldSpectate(true);
            context.getGamePlayer().setEliminated(true);

            if (killer != null) {
                applyDeathEffects(match, victim, killer);
            }

            match.getSpectatorHandler().setupSpectator(victim);
            match.getPlugin().getServer().getScheduler().runTaskLater(
                    match.getPlugin(), () -> match.getSpectatorHandler().addSpectator(victim), 1L
            );
            context.abort();
        } else {
            context.setShouldRespawn(true);
        }
    }

    /**
     * Applies cosmetic kill and sound effects when a player is eliminated.
     *
     * @param match  The match context.
     * @param victim The player who was eliminated.
     * @param killer The player who got the kill.
     */
    private void applyDeathEffects(Match match, Player victim, Player killer) {
        AlleyPlugin plugin = match.getPlugin();
        ProfileService profileService = plugin.getService(ProfileService.class);
        Profile profile = profileService.getProfile(killer.getUniqueId());

        String selectedKillEffect = profile.getProfileData().getCosmeticData().getSelectedKillEffect();
        String selectedSoundEffect = profile.getProfileData().getCosmeticData().getSelectedSoundEffect();

        applyCosmetic(plugin, CosmeticType.KILL_EFFECT, selectedKillEffect, victim);
        applyCosmetic(plugin, CosmeticType.SOUND_EFFECT, selectedSoundEffect, killer);
    }

    /**
     * Generically applies a cosmetic effect by type.
     */
    private void applyCosmetic(AlleyPlugin plugin, CosmeticType type, String name, Player target) {
        if (name == null || name.equalsIgnoreCase("None")) {
            return;
        }

        CosmeticService cosmeticService = plugin.getService(CosmeticService.class);
        if (cosmeticService == null) {
            return;
        }

        switch (type) {
            case KILL_EFFECT:
                KillEffectRepository killRepo = cosmeticService.getRepository(CosmeticType.KILL_EFFECT, KillEffectRepository.class);
                if (killRepo != null) {
                    BaseKillEffect effect = killRepo.getCosmetic(name);
                    if (effect != null) {
                        effect.execute(target);
                    }
                }
                break;
            case SOUND_EFFECT:
                SoundEffectRepository soundRepo = cosmeticService.getRepository(CosmeticType.SOUND_EFFECT, SoundEffectRepository.class);
                if (soundRepo != null) {
                    BaseSoundEffect effect = soundRepo.getCosmetic(name);
                    if (effect != null) {
                        effect.execute(target);
                    }
                }
                break;
            default:
                Logger.warn("Cosmetic type " + type.name() + " does not support execution");
                break;
        }
    }
}
