package com.ordredesarcane.online.game.model;

import java.util.List;
import java.util.UUID;

public final class TeamState {

    private final TeamId teamId;
    private final int maxLp;
    private int lp;
    private final List<UUID> playerIds;

    public TeamState(TeamId teamId, int startingLp, List<UUID> playerIds) {
        this.teamId = teamId;
        this.maxLp = startingLp;
        this.lp = startingLp;
        this.playerIds = List.copyOf(playerIds);
    }

    public TeamId getTeamId() {
        return teamId;
    }

    public int getMaxLp() {
        return maxLp;
    }

    public int getLp() {
        return lp;
    }

    public List<UUID> getPlayerIds() {
        return playerIds;
    }

    public int applyDamage(int damage) {
        int actual = Math.max(0, damage);
        this.lp = Math.max(0, this.lp - actual);
        return actual;
    }

    public int heal(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = this.lp;
        this.lp = Math.min(this.maxLp, this.lp + amount);
        return this.lp - before;
    }
}
