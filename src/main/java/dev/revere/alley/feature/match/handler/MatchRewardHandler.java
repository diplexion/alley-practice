package dev.revere.alley.feature.match.handler;

import org.bukkit.entity.Player;

/**
 * Handles post-match rewards including coin calculation, reward messaging,
 * and cosmetic death effects (kill effects and sound effects).
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public interface MatchRewardHandler {

    /**
     * Calculates and applies a coin reward based on player performance metrics
     * (kills, deaths, missed potions).
     *
     * @param player The player to reward.
     */
    void calculateCoinReward(Player player);

    /**
     * Sends the post-match coin reward message to a player.
     *
     * @param player The player to notify.
     */
    void sendRewardMessage(Player player);

    /**
     * Applies cosmetic death effects on match conclusion. Plays the killer's
     * selected kill effect on the victim and sound effect on the killer.
     *
     * @param victim The player who died (receives kill effect).
     * @param killer The player who killed (receives sound effect).
     */
    void applyDeathEffects(Player victim, Player killer);
}
