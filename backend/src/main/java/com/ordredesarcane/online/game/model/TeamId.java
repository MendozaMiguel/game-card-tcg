package com.ordredesarcane.online.game.model;

public enum TeamId {
    TEAM_A,
    TEAM_B;

    public TeamId opponent() {
        return this == TEAM_A ? TEAM_B : TEAM_A;
    }
}
