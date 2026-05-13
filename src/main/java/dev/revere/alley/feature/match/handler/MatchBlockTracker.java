package dev.revere.alley.feature.match.handler;

import org.bukkit.Location;
import org.bukkit.block.BlockState;

/**
 * Tracks block placements and breaks during a match, enabling full rollback
 * to the arena's original state when the match concludes.
 *
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public interface MatchBlockTracker {

    /**
     * Records a block placement for rollback at match end.
     *
     * @param blockState The state of the placed block.
     * @param location   The location of the placed block.
     */
    void trackPlacement(BlockState blockState, Location location);

    /**
     * Removes a block from the placed blocks tracking.
     *
     * @param blockState The block state to untrack.
     * @param location   The location.
     */
    void untrackPlacement(BlockState blockState, Location location);

    /**
     * Records a broken block for restoration at match end. If the block was
     * previously placed by a player, it is simply removed from the placed set
     * instead of being added to the broken set.
     *
     * @param blockState The original state of the broken block.
     * @param location   The location of the broken block.
     */
    void trackBreak(BlockState blockState, Location location);

    /**
     * Removes all player-placed blocks (sets them to AIR).
     */
    void removePlacedBlocks();

    /**
     * Restores all block changes made during the match: removes placed blocks
     * and restores broken blocks to their original state.
     */
    void rollback();
}
