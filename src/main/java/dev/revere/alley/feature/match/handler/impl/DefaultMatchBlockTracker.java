package dev.revere.alley.feature.match.handler.impl;

import dev.revere.alley.common.ListenerUtil;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.handler.MatchBlockTracker;
import dev.revere.alley.feature.arena.Arena;
import dev.revere.alley.feature.kit.setting.types.mode.KitSettingRaiding;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link MatchBlockTracker} that tracks placed/broken
 * blocks during a match and restores the arena to its original state on rollback.
 *
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public class DefaultMatchBlockTracker implements MatchBlockTracker {

    private final Match match;
    private final Map<BlockState, Location> placedBlocks = new ConcurrentHashMap<>();
    private final Map<BlockState, Location> brokenBlocks = new ConcurrentHashMap<>();

    public DefaultMatchBlockTracker(Match match) {
        this.match = match;
    }

    @Override
    public void trackPlacement(BlockState blockState, Location location) {
        this.placedBlocks.put(blockState, location);
    }

    @Override
    public void untrackPlacement(BlockState blockState, Location location) {
        this.placedBlocks.remove(blockState, location);
    }

    @Override
    public void trackBreak(BlockState blockState, Location location) {
        if (this.placedBlocks.containsValue(location)) {
            this.placedBlocks.values().remove(location);
        } else {
            this.brokenBlocks.put(blockState, location);
        }
    }

    @Override
    public void removePlacedBlocks() {
        for (Map.Entry<BlockState, Location> entry : this.placedBlocks.entrySet()) {
            entry.getValue().getBlock().setType(Material.AIR);
        }
        this.placedBlocks.clear();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void rollback() {
        if (this.match.getKit().isSettingEnabled(KitSettingRaiding.class)) {
            this.clearInteractiveBlocks();
        }

        this.removePlacedBlocks();

        for (Map.Entry<BlockState, Location> entry : this.brokenBlocks.entrySet()) {
            Location location = entry.getValue();
            BlockState originalState = entry.getKey();
            Block block = location.getBlock();
            block.setType(originalState.getType());
            block.setData(originalState.getRawData());
        }

        this.brokenBlocks.clear();
    }

    /**
     * For raiding kits: clears all interactive blocks within the arena bounds.
     */
    @SuppressWarnings("deprecation")
    private void clearInteractiveBlocks() {
        Arena arena = this.match.getArena();
        Location pos1 = arena.getPos1();
        Location pos2 = arena.getPos2();

        for (int x = pos1.getBlockX(); x <= pos2.getBlockX(); x++) {
            for (int z = pos1.getBlockZ(); z <= pos2.getBlockZ(); z++) {
                for (int y = pos1.getBlockY(); y <= pos2.getBlockY(); y++) {
                    Location location = new Location(pos1.getWorld(), x, y, z);
                    Block block = location.getBlock();
                    if (ListenerUtil.isInteractiveBlock(block.getType())) {
                        BlockState originalState = block.getState();
                        if (originalState.getType() == Material.AIR) {
                            continue;
                        }
                        this.brokenBlocks.put(originalState, location);
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    public Map<BlockState, Location> getPlacedBlocks() {
        return this.placedBlocks;
    }

    public Map<BlockState, Location> getBrokenBlocks() {
        return this.brokenBlocks;
    }
}
