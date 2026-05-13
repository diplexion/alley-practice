package dev.revere.alley.feature.match;

import dev.revere.alley.AlleyPlugin;
import dev.revere.alley.common.ListenerUtil;
import dev.revere.alley.common.PlayerUtil;
import dev.revere.alley.common.time.TimeUtil;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.core.profile.enums.ProfileState;
import dev.revere.alley.feature.arena.Arena;
import dev.revere.alley.feature.hotbar.HotbarService;
import dev.revere.alley.feature.kit.Kit;
import dev.revere.alley.feature.layout.LayoutService;
import dev.revere.alley.feature.layout.data.LayoutData;
import dev.revere.alley.feature.match.handler.MatchBlockTracker;
import dev.revere.alley.feature.match.handler.MatchLifecycle;
import dev.revere.alley.feature.match.handler.MatchMessenger;
import dev.revere.alley.feature.match.handler.MatchParticipationHandler;
import dev.revere.alley.feature.match.handler.MatchRewardHandler;
import dev.revere.alley.feature.match.handler.MatchSpectatorHandler;
import dev.revere.alley.feature.match.handler.impl.DefaultMatchBlockTracker;
import dev.revere.alley.feature.match.handler.impl.DefaultMatchLifecycle;
import dev.revere.alley.feature.match.handler.impl.DefaultMatchMessenger;
import dev.revere.alley.feature.match.handler.impl.DefaultMatchParticipationHandler;
import dev.revere.alley.feature.match.handler.impl.DefaultMatchRewardHandler;
import dev.revere.alley.feature.match.handler.impl.DefaultMatchSpectatorHandler;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.GamePlayer;
import dev.revere.alley.feature.match.model.TeamGameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.match.pipeline.DeathContext;
import dev.revere.alley.feature.match.pipeline.DeathPipeline;
import dev.revere.alley.feature.match.snapshot.Snapshot;
import dev.revere.alley.feature.match.task.MatchTask;
import dev.revere.alley.feature.match.task.other.MatchRespawnTask;
import dev.revere.alley.feature.queue.Queue;
import dev.revere.alley.feature.spawn.SpawnService;
import dev.revere.alley.feature.tournament.model.Tournament;
import dev.revere.alley.feature.visibility.VisibilityService;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Abstract base class for all match types. Defines the lifecycle, player setup/teardown,
 * the death pipeline integration, and composed handlers for messaging, spectators, and blocks.
 * <p>
 * Match behavior is configured through {@link MatchConfiguration} and abstract methods:
 * <ul>
 *   <li>{@link MatchConfiguration} — static flags (eliminationBased, roundBased, immediateRespawn, allowNewTeamPull)</li>
 *   <li>{@link #shouldBecomeSpectator(DeathContext)} — the core death decision point</li>
 * </ul>
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
@Getter
@Setter
public abstract class Match {

    protected final AlleyPlugin plugin = AlleyPlugin.getInstance();

    private final Queue queue;
    private final Kit kit;
    private final Arena arena;
    private final boolean ranked;

    protected Tournament tournament;

    private boolean teamMatch;
    private boolean affectStatistics = true;

    private MatchState state;
    private MatchTask runnable;
    private long startTime;
    private long endTime;

    private MatchConfiguration configuration = MatchConfiguration.defaults();

    private final DeathPipeline deathPipeline = new DeathPipeline();
    private final MatchMessenger messenger;
    private final MatchBlockTracker blockTracker;
    private final MatchSpectatorHandler spectatorHandler;
    private final MatchLifecycle lifecycle;
    private final MatchRewardHandler rewardHandler;
    private final MatchParticipationHandler participationHandler;

    private final List<Snapshot> snapshots = new ArrayList<>();
    private final Map<UUID, BukkitTask> activeRespawnTasks = new HashMap<>();

    /**
     * Constructs a new match with the specified configuration and initializes
     * all composed handlers.
     *
     * @param queue  The queue that created this match (nullable for tournaments/admin matches).
     * @param kit    The kit used in this match. Must not be null.
     * @param arena  The arena where this match takes place. Must not be null.
     * @param ranked Whether this match affects ranked Elo.
     */
    public Match(Queue queue, Kit kit, Arena arena, boolean ranked) {
        this.queue = queue;
        this.kit = Objects.requireNonNull(kit, "Kit cannot be null");
        this.arena = Objects.requireNonNull(arena, "Arena cannot be null");
        this.ranked = ranked;

        this.messenger = new DefaultMatchMessenger(this);
        this.blockTracker = new DefaultMatchBlockTracker(this);
        this.spectatorHandler = new DefaultMatchSpectatorHandler(this);
        this.lifecycle = new DefaultMatchLifecycle(this);
        this.rewardHandler = new DefaultMatchRewardHandler(this);
        this.participationHandler = new DefaultMatchParticipationHandler(this);
    }

    /**
     * Returns the list of all participants in this match.
     */
    public abstract List<GameParticipant<MatchGamePlayer>> getParticipants();

    /**
     * Handles a player disconnecting mid-match.
     */
    public abstract void handleDisconnect(Player player);

    /**
     * Respawns a player within the match after the pipeline determines they should continue.
     */
    public abstract void handleRespawn(Player player);

    /**
     * Determines if conditions allow a new round to begin.
     */
    public abstract boolean canStartRound();

    /**
     * Determines if the current round should end.
     */
    public abstract boolean canEndRound();

    /**
     * Determines if the entire match should end after the current round.
     */
    public abstract boolean canEndMatch();

    /**
     * Handles item drops on player death.
     */
    public abstract void handleDeathItemDrop(Player player, PlayerDeathEvent event);

    /**
     * Sends the pre-match "Player vs Player" information message.
     */
    public abstract void sendPlayerVersusPlayerMessage();

    /**
     * Determines whether the dead player should become a spectator (eliminated)
     * or continue playing (respawn). Core decision point for the death pipeline.
     *
     * @param context The death context with full state information.
     * @return {@code true} if the player should spectate, {@code false} to respawn.
     */
    public boolean shouldBecomeSpectator(DeathContext context) {
        if (context.getGamePlayer().isDisconnected()) {
            return false;
        }

        if (this.configuration.isEliminationBased()) {
            return context.getGamePlayer().isEliminated() || context.getVictimParticipant().isAllEliminated();
        }

        if (this.configuration.isRoundBased()) {
            return false;
        }

        return !context.getVictimParticipant().isAllDead();
    }

    /**
     * Marks a participant as dead and applies mode-specific death logic.
     * Override in subclasses that need custom death marking (e.g. reducing lives).
     *
     * @param player     The player who died.
     * @param gamePlayer The game player data to mutate.
     */
    public void handleParticipant(Player player, MatchGamePlayer gamePlayer) {
        gamePlayer.setDead(true);
    }

    /**
     * Hook called by the lifecycle at the end of a round, BEFORE history/snapshots are recorded.
     * Override to set winner/loser, broadcast results, process statistics, etc.
     */
    public void onRoundEnd() {
    }

    /**
     * Hook called by the lifecycle AFTER the match has fully started (participants initialized,
     * task running). Override to schedule additional mode-specific tasks.
     */
    public void onMatchStart() {
    }

    /**
     * Hook called by the lifecycle BEFORE cleanup begins on match end.
     * Override to cancel mode-specific scheduled tasks.
     */
    public void onMatchEnd() {
    }

    /**
     * Entry point for handling a player's death. Delegates to the {@link DeathPipeline}.
     * Only override if you need to completely bypass the standard pipeline.
     *
     * @param player The player who died.
     * @param cause  The cause of death.
     */
    public void handleDeath(Player player, EntityDamageEvent.DamageCause cause) {
        this.deathPipeline.execute(this, player, cause);
    }

    /**
     * Sets up a player for combat: resets state, gives loadout, applies kit effects.
     *
     * @param player The player to set up.
     */
    public void setupPlayer(Player player) {
        MatchGamePlayer gamePlayer = getGamePlayer(player);
        if (gamePlayer == null) {
            return;
        }

        gamePlayer.setDead(false);
        PlayerUtil.reset(player, true, true);
        this.giveLoadout(player, this.kit);
    }

    /**
     * Gives a player their kit loadout, respecting any custom layouts.
     *
     * @param player The player to equip.
     * @param kit    The kit to apply.
     */
    public void giveLoadout(Player player, Kit kit) {
        LayoutService layoutService = this.plugin.getService(LayoutService.class);
        ProfileService profileService = this.plugin.getService(ProfileService.class);

        player.getInventory().setArmorContents(kit.getArmor());

        Profile profile = profileService.getProfile(player.getUniqueId());
        if (profile.getProfileData().getLayoutData().getLayouts().size() > 1) {
            ItemStack[] itemsToGive;

            LayoutData kitLayout = profile.getProfileData().getLayoutData().getLayouts().get(kit.getName()).get(0);
            if (kitLayout == null) {
                itemsToGive = kit.getItems();
            } else {
                itemsToGive = kitLayout.getItems();
            }

            player.getInventory().setContents(itemsToGive);
        } else {
            layoutService.giveBooks(player, kit.getName());
        }

        player.updateInventory();
        this.kit.applyPotionEffects(player);
    }

    /**
     * Finalizes a player after match end: resets state, teleports to spawn.
     *
     * @param player The player to finalize.
     */
    public void finalizePlayer(Player player) {
        VisibilityService visibilityService = this.plugin.getService(VisibilityService.class);
        this.updatePlayerProfileForLobby(player);
        PlayerUtil.reset(player, false, true);
        player.setFireTicks(0);
        player.updateInventory();
        visibilityService.updateVisibility(player);
        this.plugin.getService(SpawnService.class).teleportToSpawn(player);
        this.plugin.getService(HotbarService.class).applyHotbarItems(player);
    }

    /**
     * Starts a delayed respawn process with a countdown before the player respawns.
     *
     * @param player The player to respawn after a delay.
     */
    public void startRespawnProcess(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);

        MatchGamePlayer gamePlayer = this.getGamePlayer(player);
        if (gamePlayer != null) {
            gamePlayer.setDead(false);
        }

        Location spawnLocation = this.arena.getCenter();
        ListenerUtil.teleportAndClearSpawn(player, spawnLocation);

        BukkitTask task = new MatchRespawnTask(player, this, 3).runTaskTimer(this.plugin, 0L, 20L);
        this.activeRespawnTasks.put(player.getUniqueId(), task);
    }

    /**
     * Cancels all active respawn tasks and clears the tracking map.
     * Called when a match or round ends to prevent stale respawn teleports.
     */
    public void cancelRespawnTasks() {
        this.activeRespawnTasks.values().forEach(BukkitTask::cancel);
        this.activeRespawnTasks.clear();
    }

    /**
     * Replaces a solo participant with a team participant. Subclasses storing
     * participants in named fields must override this.
     */
    public void replaceParticipant(GameParticipant<MatchGamePlayer> old, TeamGameParticipant<MatchGamePlayer> replacement) {
    }

    /**
     * Gets the MatchGamePlayer for a player (active, non-disconnected only).
     */
    public MatchGamePlayer getGamePlayer(Player player) {
        return this.getParticipants().stream()
                .map(GameParticipant::getPlayers)
                .flatMap(List::stream)
                .filter(gp -> gp.getUuid().equals(player.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets a MatchGamePlayer from all players (including disconnected).
     */
    public MatchGamePlayer getFromAllGamePlayers(Player player) {
        return this.getParticipants().stream()
                .map(GameParticipant::getAllPlayers)
                .flatMap(List::stream)
                .filter(gp -> gp.getUuid().equals(player.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the participant group containing the specified player.
     */
    public GameParticipant<MatchGamePlayer> getParticipant(Player player) {
        return this.getParticipants().stream()
                .filter(p -> p.containsPlayer(player.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the opposing participant in a two-sided match.
     */
    public GameParticipant<MatchGamePlayer> getOpponent(Player player) {
        GameParticipant<MatchGamePlayer> participant = this.getParticipant(player);
        if (participant == null) {
            return null;
        }

        return this.getParticipants().stream()
                .filter(p -> !p.equals(participant))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if two players are on the same team.
     */
    public boolean isInSameTeam(Player attacker, Player victim) {
        GameParticipant<MatchGamePlayer> attackerParticipant = this.getParticipant(attacker);
        GameParticipant<MatchGamePlayer> victimParticipant = this.getParticipant(victim);
        return attackerParticipant != null && attackerParticipant.equals(victimParticipant);
    }

    /**
     * Denies player movement during match countdown by teleporting back if moved.
     */
    public void denyPlayerMovement(List<GameParticipant<MatchGamePlayer>> participants) {
        if (participants.size() != 2) return;

        GameParticipant<?> participantA = participants.get(0);
        GameParticipant<?> participantB = participants.get(1);

        Location locationA = this.arena.getPos1();
        Location locationB = this.arena.getPos2();

        for (GamePlayer gamePlayer : participantA.getPlayers()) {
            Player player = gamePlayer.getTeamPlayer();
            if (player != null) {
                teleportBackIfMoved(player, locationA);
            }
        }

        for (GamePlayer gamePlayer : participantB.getPlayers()) {
            Player player = gamePlayer.getTeamPlayer();
            if (player != null) {
                teleportBackIfMoved(player, locationB);
            }
        }
    }

    /**
     * Gets a formatted string of the match duration.
     */
    public String getDuration() {
        if (this.state == MatchState.ENDING_MATCH) {
            return TimeUtil.getFormattedElapsedTime(this.endTime - this.startTime);
        }
        return TimeUtil.getFormattedElapsedTime(this.getElapsedTime());
    }

    /**
     * Gets the raw elapsed time in milliseconds since match start.
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - this.startTime;
    }


    protected void teleportBackIfMoved(Player player, Location location) {
        Location playerLoc = player.getLocation();
        double deltaX = Math.abs(playerLoc.getX() - location.getX());
        double deltaZ = Math.abs(playerLoc.getZ() - location.getZ());

        if (deltaX > 0.1 || deltaZ > 0.1) {
            player.teleport(new Location(
                    location.getWorld(), location.getX(), playerLoc.getY(), location.getZ(),
                    playerLoc.getYaw(), playerLoc.getPitch()
            ));
        }
    }


    private void updatePlayerProfileForLobby(Player player) {
        Profile profile = this.plugin.getService(ProfileService.class).getProfile(player.getUniqueId());
        profile.setState(ProfileState.LOBBY);
        profile.setMatch(null);
    }

}
