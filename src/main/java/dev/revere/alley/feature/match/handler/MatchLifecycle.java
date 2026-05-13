package dev.revere.alley.feature.match.handler;

import org.bukkit.entity.Player;

/**
 * Handles match state transitions and lifecycle orchestration including
 * start, end, round management, conclusion detection, and snapshot creation.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public interface MatchLifecycle {

    /**
     * Starts the match: transitions to STARTING state, initializes all participants,
     * applies knockback/visibility, and begins the countdown task.
     */
    void start();

    /**
     * Ends the match: cleans up arena, finalizes all participants, stops tasks,
     * removes from service tracking, and handles tournament integration.
     */
    void end();

    /**
     * Handles the beginning of a new round. Resets the start timer.
     */
    void handleRoundStart();

    /**
     * Finalizes match data: sets end time, records match history, creates
     * snapshots for all participants, and submits them to the snapshot service.
     * Does NOT invoke the onRoundEnd hook — callers handle that separately.
     */
    void handleRoundEnd();

    /**
     * Checks if the match/round has reached a conclusion. Transitions state
     * and triggers round-end processing if conditions are met.
     *
     * @param victim The player whose death may trigger the conclusion (nullable).
     * @param killer The killer involved (nullable).
     * @return {@code true} if a conclusion was reached.
     */
    boolean checkForConclusion(Player victim, Player killer);

    /**
     * Creates a post-mortem snapshot of the player's inventory and combat stats
     * for the inventory viewer.
     *
     * @param player The player to snapshot.
     */
    void createSnapshot(Player player);
}
