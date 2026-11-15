package com.ordredesarcane.online.game.model;

import java.util.UUID;

public final class StatModifier {

    private final UUID id = UUID.randomUUID();
    private final String sourceCardId;
    private final TeamId sourceTeam;
    private final int atkDelta;
    private final int defDelta;
    private final ModifierDuration duration;
    private final boolean debuff;
    private final boolean fromOpponentSpell;
    private int remainingOpponentTurnEnds;

    public StatModifier(
            String sourceCardId,
            TeamId sourceTeam,
            int atkDelta,
            int defDelta,
            ModifierDuration duration,
            boolean debuff,
            boolean fromOpponentSpell) {
        this.sourceCardId = sourceCardId;
        this.sourceTeam = sourceTeam;
        this.atkDelta = atkDelta;
        this.defDelta = defDelta;
        this.duration = duration;
        this.debuff = debuff;
        this.fromOpponentSpell = fromOpponentSpell;
        this.remainingOpponentTurnEnds = duration == ModifierDuration.END_OF_NEXT_OPPONENT_TURN ? 1 : 0;
    }

    public UUID getId() {
        return id;
    }

    public String getSourceCardId() {
        return sourceCardId;
    }

    public TeamId getSourceTeam() {
        return sourceTeam;
    }

    public int getAtkDelta() {
        return atkDelta;
    }

    public int getDefDelta() {
        return defDelta;
    }

    public ModifierDuration getDuration() {
        return duration;
    }

    public boolean isDebuff() {
        return debuff;
    }

    public boolean isFromOpponentSpell() {
        return fromOpponentSpell;
    }

    public int getRemainingOpponentTurnEnds() {
        return remainingOpponentTurnEnds;
    }

    public boolean decrementAndShouldExpireOnOpponentTurnEnd() {
        if (duration != ModifierDuration.END_OF_NEXT_OPPONENT_TURN) {
            return false;
        }
        remainingOpponentTurnEnds--;
        return remainingOpponentTurnEnds <= 0;
    }
}
