package com.ordredesarcane.online.game.model;

import com.ordredesarcane.online.catalog.CardType;
import com.ordredesarcane.online.catalog.Faction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerState {

    public static final int MAX_MONSTER_SLOTS = 4;
    public static final int MAX_FAMILIAR_SLOTS = 3;
    public static final int MAX_SPELL_ARTIFACT_SLOTS = 2;
    public static final int MAX_HAND_SIZE_END_TURN = 6;

    private final UUID userId;
    private final String username;
    private final boolean bot;
    private final AiDifficulty aiDifficulty;
    private final TeamId teamId;
    private final int seatIndex;

    private final String cardBackSkin;
    private final String boardSkin;
    private final String pet;
    private final String energySkin;
    private final String avatar;
    private final String leagueFrame;
    private final String title;
    private final String finisher;
    private Faction primaryFaction = Faction.FIRE;

    private boolean connected;
    private Instant disconnectedAt;
    private UUID controlledByUserId;

    private boolean mulliganCompleted;
    private final Set<Integer> mulliganReplacedSlots = new LinkedHashSet<>();
    private int energyTokens;
    private boolean healedThisTurn;
    private boolean spellsLockedThisTurn;
    private boolean spellsLockedNextTurn;
    private boolean directAttacksBlockedThisTurn;

    private final List<CardInstance> deck = new ArrayList<>();
    private final List<CardInstance> hand = new ArrayList<>();
    private final List<CardInstance> discard = new ArrayList<>();
    private final CardInstance[] monsters = new CardInstance[MAX_MONSTER_SLOTS];
    private final CardInstance[] familiars = new CardInstance[MAX_FAMILIAR_SLOTS];
    private final CardInstance[] spellArtifacts = new CardInstance[MAX_SPELL_ARTIFACT_SLOTS];
    private final Map<UUID, String> peekedOpponentTopCards = new HashMap<>();

    public PlayerState(
            UUID userId,
            String username,
            boolean bot,
            TeamId teamId,
            int seatIndex,
            String cardBackSkin,
            String boardSkin,
            String pet,
            String energySkin,
            String avatar,
            String leagueFrame,
            String title,
            String finisher) {
        this(
                userId,
                username,
                bot,
                AiDifficulty.MEDIUM,
                teamId,
                seatIndex,
                cardBackSkin,
                boardSkin,
                pet,
                energySkin,
                avatar,
                leagueFrame,
                title,
                finisher);
    }

    public PlayerState(
            UUID userId,
            String username,
            boolean bot,
            AiDifficulty aiDifficulty,
            TeamId teamId,
            int seatIndex,
            String cardBackSkin,
            String boardSkin,
            String pet,
            String energySkin,
            String avatar,
            String leagueFrame,
            String title,
            String finisher) {
        this.userId = userId;
        this.username = username;
        this.bot = bot;
        this.aiDifficulty = bot ? AiDifficulty.resolveOrDefault(aiDifficulty) : null;
        this.teamId = teamId;
        this.seatIndex = seatIndex;
        this.cardBackSkin = cardBackSkin;
        this.boardSkin = boardSkin;
        this.pet = pet;
        this.energySkin = energySkin;
        this.avatar = avatar;
        this.leagueFrame = leagueFrame;
        this.title = title;
        this.finisher = finisher;
        this.connected = true;
        this.mulliganCompleted = bot;
    }

    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isBot() { return bot; }
    public AiDifficulty getAiDifficulty() { return aiDifficulty; }
    public TeamId getTeamId() { return teamId; }
    public int getSeatIndex() { return seatIndex; }
    public String getCardBackSkin() { return cardBackSkin; }
    public String getBoardSkin() { return boardSkin; }
    public String getPet() { return pet; }
    public String getEnergySkin() { return energySkin; }
    public String getAvatar() { return avatar; }
    public String getLeagueFrame() { return leagueFrame; }
    public String getTitle() { return title; }
    public String getFinisher() { return finisher; }
    public Faction getPrimaryFaction() { return primaryFaction; }
    public void setPrimaryFaction(Faction primaryFaction) {
        this.primaryFaction = primaryFaction != null ? primaryFaction : Faction.FIRE;
    }

    public static String boardZoneOf(CardInstance card) {
        if (card == null || card.getDefinition() == null) {
            return null;
        }
        CardType type = card.getDefinition().cardType();
        if (type == CardType.MONSTER) {
            return "monster";
        }
        if (type == CardType.FAMILIAR) {
            return "familiar";
        }
        if (type.isSpellCategory()) {
            return "spell";
        }
        return null;
    }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) {
        this.connected = connected;
        this.disconnectedAt = connected ? null : Instant.now();
    }

    public Instant getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(Instant disconnectedAt) { this.disconnectedAt = disconnectedAt; }
    public UUID getControlledByUserId() { return controlledByUserId; }
    public void setControlledByUserId(UUID controlledByUserId) { this.controlledByUserId = controlledByUserId; }

    public boolean canBeControlledBy(UUID actorUserId) {
        return userId.equals(actorUserId) || userId != null && actorUserId != null && actorUserId.equals(controlledByUserId);
    }

    public boolean isMulliganCompleted() { return mulliganCompleted; }
    public void setMulliganCompleted(boolean mulliganCompleted) { this.mulliganCompleted = mulliganCompleted; }
    public boolean hasMulliganReplacedSlot(int slotIndex) { return mulliganReplacedSlots.contains(slotIndex); }
    public void markMulliganSlotReplaced(int slotIndex) { mulliganReplacedSlots.add(slotIndex); }
    public List<Integer> getMulliganReplacedSlots() { return List.copyOf(mulliganReplacedSlots); }
    public int getEnergyTokens() { return energyTokens; }
    public void addEnergy(int amount) { this.energyTokens = Math.max(0, this.energyTokens + amount); }
    public void spendEnergy(int amount) {
        if (this.energyTokens < amount) {
            throw new IllegalArgumentException("Énergie insuffisante (" + this.energyTokens + "/" + amount + ").");
        }
        this.energyTokens -= amount;
    }

    public boolean isHealedThisTurn() { return healedThisTurn; }
    public void setHealedThisTurn(boolean healedThisTurn) { this.healedThisTurn = healedThisTurn; }
    public boolean isSpellsLockedThisTurn() { return spellsLockedThisTurn; }
    public void setSpellsLockedThisTurn(boolean spellsLockedThisTurn) { this.spellsLockedThisTurn = spellsLockedThisTurn; }
    public boolean isSpellsLockedNextTurn() { return spellsLockedNextTurn; }
    public void setSpellsLockedNextTurn(boolean spellsLockedNextTurn) { this.spellsLockedNextTurn = spellsLockedNextTurn; }
    public boolean isDirectAttacksBlockedThisTurn() { return directAttacksBlockedThisTurn; }
    public void setDirectAttacksBlockedThisTurn(boolean v) { this.directAttacksBlockedThisTurn = v; }

    public List<CardInstance> getDeck() { return deck; }
    public List<CardInstance> getHand() { return hand; }
    public List<CardInstance> getDiscard() { return discard; }
    public CardInstance[] getMonsters() { return monsters; }
    public CardInstance[] getFamiliars() { return familiars; }
    public CardInstance[] getSpellArtifacts() { return spellArtifacts; }
    public Map<UUID, String> getPeekedOpponentTopCards() { return peekedOpponentTopCards; }

    public List<CardInstance> getAllBoardCreatures() {
        List<CardInstance> result = new ArrayList<>();
        for (CardInstance m : monsters) {
            if (m != null) result.add(m);
        }
        for (CardInstance f : familiars) {
            if (f != null) result.add(f);
        }
        return result;
    }

    public List<CardInstance> getAllMonsters() {
        return Arrays.stream(monsters).filter(Objects::nonNull).toList();
    }

    public List<CardInstance> getAllFamiliars() {
        return Arrays.stream(familiars).filter(Objects::nonNull).toList();
    }

    public List<CardInstance> getAllSpellArtifacts() {
        return Arrays.stream(spellArtifacts).filter(Objects::nonNull).toList();
    }

    public boolean hasEpicMonsterOnBoard() {
        return Arrays.stream(monsters).filter(Objects::nonNull).anyMatch(c -> c.getDefinition().isEpic());
    }

    public int findFirstFreeMonsterSlot() {
        for (int i = 0; i < MAX_MONSTER_SLOTS; i++) {
            if (monsters[i] == null) return i;
        }
        return -1;
    }

    public int findFirstFreeFamiliarSlot() {
        for (int i = 0; i < MAX_FAMILIAR_SLOTS; i++) {
            if (familiars[i] == null) return i;
        }
        return -1;
    }

    public int findFirstFreeSpellSlot() {
        for (int i = 0; i < MAX_SPELL_ARTIFACT_SLOTS; i++) {
            if (spellArtifacts[i] == null) return i;
        }
        return -1;
    }

    public Optional<CardInstance> findInHand(String instanceId) {
        return hand.stream().filter(c -> c.getInstanceId().equals(instanceId)).findFirst();
    }

    public Optional<CardInstance> findOnBoard(String instanceId) {
        for (CardInstance c : monsters) {
            if (c != null && c.getInstanceId().equals(instanceId)) return Optional.of(c);
        }
        for (CardInstance c : familiars) {
            if (c != null && c.getInstanceId().equals(instanceId)) return Optional.of(c);
        }
        for (CardInstance c : spellArtifacts) {
            if (c != null && c.getInstanceId().equals(instanceId)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public boolean removeFromBoard(String instanceId) {
        for (int i = 0; i < monsters.length; i++) {
            if (monsters[i] != null && monsters[i].getInstanceId().equals(instanceId)) {
                CardInstance removed = monsters[i];
                monsters[i] = null;
                removed.resetBoardStateForDiscard();
                discard.add(removed);
                return true;
            }
        }
        for (int i = 0; i < familiars.length; i++) {
            if (familiars[i] != null && familiars[i].getInstanceId().equals(instanceId)) {
                CardInstance removed = familiars[i];
                familiars[i] = null;
                removed.resetBoardStateForDiscard();
                discard.add(removed);
                return true;
            }
        }
        for (int i = 0; i < spellArtifacts.length; i++) {
            if (spellArtifacts[i] != null && spellArtifacts[i].getInstanceId().equals(instanceId)) {
                CardInstance removed = spellArtifacts[i];
                spellArtifacts[i] = null;
                removed.resetBoardStateForDiscard();
                discard.add(removed);
                return true;
            }
        }
        return false;
    }
}
