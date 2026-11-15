package com.ordredesarcane.online.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "mmr_1v1", nullable = false)
    private int mmr1v1 = 1000;

    @Column(name = "mmr_2v2", nullable = false)
    private int mmr2v2 = 1000;

    @Enumerated(EnumType.STRING)
    @Column(name = "league_tier", length = 32, nullable = false)
    private LeagueTier leagueTier = LeagueTier.APPRENTI;

    @Column(name = "golden_shards", nullable = false)
    private int goldenShards = 0;

    @Column(name = "arcane_crowns", nullable = false)
    private int arcaneCrowns = 0;

    @Column(name = "equipped_card_back", length = 64, nullable = false)
    private String equippedCardBack = "card_back_default";

    @Column(name = "equipped_board_skin", length = 64, nullable = false)
    private String equippedBoardSkin = "board_sanctuary";

    @Column(name = "equipped_pet", length = 64, nullable = false)
    private String equippedPet = "pet_none";

    @Column(name = "equipped_energy_skin", length = 64, nullable = false)
    private String equippedEnergySkin = "energy_arcane";

    @Column(name = "equipped_avatar", length = 64, nullable = false)
    private String equippedAvatar = "avatar_initiate";

    @Column(name = "equipped_league_frame", length = 64, nullable = false)
    private String equippedLeagueFrame = "frame_apprenti";

    @Column(name = "equipped_title", length = 64, nullable = false)
    private String equippedTitle = "Apprenti Arcane";

    @Column(name = "equipped_finisher", length = 64, nullable = false)
    private String equippedFinisher = "finisher_arcane_burst";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "faction_wins", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> factionWins = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserProfileEntity() {
    }

    public UserProfileEntity(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public int getMmr1v1() { return mmr1v1; }
    public void setMmr1v1(int mmr1v1) { this.mmr1v1 = mmr1v1; touch(); }
    public int getMmr2v2() { return mmr2v2; }
    public void setMmr2v2(int mmr2v2) { this.mmr2v2 = mmr2v2; touch(); }
    public LeagueTier getLeagueTier() { return leagueTier; }
    public void setLeagueTier(LeagueTier leagueTier) { this.leagueTier = leagueTier; touch(); }
    public int getGoldenShards() { return goldenShards; }
    public void setGoldenShards(int goldenShards) { this.goldenShards = goldenShards; touch(); }
    public int getArcaneCrowns() { return arcaneCrowns; }
    public void setArcaneCrowns(int arcaneCrowns) { this.arcaneCrowns = arcaneCrowns; touch(); }
    public String getEquippedCardBack() { return equippedCardBack; }
    public void setEquippedCardBack(String v) { this.equippedCardBack = v; touch(); }
    public String getEquippedBoardSkin() { return equippedBoardSkin; }
    public void setEquippedBoardSkin(String v) { this.equippedBoardSkin = v; touch(); }
    public String getEquippedPet() { return equippedPet; }
    public void setEquippedPet(String v) { this.equippedPet = v; touch(); }
    public String getEquippedEnergySkin() { return equippedEnergySkin; }
    public void setEquippedEnergySkin(String v) { this.equippedEnergySkin = v; touch(); }
    public String getEquippedAvatar() { return equippedAvatar; }
    public void setEquippedAvatar(String v) { this.equippedAvatar = v; touch(); }
    public String getEquippedLeagueFrame() { return equippedLeagueFrame; }
    public void setEquippedLeagueFrame(String v) { this.equippedLeagueFrame = v; touch(); }
    public String getEquippedTitle() { return equippedTitle; }
    public void setEquippedTitle(String v) { this.equippedTitle = v; touch(); }
    public String getEquippedFinisher() { return equippedFinisher; }
    public void setEquippedFinisher(String v) { this.equippedFinisher = v; touch(); }
    public Map<String, Integer> getFactionWins() { return factionWins; }
    public void setFactionWins(Map<String, Integer> factionWins) { this.factionWins = factionWins; touch(); }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
