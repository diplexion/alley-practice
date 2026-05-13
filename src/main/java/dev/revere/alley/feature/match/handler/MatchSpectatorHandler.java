package dev.revere.alley.feature.match.handler;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Handles the spectator lifecycle for a match: adding, removing, and
 * broadcasting spectator state to all match participants.
 *
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public interface MatchSpectatorHandler {

    /**
     * Adds a player to the match as a spectator. Handles profile state,
     * visibility, teleportation, and broadcast.
     *
     * @param player The player to add as a spectator.
     */
    void addSpectator(Player player);

    /**
     * Removes a player from the spectator list and returns them to lobby state.
     *
     * @param player The player to remove.
     * @param notify Whether to broadcast a departure message.
     */
    void removeSpectator(Player player, boolean notify);

    /**
     * Prepares a player's profile for spectating (internal transition from active to spectator).
     *
     * @param player The player transitioning to spectator mode.
     */
    void setupSpectator(Player player);

    /**
     * Broadcasts the spectator list and removes all spectators at match end.
     */
    void broadcastAndClear();

    /**
     * Returns the list of spectator UUIDs currently watching this match.
     *
     * @return An unmodifiable view of the spectator list.
     */
    List<UUID> getSpectators();

    /**
     * Checks if a player is currently spectating this match.
     *
     * @param player The player to check.
     * @return {@code true} if the player is spectating.
     */
    boolean isSpectating(Player player);
}
