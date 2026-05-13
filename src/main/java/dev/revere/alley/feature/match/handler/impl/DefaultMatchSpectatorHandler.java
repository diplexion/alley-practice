package dev.revere.alley.feature.match.handler.impl;

import dev.revere.alley.AlleyPlugin;
import dev.revere.alley.common.ListenerUtil;
import dev.revere.alley.common.PlayerUtil;
import dev.revere.alley.common.text.CC;
import dev.revere.alley.core.locale.LocaleService;
import dev.revere.alley.core.locale.internal.impl.message.GameMessagesLocaleImpl;
import dev.revere.alley.core.profile.Profile;
import dev.revere.alley.core.profile.ProfileService;
import dev.revere.alley.core.profile.enums.ProfileState;
import dev.revere.alley.feature.hotbar.HotbarService;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.MatchState;
import dev.revere.alley.feature.match.handler.MatchSpectatorHandler;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.spawn.SpawnService;
import dev.revere.alley.feature.visibility.VisibilityService;
import dev.revere.alley.visual.nametag.NametagService;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public class DefaultMatchSpectatorHandler implements MatchSpectatorHandler {

    private final Match match;
    private final AlleyPlugin plugin;
    private final List<UUID> spectators = new CopyOnWriteArrayList<>();

    public DefaultMatchSpectatorHandler(Match match) {
        this.match = match;
        this.plugin = AlleyPlugin.getInstance();
    }

    @Override
    public void addSpectator(Player player) {
        if (this.match.getGamePlayer(player) == null) {
            if (this.match.getState() == MatchState.ENDING_MATCH) {
                player.sendMessage(CC.translate("&cThis match has already ended."));
                return;
            }
            this.setupSpectator(player);
            this.spectators.add(player.getUniqueId());
        }

        NametagService nametagService = this.plugin.getService(NametagService.class);
        VisibilityService visibilityService = this.plugin.getService(VisibilityService.class);
        HotbarService hotbarService = this.plugin.getService(HotbarService.class);

        nametagService.updatePlayerState(player);
        visibilityService.updateVisibility(player);
        hotbarService.applyHotbarItems(player);

        if (this.match.getArena().getCenter() == null) {
            player.sendMessage(CC.translate("&cThe arena is not set up for spectating"));
            return;
        }

        player.setAllowFlight(true);
        player.setFlying(true);
        ListenerUtil.teleportAndClearSpawn(player, this.match.getArena().getCenter());

        ProfileService profileService = this.plugin.getService(ProfileService.class);
        Profile profile = profileService.getProfile(player.getUniqueId());
        this.match.getMessenger().notifyAll("&6" + profile.getFancyName() + " &fis now spectating the match.");
    }

    @Override
    public void removeSpectator(Player player, boolean notify) {
        ProfileService profileService = this.plugin.getService(ProfileService.class);
        Profile profile = profileService.getProfile(player.getUniqueId());

        profile.setState(profile.inTournament() ? ProfileState.TOURNAMENT_LOBBY : ProfileState.LOBBY);
        profile.setMatch(null);

        this.plugin.getService(NametagService.class).updatePlayerState(player);
        this.plugin.getService(VisibilityService.class).updateVisibility(player);

        player.setAllowFlight(false);
        player.setFlying(false);

        player.setFireTicks(0);
        player.updateInventory();
        PlayerUtil.reset(player, false, true);

        this.plugin.getService(SpawnService.class).teleportToSpawn(player);
        this.plugin.getService(HotbarService.class).applyHotbarItems(player);

        this.spectators.remove(player.getUniqueId());

        if (notify) {
            this.match.getMessenger().notifyAll("&6" + profile.getFancyName() + " &fis no longer spectating the match.");
        }
    }

    @Override
    public void setupSpectator(Player player) {
        ProfileService profileService = this.plugin.getService(ProfileService.class);
        Profile profile = profileService.getProfile(player.getUniqueId());
        profile.setState(ProfileState.SPECTATING);
        profile.setMatch(this.match);
        PlayerUtil.reset(player, false, true);
    }

    @Override
    public void broadcastAndClear() {
        List<String> firstThreeNames = new ArrayList<>();
        this.spectators.stream()
                .map(uuid -> this.plugin.getServer().getPlayer(uuid))
                .filter(Objects::nonNull)
                .limit(3)
                .forEach(player -> firstThreeNames.add(player.getName()));

        long remainingCount = Math.max(0, this.spectators.size() - 3);

        this.match.getMessenger().notifyAll(
                this.plugin.getService(LocaleService.class).getString(GameMessagesLocaleImpl.MATCH_ENDED_SPECTATORS_LIST)
                        .replace("{spectators}", String.join(", ", firstThreeNames))
                        .replace("{more_count}", String.valueOf(remainingCount))
        );

        new ArrayList<>(this.spectators).forEach(uuid -> {
            Player player = this.plugin.getServer().getPlayer(uuid);
            if (player != null) {
                this.removeSpectator(player, false);
            }
        });
    }

    @Override
    public List<UUID> getSpectators() {
        return Collections.unmodifiableList(this.spectators);
    }

    @Override
    public boolean isSpectating(Player player) {
        return this.spectators.contains(player.getUniqueId());
    }
}
