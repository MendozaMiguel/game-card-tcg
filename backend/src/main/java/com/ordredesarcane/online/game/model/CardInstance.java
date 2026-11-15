package com.ordredesarcane.online.game.model;

import com.ordredesarcane.online.catalog.CardDefinition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class CardInstance {

    private final String instanceId;
    private final UUID ownerPlayerId;
    private final CardDefinition definition;
    private final boolean foil;

    private Posture posture = Posture.ATTACK;
    private int slot = -1;
    private boolean faceDown;
    private boolean exhausted;
    private boolean activatedThisTurn;
    private boolean changedPostureThisTurn;
    private int attacksMadeThisTurn;
    private boolean slowedCurrentTurn;
    private boolean slowPendingNextOwnTurn;
    private boolean camouflageUntilNextOwnTurn;
    private boolean firstCamouflageUsed;
    private boolean preventDestructionThisCombat;
    private Integer curseRemainingOpponentTurns;
    private final List<StatModifier> modifiers = new ArrayList<>();

    public CardInstance(String instanceId, UUID ownerPlayerId, CardDefinition definition, boolean foil) {
        this.instanceId = instanceId;
        this.ownerPlayerId = ownerPlayerId;
        this.definition = definition;
        this.foil = foil;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public UUID getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public CardDefinition getDefinition() {
        return definition;
    }

    public boolean isFoil() {
        return foil;
    }

    public Posture getPosture() {
        return posture;
    }

    public void setPosture(Posture posture) {
        this.posture = posture;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public boolean isFaceDown() {
        return faceDown;
    }

    public void setFaceDown(boolean faceDown) {
        this.faceDown = faceDown;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public void setExhausted(boolean exhausted) {
        this.exhausted = exhausted;
    }

    public boolean isActivatedThisTurn() {
        return activatedThisTurn;
    }

    public void setActivatedThisTurn(boolean activatedThisTurn) {
        this.activatedThisTurn = activatedThisTurn;
    }

    public boolean isChangedPostureThisTurn() {
        return changedPostureThisTurn;
    }

    public void setChangedPostureThisTurn(boolean changedPostureThisTurn) {
        this.changedPostureThisTurn = changedPostureThisTurn;
    }

    public int getAttacksMadeThisTurn() {
        return attacksMadeThisTurn;
    }

    public void incrementAttacksMadeThisTurn() {
        this.attacksMadeThisTurn++;
    }

    public boolean isSlowed() {
        return slowedCurrentTurn || slowPendingNextOwnTurn;
    }

    public boolean isSlowedCurrentTurn() {
        return slowedCurrentTurn;
    }

    public boolean isSlowPendingNextOwnTurn() {
        return slowPendingNextOwnTurn;
    }

    public void applySlow(boolean currentlyTargetsOwnTurn) {
        if (currentlyTargetsOwnTurn) {
            this.slowedCurrentTurn = true;
            this.slowPendingNextOwnTurn = true;
        } else {
            this.slowedCurrentTurn = true;
        }
    }

    public boolean isCamouflaged() {
        return camouflageUntilNextOwnTurn;
    }

    public void setCamouflageUntilNextOwnTurn(boolean camouflageUntilNextOwnTurn) {
        this.camouflageUntilNextOwnTurn = camouflageUntilNextOwnTurn;
    }

    public boolean isFirstCamouflageUsed() {
        return firstCamouflageUsed;
    }

    public void setFirstCamouflageUsed(boolean firstCamouflageUsed) {
        this.firstCamouflageUsed = firstCamouflageUsed;
    }

    public boolean isPreventDestructionThisCombat() {
        return preventDestructionThisCombat;
    }

    public void setPreventDestructionThisCombat(boolean preventDestructionThisCombat) {
        this.preventDestructionThisCombat = preventDestructionThisCombat;
    }

    public Integer getCurseRemainingOpponentTurns() {
        return curseRemainingOpponentTurns;
    }

    public void setCurseRemainingOpponentTurns(Integer curseRemainingOpponentTurns) {
        this.curseRemainingOpponentTurns = curseRemainingOpponentTurns;
    }

    public List<StatModifier> getModifiers() {
        return modifiers;
    }

    public void addModifier(StatModifier modifier) {
        this.modifiers.add(modifier);
    }

    public void clearDebuffs() {
        modifiers.removeIf(StatModifier::isDebuff);
        slowedCurrentTurn = false;
        slowPendingNextOwnTurn = false;
    }

    public void resetBoardStateForDiscard() {
        this.slot = -1;
        this.posture = Posture.ATTACK;
        this.faceDown = false;
        this.exhausted = false;
        this.activatedThisTurn = false;
        this.changedPostureThisTurn = false;
        this.attacksMadeThisTurn = 0;
        this.slowedCurrentTurn = false;
        this.slowPendingNextOwnTurn = false;
        this.camouflageUntilNextOwnTurn = false;
        this.preventDestructionThisCombat = false;
        this.curseRemainingOpponentTurns = null;
        this.modifiers.clear();
    }

    public void onOwnerTurnStart() {
        this.exhausted = false;
        this.activatedThisTurn = false;
        this.changedPostureThisTurn = false;
        this.attacksMadeThisTurn = 0;
        this.camouflageUntilNextOwnTurn = false;
        this.preventDestructionThisCombat = false;
    }

    public void onOwnerTurnEnd() {
        this.slowedCurrentTurn = this.slowPendingNextOwnTurn;
        this.slowPendingNextOwnTurn = false;
    }

    public void expireEndOfCombatModifiers() {
        modifiers.removeIf(m -> m.getDuration() == ModifierDuration.END_OF_COMBAT);
        preventDestructionThisCombat = false;
    }

    public void expireEndOfTurnModifiers(TeamId endingPlayerTeam) {
        Iterator<StatModifier> it = modifiers.iterator();
        while (it.hasNext()) {
            StatModifier m = it.next();
            if (m.getDuration() == ModifierDuration.END_OF_TURN) {
                it.remove();
            } else if (m.getDuration() == ModifierDuration.END_OF_NEXT_OPPONENT_TURN
                    && m.getSourceTeam() != endingPlayerTeam
                    && m.decrementAndShouldExpireOnOpponentTurnEnd()) {
                it.remove();
            }
        }
    }
}
