package dev.revere.alley.feature.match.pipeline.steps;

import dev.revere.alley.feature.match.pipeline.DeathContext;
import dev.revere.alley.feature.match.pipeline.DeathStep;

/**
 * First step in the death pipeline: marks the player as dead and delegates
 * to the match's elimination strategy for any mode-specific death handling
 * (e.g. reducing lives, marking bed-broken elimination).
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class MarkDeadStep implements DeathStep {

    @Override
    public void process(DeathContext context) {
        context.getMatch().handleParticipant(context.getVictim(), context.getGamePlayer());
    }
}
