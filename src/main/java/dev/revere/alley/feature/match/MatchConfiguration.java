package dev.revere.alley.feature.match;

import lombok.Getter;

/**
 * Immutable configuration object holding match behavior flags and numeric parameters.
 * Each match type builds its configuration at construction time via the builder.
 *
 * @author Remi
 * @project Alley
 * @date 5/21/2024
 */
@Getter
public class MatchConfiguration {

    private final boolean eliminationBased;
    private final boolean roundBased;
    private final boolean immediateRespawn;
    private final boolean allowNewTeamPull;

    private MatchConfiguration(Builder builder) {
        this.eliminationBased = builder.eliminationBased;
        this.roundBased = builder.roundBased;
        this.immediateRespawn = builder.immediateRespawn;
        this.allowNewTeamPull = builder.allowNewTeamPull;
    }

    public static MatchConfiguration defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean eliminationBased = false;
        private boolean roundBased = false;
        private boolean immediateRespawn = true;
        private boolean allowNewTeamPull = false;

        public Builder eliminationBased(boolean value) {
            this.eliminationBased = value;
            return this;
        }

        public Builder roundBased(boolean value) {
            this.roundBased = value;
            return this;
        }

        public Builder immediateRespawn(boolean value) {
            this.immediateRespawn = value;
            return this;
        }

        public Builder allowNewTeamPull(boolean value) {
            this.allowNewTeamPull = value;
            return this;
        }

        public MatchConfiguration build() {
            return new MatchConfiguration(this);
        }
    }
}
