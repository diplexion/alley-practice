package dev.revere.alley.feature.match.handler;

import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Responsible for all outbound communication from a match to participants
 * and spectators. Handles text messages, titles, sounds, and chat components.
 *
 * @author Remi
 * @project Alley
 * @date 5/13/2026
 */
public interface MatchMessenger {

    /**
     * Sends a color-translated message to all participants only.
     *
     * @param message The message string (color codes are translated).
     */
    void notifyParticipants(String message);

    /**
     * Sends a color-translated message to all spectators only.
     *
     * @param message The message string (color codes are translated).
     */
    void notifySpectators(String message);

    /**
     * Sends a color-translated message to all participants and spectators.
     *
     * @param message The message string (color codes are translated).
     */
    void notifyAll(String message);

    /**
     * Sends a list of color-translated messages to all participants and spectators.
     *
     * @param messages The messages to send.
     */
    void notifyAll(List<String> messages);

    /**
     * Sends a clickable/hoverable chat component to all participants and spectators.
     *
     * @param component The chat component to broadcast.
     */
    void sendComponent(BaseComponent component);

    /**
     * Sends a title to all participants and spectators with default timing.
     *
     * @param title    The main title text.
     * @param subtitle The subtitle text.
     */
    void sendTitle(String title, String subtitle);

    /**
     * Sends a title with custom timing and optional spectator inclusion.
     *
     * @param title      The main title text.
     * @param subtitle   The subtitle text.
     * @param fadeIn     Fade-in duration in ticks.
     * @param stay       Stay duration in ticks.
     * @param fadeOut    Fade-out duration in ticks.
     * @param spectators Whether to include spectators.
     */
    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut, boolean spectators);

    /**
     * Plays a sound for all participants and spectators.
     *
     * @param sound The sound to play.
     */
    void playSound(Sound sound);

    /**
     * Plays a sound for a specific participant group.
     *
     * @param participant The participant group.
     * @param sound       The sound to play.
     */
    void playSound(GameParticipant<MatchGamePlayer> participant, Sound sound);
}
