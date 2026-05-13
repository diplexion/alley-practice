package dev.revere.alley.feature.match.handler.impl;

import dev.revere.alley.AlleyPlugin;
import dev.revere.alley.common.SoundUtil;
import dev.revere.alley.common.reflect.ReflectionService;
import dev.revere.alley.common.reflect.internal.types.TitleReflectionServiceImpl;
import dev.revere.alley.common.text.CC;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.handler.MatchMessenger;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Default implementation of {@link MatchMessenger} that broadcasts messages
 * to all participants and spectators of a match.
 *
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public class DefaultMatchMessenger implements MatchMessenger {

    private final Match match;
    private final AlleyPlugin plugin;

    public DefaultMatchMessenger(Match match) {
        this.match = match;
        this.plugin = AlleyPlugin.getInstance();
    }

    @Override
    public void notifyParticipants(String message) {
        this.match.getParticipants().forEach(participant ->
                participant.getPlayers().forEach(gamePlayer -> {
                    Player player = this.plugin.getServer().getPlayer(gamePlayer.getUuid());
                    if (player != null) {
                        player.sendMessage(CC.translate(message));
                    }
                })
        );
    }

    @Override
    public void notifySpectators(String message) {
        for (UUID uuid : this.match.getSpectatorHandler().getSpectators()) {
            Player player = this.plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(CC.translate(message));
            }
        }
    }

    @Override
    public void notifyAll(String message) {
        this.notifyParticipants(message);
        this.notifySpectators(message);
    }

    @Override
    public void notifyAll(List<String> messages) {
        messages.forEach(this::notifyAll);
    }

    @Override
    public void sendComponent(BaseComponent component) {
        this.match.getParticipants().forEach(participant ->
                participant.getPlayers().forEach(gamePlayer -> {
                    Player player = this.plugin.getServer().getPlayer(gamePlayer.getUuid());
                    if (player != null) {
                        player.spigot().sendMessage(component);
                    }
                })
        );

        for (UUID uuid : this.match.getSpectatorHandler().getSpectators()) {
            Player player = this.plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.spigot().sendMessage(component);
            }
        }
    }

    @Override
    public void sendTitle(String title, String subtitle) {
        TitleReflectionServiceImpl titleService = this.plugin.getService(ReflectionService.class)
                .getReflectionService(TitleReflectionServiceImpl.class);
        forEachOnlinePlayer(player -> titleService.sendTitle(player, title, subtitle));
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut, boolean spectators) {
        TitleReflectionServiceImpl titleService = this.plugin.getService(ReflectionService.class)
                .getReflectionService(TitleReflectionServiceImpl.class);

        this.match.getParticipants().forEach(participant ->
                participant.getPlayers().forEach(gamePlayer -> {
                    Player player = this.plugin.getServer().getPlayer(gamePlayer.getUuid());
                    if (player != null) {
                        titleService.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
                    }
                })
        );

        if (spectators) {
            for (UUID uuid : this.match.getSpectatorHandler().getSpectators()) {
                Player player = this.plugin.getServer().getPlayer(uuid);
                if (player != null) {
                    titleService.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
                }
            }
        }
    }

    @Override
    public void playSound(Sound sound) {
        forEachOnlinePlayer(player -> SoundUtil.playCustomSound(player, sound, 1.0F, 1.0F));
    }

    @Override
    public void playSound(GameParticipant<MatchGamePlayer> participant, Sound sound) {
        participant.getPlayers().forEach(gamePlayer -> {
            Player player = this.plugin.getServer().getPlayer(gamePlayer.getUuid());
            if (player != null) {
                SoundUtil.playCustomSound(player, sound, 1.0F, 1.0F);
            }
        });
    }

    private void forEachOnlinePlayer(Consumer<Player> action) {
        this.match.getParticipants().forEach(participant ->
                participant.getPlayers().forEach(gp -> {
                    Player player = this.plugin.getServer().getPlayer(gp.getUuid());
                    if (player != null) action.accept(player);
                })
        );

        for (UUID uuid : this.match.getSpectatorHandler().getSpectators()) {
            Player player = this.plugin.getServer().getPlayer(uuid);
            if (player != null) action.accept(player);
        }
    }
}
