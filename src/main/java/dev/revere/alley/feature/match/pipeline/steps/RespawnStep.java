package dev.revere.alley.feature.match.pipeline.steps;

import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.pipeline.DeathContext;
import dev.revere.alley.feature.match.pipeline.DeathStep;
import org.bukkit.entity.Player;

/**
 * Final step: handles the respawn of a player who was not eliminated.
 * Distinguishes between immediate respawn (default) and delayed/timer-based respawn
 * (used by modes like BedFight or respawn-timer kits).
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class RespawnStep implements DeathStep {

    @Override
    public void process(DeathContext context) {
        if (!context.isShouldRespawn()) {
            return;
        }

        if (context.getGamePlayer().isEliminated()) {
            return;
        }

        Match match = context.getMatch();
        Player victim = context.getVictim();

        if (match.getConfiguration().isImmediateRespawn()) {
            match.getPlugin().getServer().getScheduler().runTaskLater(
                    match.getPlugin(), () -> match.handleRespawn(victim), 1L
            );
        } else {
            match.startRespawnProcess(victim);
        }
    }
}
