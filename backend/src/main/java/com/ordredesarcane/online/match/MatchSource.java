package com.ordredesarcane.online.match;

import java.util.UUID;

public enum MatchSource {
    RANKED,
    CASUAL,
    CUSTOM,
    AI,
    TOURNAMENT;

    public static MatchSource resolve(boolean ranked, boolean aiMatch, boolean customRoom, UUID tournamentId) {
        if (tournamentId != null) {
            return TOURNAMENT;
        }
        if (customRoom) {
            return CUSTOM;
        }
        if (aiMatch) {
            return AI;
        }
        return ranked ? RANKED : CASUAL;
    }
}
