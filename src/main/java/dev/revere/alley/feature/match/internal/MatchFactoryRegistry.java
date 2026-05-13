package dev.revere.alley.feature.match.internal;

import dev.revere.alley.feature.arena.Arena;
import dev.revere.alley.feature.kit.Kit;
import dev.revere.alley.feature.kit.setting.KitSetting;
import dev.revere.alley.feature.kit.setting.types.mode.*;
import dev.revere.alley.feature.match.Match;
import dev.revere.alley.feature.match.internal.types.*;
import dev.revere.alley.feature.match.model.GameParticipant;
import dev.revere.alley.feature.match.model.internal.MatchGamePlayer;
import dev.revere.alley.feature.queue.Queue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry that maps kit settings to their corresponding match factory.
 * Resolves which match type to instantiate based on a kit's enabled settings.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
public class MatchFactoryRegistry {
    @FunctionalInterface
    public interface MatchFactory {
        Match create(Queue queue, Kit kit, Arena arena, boolean isRanked, GameParticipant<MatchGamePlayer> pA, GameParticipant<MatchGamePlayer> pB);
    }

    private final Map<Class<? extends KitSetting>, MatchFactory> factories = new LinkedHashMap<>();

    public MatchFactoryRegistry() {
        factories.put(KitSettingBed.class, BedMatch::new);
        factories.put(KitSettingLives.class, LivesMatch::new);
        factories.put(KitSettingCheckpoint.class, CheckpointMatch::new);
        factories.put(KitSettingHideAndSeek.class, HideAndSeekMatch::new);
        factories.put(KitSettingStickFight.class, (q, k, ar, r, pA, pB) -> new RoundsMatch(q, k, ar, r, pA, pB, 5));
        factories.put(KitSettingRounds.class, (q, k, ar, r, pA, pB) -> new RoundsMatch(q, k, ar, r, pA, pB, 3));
    }

    /**
     * Resolves a match instance for the given kit. If the kit has a registered
     * setting, the corresponding factory is used; otherwise falls back to DefaultMatch.
     */
    public Match resolve(Queue queue, Kit kit, Arena arena, boolean isRanked, GameParticipant<MatchGamePlayer> pA, GameParticipant<MatchGamePlayer> pB) {
        for (Map.Entry<Class<? extends KitSetting>, MatchFactory> entry : factories.entrySet()) {
            if (kit.isSettingEnabled(entry.getKey())) {
                return entry.getValue().create(queue, kit, arena, isRanked, pA, pB);
            }
        }
        return new DefaultMatch(queue, kit, arena, isRanked, pA, pB);
    }
}
