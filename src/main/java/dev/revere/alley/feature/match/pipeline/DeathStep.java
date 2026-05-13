package dev.revere.alley.feature.match.pipeline;

/**
 * A single processing step within the death pipeline. Each step receives
 * a {@link DeathContext} and performs one focused responsibility (e.g. sending
 * death messages, checking for round conclusion, triggering respawn).
 * <p>
 * Steps are executed in a fixed, deterministic order. A step may signal
 * the pipeline to stop early by calling {@link DeathContext#abort()}.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public interface DeathStep {

    /**
     * Processes this step for the given death context.
     *
     * @param context The death context containing all information about the death event.
     */
    void process(DeathContext context);
}
