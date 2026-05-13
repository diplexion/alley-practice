package dev.revere.alley.feature.match.pipeline;

import dev.revere.alley.feature.combat.CombatService;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.MatchState;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.match.pipeline.steps.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;

/**
 * Orchestrates the ordered processing of a player death event through a series
 * of {@link DeathStep} instances. Each step handles one concern (marking dead,
 * sending messages, checking conclusion, determining respawn/spectate).
 * <p>
 * This replaces the monolithic {@code handleDeath} method with a pipeline that
 * is deterministic, debuggable, and extensible without risking ordering bugs.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class DeathPipeline {
    private final List<DeathStep> steps;

    /**
     * Constructs the death pipeline with the standard step ordering.
     * Steps are executed in the order listed—changing the order changes behavior.
     */
    public DeathPipeline() {
        this.steps = Arrays.asList(
                new MarkDeadStep(),
                new DeathMessageStep(),
                new ConclusionCheckStep(),
                new EliminationStep(),
                new RespawnStep()
        );
    }

    /**
     * Executes the death pipeline for the given player within the specified match.
     * Validates that the match is in a valid state before processing, constructs the
     * {@link DeathContext}, and passes it through each step in order.
     *
     * @param match  The match in which the death occurred.
     * @param player The player who died.
     * @param cause  The damage cause of the death.
     */
    public void execute(Match match, Player player, EntityDamageEvent.DamageCause cause) {
        if (!(match.getState() == MatchState.STARTING || match.getState() == MatchState.RUNNING)) {
            return;
        }

        MatchGamePlayer gamePlayer = match.getFromAllGamePlayers(player);
        GameParticipant<MatchGamePlayer> participant = match.getParticipant(player);

        if (participant == null || gamePlayer == null) {
            return;
        }

        if (participant.isAllEliminated() && !gamePlayer.isDisconnected()) {
            return;
        }

        Player killer = match.getPlugin().getService(
                CombatService.class
        ).getLastAttacker(player);

        DeathContext context = new DeathContext(match, player, killer, cause, gamePlayer, participant);

        player.setVelocity(new Vector());

        for (DeathStep step : this.steps) {
            step.process(context);
            if (context.isAborted()) {
                break;
            }
        }
    }
}
