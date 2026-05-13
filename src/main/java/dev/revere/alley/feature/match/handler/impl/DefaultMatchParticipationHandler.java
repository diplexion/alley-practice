package dev.revere.alley.feature.match.handler.impl;

import dev.revere.alley.adapter.knockback.KnockbackAdapter;
import dev.revere.alley.common.text.CC;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.core.profile.enums.ProfileState;
import dev.revere.alley.feature.kit.setting.types.visual.KitSettingHealthBar;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.MatchState;
import dev.revere.alley.feature.match.handler.MatchParticipationHandler;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.TeamGameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.visibility.VisibilityService;
import dev.revere.alley.visual.nametag.NametagService;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public class DefaultMatchParticipationHandler implements MatchParticipationHandler {

    private final Match match;

    public DefaultMatchParticipationHandler(Match match) {
        this.match = match;
    }

    @Override
    public void revivePlayer(Player player, boolean silent) {
        if (player == null) return;

        MatchGamePlayer gamePlayer = this.match.getFromAllGamePlayers(player);
        if (gamePlayer == null || gamePlayer.isDisconnected()) {
            return;
        }

        Profile profile = this.match.getPlugin().getService(ProfileService.class).getProfile(player.getUniqueId());
        boolean wasSpectating = profile.getState() == ProfileState.SPECTATING
                && this.match.getSpectatorHandler().isSpectating(player);

        if (!gamePlayer.isEliminated() && !wasSpectating) {
            return;
        }

        gamePlayer.setEliminated(false);
        gamePlayer.setDead(false);
        profile.setState(ProfileState.PLAYING);

        if (wasSpectating) {
            this.match.getSpectatorHandler().removeSpectator(player, false);
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);

        this.registerHealthObjective(player);
        this.match.setupPlayer(player);
        this.preparePlayerForCombat(player);

        if (!silent) {
            this.match.getMessenger().notifyAll(CC.translate("&a" + player.getName() + " &ahas been revived."));
        }
    }

    @Override
    public boolean pullPlayerIntoMatch(Player player, Player target, boolean newTeam) {
        if (player == null || target == null) return false;
        if (this.match.getState() == MatchState.ENDING_MATCH || this.match.getState() == MatchState.ENDING_ROUND) return false;
        if (newTeam && !this.match.getConfiguration().isAllowNewTeamPull()) return false;

        Profile playerProfile = this.match.getPlugin().getService(ProfileService.class).getProfile(player.getUniqueId());
        if (playerProfile == null || playerProfile.getState() != ProfileState.LOBBY) return false;
        if (this.match.getFromAllGamePlayers(player) != null || this.match.getSpectatorHandler().isSpectating(player)) return false;

        GameParticipant<MatchGamePlayer> targetParticipant = this.match.getParticipants().stream()
                .filter(p -> p.containsPlayer(target.getUniqueId()))
                .findFirst()
                .orElse(null);
        if (targetParticipant == null) return false;

        MatchGamePlayer newGamePlayer = new MatchGamePlayer(player.getUniqueId(), player.getName());

        if (newTeam) {
            this.match.getParticipants().add(new GameParticipant<>(newGamePlayer));
        } else if (targetParticipant instanceof TeamGameParticipant) {
            targetParticipant.addPlayer(newGamePlayer);
        } else {
            TeamGameParticipant<MatchGamePlayer> upgraded = new TeamGameParticipant<>(targetParticipant.getLeader());
            upgraded.addPlayer(newGamePlayer);
            this.match.replaceParticipant(targetParticipant, upgraded);
        }

        playerProfile.setState(ProfileState.PLAYING);
        playerProfile.setMatch(this.match);

        this.match.setupPlayer(player);
        this.registerHealthObjective(player);
        this.preparePlayerForCombat(player);

        return true;
    }

    @Override
    public void registerHealthObjective(Player player) {
        if (!this.match.getKit().isSettingEnabled(KitSettingHealthBar.class)) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard.equals(this.match.getPlugin().getServer().getScoreboardManager().getMainScoreboard())) {
            scoreboard = this.match.getPlugin().getServer().getScoreboardManager().getNewScoreboard();
            player.setScoreboard(scoreboard);
        }

        Objective objective = scoreboard.getObjective("showhealth");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("showhealth", "health");
        }

        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        objective.setDisplayName(ChatColor.RED + "❤");
    }

    private void preparePlayerForCombat(Player player) {
        this.match.getPlugin().getService(NametagService.class).updatePlayerState(player);
        this.match.getPlugin().getService(KnockbackAdapter.class).getKnockbackImplementation().applyKnockback(player, this.match.getKit().getKnockbackProfile());
        this.match.getPlugin().getService(VisibilityService.class).updateVisibility(player);
    }
}
