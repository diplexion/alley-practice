package dev.revere.alley.feature.match.pipeline.steps;

import dev.revere.alley.feature.match.pipeline.DeathContext;
import dev.revere.alley.feature.match.pipeline.DeathStep;

/**
 * Checks whether the death triggers a round or match conclusion.
 * If a conclusion is reached, this step signals the pipeline to abort
 * (no further respawn/spectate logic needed—the match is ending).
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class ConclusionCheckStep implements DeathStep {

    @Override
    public void process(DeathContext context) {
        boolean concluded = context.getMatch().getLifecycle().checkForConclusion(
                context.getVictim(),
                context.getKiller()
        );

        if (concluded) {
            context.setConclusionReached(true);
            context.abort();
        }
    }
}
