package dev.revere.alley.feature.match.handler;

import org.bukkit.entity.Player;

/**
 * Handles player participation transitions within a match: reviving eliminated
 * players, pulling new players in, and applying combat-ready state (health display,
 * knockback profile, visibility, nametags).
 *
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public interface MatchParticipationHandler {

    /**
     * Revives an eliminated player back into active play. Handles spectator removal,
     * profile state, game mode reset, combat preparation, and optional broadcast.
     *
     * @param player The player to revive.
     * @param silent Whether to suppress the revive broadcast message.
     */
    void revivePlayer(Player player, boolean silent);

    /**
     * Pulls a lobby player into this match, joining the target's team.
     * If {@code newTeam} is true (FFA-only), a new participant is created.
     *
     * @param player  The player to pull in. Must be in LOBBY state.
     * @param target  A player already in this match.
     * @param newTeam If true, create a new separate participant (FFA only).
     * @return {@code true} if the pull succeeded.
     */
    boolean pullPlayerIntoMatch(Player player, Player target, boolean newTeam);

    /**
     * Registers the below-name health objective for a player if the kit
     * has the health bar setting enabled.
     *
     * @param player The player to register the objective for.
     */
    void registerHealthObjective(Player player);
}
