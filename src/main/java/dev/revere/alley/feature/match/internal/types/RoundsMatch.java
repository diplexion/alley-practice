package dev.revere.alley.feature.match.internal.types;

import dev.revere.alley.AlleyPlugin;
import dev.revere.alley.common.ListenerUtil;
import dev.revere.alley.common.PlayerUtil;
import dev.revere.alley.core.locale.LocaleService;
import dev.revere.alley.core.locale.internal.impl.VisualsLocaleImpl;
import dev.revere.alley.core.locale.internal.impl.message.GameMessagesLocaleImpl;
import dev.revere.alley.feature.arena.Arena;
import dev.revere.alley.feature.combat.CombatService;
import dev.revere.alley.feature.kit.Kit;
import dev.revere.alley.feature.kit.setting.types.mode.KitSettingBridges;
import dev.revere.alley.feature.kit.setting.types.mode.KitSettingStickFight;
import dev.revere.alley.feature.match.MatchConfiguration;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.queue.Queue;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * @author Emmy
 * @project Alley
 * @since 08/02/2025
 */
@Getter
public class RoundsMatch extends DefaultMatch {
    private GameParticipant<MatchGamePlayer> winner;
    private GameParticipant<MatchGamePlayer> loser;

    private final int rounds;
    private int currentRound;

    @Setter
    private String scorer;
    private Player fallenPlayer;

    /**
     * Constructor for the MatchRoundsImpl class.
     *
     * @param queue        The queue of the match.
     * @param kit          The kit of the match.
     * @param arena        The arena of the match.
     * @param ranked       Whether the match is ranked or not.
     * @param participantA The first participant.
     * @param participantB The second participant.
     * @param rounds       The amount of rounds the match will have.
     */
    public RoundsMatch(Queue queue, Kit kit, Arena arena, boolean ranked, GameParticipant<MatchGamePlayer> participantA, GameParticipant<MatchGamePlayer> participantB, int rounds) {
        super(queue, kit, arena, ranked, participantA, participantB);
        this.rounds = rounds;
        this.scorer = "Unknown";

        if (this.currentRound == 0) {
            this.currentRound = 1;
        }

        setConfiguration(MatchConfiguration.builder()
                .roundBased(true)
                .build());
    }

    @Override
    public void onRoundEnd() {
        this.winner = this.getParticipantA().isAllDead() ? this.getParticipantB() : this.getParticipantA();
        this.winner.getLeader().getData().incrementScore();
        this.loser = this.getParticipantA().isAllDead() ? this.getParticipantA() : this.getParticipantB();

        this.currentRound++;

        this.broadcastPlayerScoreMessage(this.winner, this.loser, this.scorer);

        if (this.getKit().isSettingEnabled(KitSettingStickFight.class)) {
            this.getBlockTracker().removePlacedBlocks();
        }

        if (!canEndMatch()) {
            if (!this.getKit().isSettingEnabled(KitSettingStickFight.class) && !getKit().isSettingEnabled(KitSettingBridges.class)) {
                this.getBlockTracker().removePlacedBlocks();
            }

            this.getParticipants().forEach(participant -> participant.getPlayers().forEach(gp -> {
                Player player = gp.getTeamPlayer();
                if (player != null) {
                    player.setVelocity(new Vector(0, 0, 0));
                    gp.setDead(false);
                    super.setupPlayer(player);
                }
            }));
        }
    }

    @Override
    public void handleDeath(Player player, EntityDamageEvent.DamageCause cause) {
        this.fallenPlayer = player;

        if (!this.getKit().isSettingEnabled(KitSettingStickFight.class)) {
            super.handleDeath(player, cause);
            return;
        }

        Player lastAttacker = plugin.getService(CombatService.class).getLastAttacker(player);
        if (lastAttacker == null) {
            GameParticipant<MatchGamePlayer> opponent = this.getOpponent(player);
            this.setScorer(opponent.getLeader().getUsername());
        } else {
            this.setScorer(lastAttacker.getName());
        }

        GameParticipant<MatchGamePlayer> victimParticipant = this.getParticipant(player);
        victimParticipant.getPlayers().forEach(gp -> {
            gp.getData().incrementDeaths();
            gp.setDead(true);
        });

        this.getLifecycle().checkForConclusion(player, lastAttacker);
    }

    @Override
    public void handleParticipant(Player player, MatchGamePlayer gamePlayer) {
        super.handleParticipant(player, gamePlayer);

        GameParticipant<MatchGamePlayer> participant = this.getParticipantA().containsPlayer(player.getUniqueId())
                ? this.getParticipantA()
                : this.getParticipantB();
        if (participant.getLeader().getData().getScore() == this.rounds) {
            GameParticipant<MatchGamePlayer> opponent = participant == this.getParticipantA() ? this.getParticipantB() : this.getParticipantA();
            opponent.getLeader().setEliminated(true);
        }
    }

    @Override
    public void handleRespawn(Player player) {
        player.spigot().respawn();
        PlayerUtil.reset(player, false, true);

        Location spawnLocation = getParticipants().get(0).containsPlayer(player.getUniqueId()) ? this.getArena().getPos1() : this.getArena().getPos2();
        ListenerUtil.teleportAndClearSpawn(player, spawnLocation);

        this.giveLoadout(player, this.getKit());
        this.applyColorKit(player);
    }

    @Override
    public boolean canStartRound() {
        return this.getParticipantA().getLeader().getData().getScore() < this.rounds && this.getParticipantB().getLeader().getData().getScore() < this.rounds;
    }

    @Override
    public boolean canEndRound() {
        return (this.getParticipantA().isAllDead() || this.getParticipantB().isAllDead())
                || (this.getParticipantA().getAllPlayers().stream().allMatch(MatchGamePlayer::isDisconnected)
                || this.getParticipantB().getAllPlayers().stream().allMatch(MatchGamePlayer::isDisconnected));
    }

    @Override
    public boolean canEndMatch() {
        return (this.getParticipantA().getLeader().getData().getScore() == this.rounds || this.getParticipantB().getLeader().getData().getScore() == this.rounds)
                || (this.getParticipantA().getAllPlayers().stream().allMatch(MatchGamePlayer::isDisconnected)
                || this.getParticipantB().getAllPlayers().stream().allMatch(MatchGamePlayer::isDisconnected));
    }

    /**
     * Broadcasts a message to all players in the match when a player scores.
     *
     * @param winner The player who scored.
     * @param loser  The player who was scored on.
     * @param scorer The name of the player who scored.
     */
    public void broadcastPlayerScoreMessage(GameParticipant<MatchGamePlayer> winner, GameParticipant<MatchGamePlayer> loser, String scorer) {
        LocaleService localeService = AlleyPlugin.getInstance().getService(LocaleService.class);

        boolean messageEnabled = localeService.getBoolean(GameMessagesLocaleImpl.MATCH_SCORED_MESSAGE_ENABLED_BOOLEAN);
        if (messageEnabled) {
            List<String> message;
            if (this.isTeamMatch()) {
                message = localeService.getStringList(GameMessagesLocaleImpl.MATCH_SCORED_MESSAGE_SOLO_FORMAT);
            } else {
                message = localeService.getStringList(GameMessagesLocaleImpl.MATCH_SCORED_MESSAGE_TEAM_FORMAT);
            }

            message.forEach(line -> this.getMessenger().notifyAll(line
                    .replace("{scorer}", scorer)
                    .replace("{winner}", winner.getLeader().getUsername())
                    .replace("{winner-color}", String.valueOf(this.getTeamColor(winner)))
                    .replace("{winner-goals}", String.valueOf(winner.getLeader().getData().getScore()))
                    .replace("{loser}", loser.getLeader().getUsername())
                    .replace("{loser-color}", String.valueOf(this.getTeamColor(loser)))
                    .replace("{loser-goals}", String.valueOf(loser.getLeader().getData().getScore()))
                    .replace("{current-score}", String.valueOf(winner.getLeader().getData().getScore()))
                    .replace("{max-rounds}", String.valueOf(this.rounds))
            ));
        }

        if (localeService.getBoolean(VisualsLocaleImpl.TITLE_TEAM_SCORED_ENABLED_BOOLEAN)) {
            String header = localeService.getString(VisualsLocaleImpl.TITLE_TEAM_SCORED_HEADER)
                    .replace("{loser-color}", String.valueOf(this.getTeamColor(loser)))
                    .replace("{winner-color}", String.valueOf(this.getTeamColor(winner)))
                    .replace("{scorer}", scorer)
                    .replace("{current-score}", String.valueOf(winner.getLeader().getData().getScore()))
                    .replace("{opponent-current-score}", String.valueOf(loser.getLeader().getData().getScore()))
                    .replace("{max-rounds}", String.valueOf(this.rounds));
            String footer = localeService.getString(VisualsLocaleImpl.TITLE_TEAM_SCORED_FOOTER)
                    .replace("{loser-color}", String.valueOf(this.getTeamColor(loser)))
                    .replace("{winner-color}", String.valueOf(this.getTeamColor(winner)))
                    .replace("{scorer}", scorer)
                    .replace("{current-score}", String.valueOf(winner.getLeader().getData().getScore()))
                    .replace("{opponent-current-score}", String.valueOf(loser.getLeader().getData().getScore()))
                    .replace("{max-rounds}", String.valueOf(this.rounds));
            int fadeIn = localeService.getInt(VisualsLocaleImpl.TITLE_TEAM_SCORED_FADE_IN);
            int stay = localeService.getInt(VisualsLocaleImpl.TITLE_TEAM_SCORED_STAY);
            int fadeOut = localeService.getInt(VisualsLocaleImpl.TITLE_TEAM_SCORED_FADEOUT);

            this.getMessenger().sendTitle(header, footer, fadeIn, stay, fadeOut, true);
        }
    }
}