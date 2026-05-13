package dev.revere.alley.feature.match.pipeline;

import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Immutable context object carrying all relevant state for processing a player's death.
 * Created once per death event and passed through each {@link DeathStep} in the pipeline.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
@Getter
@Setter
public class DeathContext {
    private final Match match;
    private final Player victim;
    private final Player killer;
    private final EntityDamageEvent.DamageCause cause;
    private final MatchGamePlayer gamePlayer;
    private final GameParticipant<MatchGamePlayer> victimParticipant;

    /** Flag indicating whether this death should conclude the round/match. */
    private boolean conclusionReached;

    /** Flag indicating the pipeline should stop processing further steps. */
    private boolean aborted;

    /** Flag indicating the player should become a spectator after this death. */
    private boolean shouldSpectate;

    /** Flag indicating the player should respawn after this death. */
    private boolean shouldRespawn;

    /** Flag indicating that respawn should use a delayed (timer-based) process. */
    private boolean delayedRespawn;

    /**
     * Constructs a new DeathContext.
     *
     * @param match            The match in which the death occurred.
     * @param victim           The player who died.
     * @param killer           The player responsible for the kill, or null if environmental.
     * @param cause            The damage cause that triggered the death.
     * @param gamePlayer       The MatchGamePlayer representation of the victim.
     * @param victimParticipant The participant group the victim belongs to.
     */
    public DeathContext(Match match, Player victim, Player killer, EntityDamageEvent.DamageCause cause, MatchGamePlayer gamePlayer, GameParticipant<MatchGamePlayer> victimParticipant) {
        this.match = match;
        this.victim = victim;
        this.killer = killer;
        this.cause = cause;
        this.gamePlayer = gamePlayer;
        this.victimParticipant = victimParticipant;
    }

    /**
     * Signals that pipeline processing should stop after the current step.
     */
    public void abort() {
        this.aborted = true;
    }
}
