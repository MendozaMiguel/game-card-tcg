package com.ordredesarcane.online.game.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PendingReactionWindow {

    public enum ReactionTriggerType {
        ATTACK_DECLARED,
        FAMILIAR_EFFECT_ACTIVATED,
        SPELL_TARGETED_ALLY_MONSTER
    }

    private final UUID windowId = UUID.randomUUID();
    private final ReactionTriggerType triggerType;
    private final GamePhase returnPhase;
    private final long savedTurnRemainingMillis;
    private final Instant expiresAt;

    private final UUID attackerPlayerId;
    private final String attackerInstanceId;
    private String defenderTargetId;
    private final boolean directAttack;

    private final UUID sourceActorPlayerId;
    private final String sourceCardInstanceId;
    private final String pendingTargetId;
    private final String pendingSecondaryTargetId;
    private final Integer pendingChoiceIndex;
    private final int pendingSpellSlot;

    private final List<UUID> respondingPlayerQueue = new ArrayList<>();
    private final List<UUID> passedPlayerIds = new ArrayList<>();
    private UUID currentResponderPlayerId;
    private boolean actionCancelled;

    public PendingReactionWindow(
            ReactionTriggerType triggerType,
            GamePhase returnPhase,
            long savedTurnRemainingMillis,
            Instant expiresAt,
            UUID attackerPlayerId,
            String attackerInstanceId,
            String defenderTargetId,
            boolean directAttack,
            UUID sourceActorPlayerId,
            String sourceCardInstanceId,
            String pendingTargetId,
            String pendingSecondaryTargetId,
            Integer pendingChoiceIndex,
            int pendingSpellSlot,
            List<UUID> responders) {
        this.triggerType = triggerType;
        this.returnPhase = returnPhase;
        this.savedTurnRemainingMillis = savedTurnRemainingMillis;
        this.expiresAt = expiresAt;
        this.attackerPlayerId = attackerPlayerId;
        this.attackerInstanceId = attackerInstanceId;
        this.defenderTargetId = defenderTargetId;
        this.directAttack = directAttack;
        this.sourceActorPlayerId = sourceActorPlayerId;
        this.sourceCardInstanceId = sourceCardInstanceId;
        this.pendingTargetId = pendingTargetId;
        this.pendingSecondaryTargetId = pendingSecondaryTargetId;
        this.pendingChoiceIndex = pendingChoiceIndex;
        this.pendingSpellSlot = pendingSpellSlot;
        this.respondingPlayerQueue.addAll(responders);
        this.currentResponderPlayerId = responders.isEmpty() ? null : responders.getFirst();
    }

    public UUID getWindowId() {
        return windowId;
    }

    public ReactionTriggerType getTriggerType() {
        return triggerType;
    }

    public GamePhase getReturnPhase() {
        return returnPhase;
    }

    public long getSavedTurnRemainingMillis() {
        return savedTurnRemainingMillis;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getAttackerPlayerId() {
        return attackerPlayerId;
    }

    public String getAttackerInstanceId() {
        return attackerInstanceId;
    }

    public String getDefenderTargetId() {
        return defenderTargetId;
    }

    public void setDefenderTargetId(String defenderTargetId) {
        this.defenderTargetId = defenderTargetId;
    }

    public boolean isDirectAttack() {
        return directAttack;
    }

    public UUID getSourceActorPlayerId() {
        return sourceActorPlayerId;
    }

    public String getSourceCardInstanceId() {
        return sourceCardInstanceId;
    }

    public String getPendingTargetId() {
        return pendingTargetId;
    }

    public String getPendingSecondaryTargetId() {
        return pendingSecondaryTargetId;
    }

    public Integer getPendingChoiceIndex() {
        return pendingChoiceIndex;
    }

    public int getPendingSpellSlot() {
        return pendingSpellSlot;
    }

    public UUID getCurrentResponderPlayerId() {
        return currentResponderPlayerId;
    }

    public List<UUID> getRespondingPlayerQueue() {
        return respondingPlayerQueue;
    }

    public List<UUID> getPassedPlayerIds() {
        return passedPlayerIds;
    }

    public boolean isActionCancelled() {
        return actionCancelled;
    }

    public void setActionCancelled(boolean actionCancelled) {
        this.actionCancelled = actionCancelled;
    }

    public void advanceResponder(UUID responderId) {
        passedPlayerIds.add(responderId);
        int idx = respondingPlayerQueue.indexOf(responderId);
        if (idx >= 0 && idx + 1 < respondingPlayerQueue.size()) {
            currentResponderPlayerId = respondingPlayerQueue.get(idx + 1);
        } else {
            currentResponderPlayerId = null;
        }
    }

    public boolean hasRemainingResponders() {
        return currentResponderPlayerId != null;
    }
}
