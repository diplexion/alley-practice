package dev.revere.alley.feature.match.handler.impl;

import dev.revere.alley.adapter.knockback.KnockbackAdapter;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.core.profile.enums.ProfileState;
import dev.revere.alley.feature.arena.ArenaService;
import dev.revere.alley.feature.arena.internal.types.StandAloneArena;
import dev.revere.alley.feature.kit.setting.types.mechanic.KitSettingCampProtectionImpl;
import dev.revere.alley.feature.kit.setting.types.mode.KitSettingPlatformDecay;
import dev.revere.alley.feature.kit.setting.types.visual.KitSettingHealthBar;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.MatchService;
import dev.revere.alley.feature.match.MatchState;
import dev.revere.alley.feature.match.data.MatchData;
import dev.revere.alley.feature.match.data.types.MatchDataSolo;
import dev.revere.alley.feature.match.handler.MatchLifecycle;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.match.snapshot.Snapshot;
import dev.revere.alley.feature.match.snapshot.SnapshotService;
import dev.revere.alley.feature.match.task.MatchTask;
import dev.revere.alley.feature.match.task.mode.PlatformDecayTask;
import dev.revere.alley.feature.match.task.other.MatchCampProtectionTask;
import dev.revere.alley.feature.tournament.TournamentService;
import dev.revere.alley.feature.visibility.VisibilityService;
import dev.revere.alley.visual.nametag.NametagService;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.Objects;
import java.util.UUID;

/**
 * Default lifecycle implementation that orchestrates match flow by delegating
 * type-specific decisions to the match's abstract methods (canEndRound, canEndMatch, etc.)
 * while owning the state transition and participant management logic.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class DefaultMatchLifecycle implements MatchLifecycle {

    private final Match match;

    public DefaultMatchLifecycle(Match match) {
        this.match = match;
    }

    @Override
    public void start() {
        match.sendPlayerVersusPlayerMessage();
        match.setState(MatchState.STARTING);
        this.startMatchTask();
        match.getParticipants().forEach(this::initializeParticipant);
        this.updateAllNametags();
        match.setStartTime(System.currentTimeMillis());
        match.onMatchStart();
    }

    @Override
    public void end() {
        match.cancelRespawnTasks();
        match.onMatchEnd();
        this.deleteArenaCopyIfStandalone();
        match.getParticipants().forEach(this::finalizeParticipant);
        this.updateAllNametags();
        this.cleanupTasks();
        this.cleanupHealthDisplay();
        match.getPlugin().getService(MatchService.class).removeMatch(match);

        if (match.getTournament() != null) {
            match.getPlugin().getService(TournamentService.class).handleMatchEnd(match);
        }
    }

    @Override
    public void handleRoundStart() {
        match.setStartTime(System.currentTimeMillis());
    }

    @Override
    public void handleRoundEnd() {
        match.setEndTime(System.currentTimeMillis());
        this.handleMatchHistoryData();

        match.getParticipants().forEach(participant ->
                participant.getAllPlayers().forEach(gamePlayer -> {
                    Player player = match.getPlugin().getServer().getPlayer(gamePlayer.getUuid());
                    if (player != null) {
                        this.createSnapshot(player);
                    }
                })
        );

        SnapshotService snapshotService = match.getPlugin().getService(SnapshotService.class);
        match.getSnapshots().forEach(snapshotService::addSnapshot);
    }

    @Override
    public boolean checkForConclusion(Player victim, Player killer) {
        if (!match.canEndRound()) {
            return false;
        }

        match.setState(MatchState.ENDING_ROUND);
        match.cancelRespawnTasks();

        if (match.getRunnable() != null) {
            match.getRunnable().setStage(4);
        }

        match.onRoundEnd();

        if (match.canEndMatch()) {
            this.handleRoundEnd();
            if (victim != null && killer != null) {
                match.getRewardHandler().applyDeathEffects(victim, killer);
            }
            match.setState(MatchState.ENDING_MATCH);
        }

        return true;
    }

    @Override
    public void createSnapshot(Player player) {
        if (match.getSnapshots().stream().anyMatch(s -> s.getUuid().equals(player.getUniqueId()))) {
            return;
        }

        MatchGamePlayer gamePlayer = match.getGamePlayer(player);
        if (gamePlayer == null || gamePlayer.isDisconnected()) {
            return;
        }

        Snapshot snapshot = new Snapshot(player, !gamePlayer.isDead());
        snapshot.setOpponent(match.getOpponent(player).getLeader().getUuid());
        snapshot.setLongestCombo(gamePlayer.getData().getLongestCombo());
        snapshot.setTotalHits(gamePlayer.getData().getHits());
        snapshot.setThrownPotions(gamePlayer.getData().getThrownPotions());
        snapshot.setMissedPotions(gamePlayer.getData().getMissedPotions());
        snapshot.setCriticalHits(gamePlayer.getData().getCriticalHits());
        snapshot.setBlockedHits(gamePlayer.getData().getBlockedHits());
        snapshot.setWTaps(gamePlayer.getData().getWTaps());

        match.getSnapshots().add(snapshot);
    }

    private void initializeParticipant(GameParticipant<MatchGamePlayer> participant) {
        VisibilityService visibilityService = match.getPlugin().getService(VisibilityService.class);
        KnockbackAdapter knockbackAdapter = match.getPlugin().getService(KnockbackAdapter.class);

        participant.getPlayers().forEach(gamePlayer -> {
            Player player = match.getPlugin().getServer().getPlayer(gamePlayer.getUuid());
            if (player == null) return;

            this.updatePlayerProfileForMatch(player);
            match.setupPlayer(player);
            visibilityService.updateVisibility(player);
            knockbackAdapter.getKnockbackImplementation().applyKnockback(player, match.getKit().getKnockbackProfile());
            match.getParticipationHandler().registerHealthObjective(player);
            this.registerCampProtectionTask(player);
        });
    }

    private void finalizeParticipant(GameParticipant<MatchGamePlayer> participant) {
        participant.getPlayers().stream()
                .filter(gp -> !gp.isDisconnected())
                .forEach(gp -> {
                    Player player = match.getPlugin().getServer().getPlayer(gp.getUuid());
                    if (player != null) {
                        match.finalizePlayer(player);
                    }
                });
    }

    private void updateAllNametags() {
        NametagService nametagService = match.getPlugin().getService(NametagService.class);
        match.getParticipants().forEach(participant ->
                participant.getAllPlayers().stream()
                        .map(gp -> match.getPlugin().getServer().getPlayer(gp.getUuid()))
                        .filter(Objects::nonNull)
                        .forEach(nametagService::updatePlayerState)
        );
    }

    private void startMatchTask() {
        MatchTask task = new MatchTask(match);
        task.runTaskTimer(match.getPlugin(), 0L, 20L);
        match.setRunnable(task);

        if (match.getKit().isSettingEnabled(KitSettingPlatformDecay.class) && match.getArena() instanceof StandAloneArena) {
            PlatformDecayTask.start(match);
        }
    }

    private void cleanupTasks() {
        if (match.getRunnable() != null) {
            match.getRunnable().cancel();
        }
    }

    private void cleanupHealthDisplay() {
        if (!match.getKit().isSettingEnabled(KitSettingHealthBar.class)) {
            return;
        }

        match.getParticipants().stream()
                .flatMap(p -> p.getPlayers().stream())
                .map(gp -> match.getPlugin().getServer().getPlayer(gp.getUuid()))
                .filter(Objects::nonNull)
                .forEach(player -> {
                    Objective objective = player.getScoreboard().getObjective("showhealth");
                    if (objective != null) {
                        objective.unregister();
                    }
                });
    }

    private void deleteArenaCopyIfStandalone() {
        if (match.getArena() instanceof StandAloneArena) {
            match.getPlugin().getService(ArenaService.class).deleteTemporaryArena((StandAloneArena) match.getArena());
        }
    }

    private void updatePlayerProfileForMatch(Player player) {
        Profile profile = match.getPlugin().getService(ProfileService.class).getProfile(player.getUniqueId());
        profile.setState(ProfileState.PLAYING);
        profile.setMatch(match);
    }

    private void registerCampProtectionTask(Player player) {
        if (!match.getKit().isSettingEnabled(KitSettingCampProtectionImpl.class)) {
            return;
        }
        new MatchCampProtectionTask(player).runTaskTimer(match.getPlugin(), 0L, 20L);
    }

    private void handleMatchHistoryData() {
        if (match.isTeamMatch()) return;

        match.getParticipants().forEach(participant ->
                participant.getAllPlayers().forEach(gamePlayer -> {
                    Player player = match.getPlugin().getServer().getPlayer(gamePlayer.getUuid());
                    if (player == null) return;

                    Profile profile = match.getPlugin().getService(ProfileService.class).getProfile(player.getUniqueId());

                    UUID winnerID = gamePlayer.isDead()
                            ? match.getOpponent(player).getLeader().getUuid()
                            : gamePlayer.getUuid();
                    UUID loserID = gamePlayer.isDead()
                            ? gamePlayer.getUuid()
                            : match.getOpponent(player).getLeader().getUuid();

                    String arenaName = (match.getArena() instanceof StandAloneArena)
                            ? ((StandAloneArena) match.getArena()).getOriginalArenaName()
                            : match.getArena().getName();

                    MatchData matchData = new MatchDataSolo(match.getKit().getName(), arenaName, winnerID, loserID);

                    if (match.isRanked()) {
                        matchData.setRanked(true);
                    }

                    profile.getProfileData().getPreviousMatches().add(matchData);
                })
        );
    }
}
