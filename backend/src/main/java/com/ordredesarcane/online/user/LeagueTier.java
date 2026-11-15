package com.ordredesarcane.online.user;

public enum LeagueTier {
    APPRENTI(0),
    INITIE(1100),
    ADEPTE(1250),
    MAGE(1450),
    ARCHIMAGE(1700),
    GRAND_MAITRE(2000);

    private final int minMmr;

    LeagueTier(int minMmr) {
        this.minMmr = minMmr;
    }

    public int getMinMmr() {
        return minMmr;
    }

    public static LeagueTier fromMmr(int mmr) {
        LeagueTier result = APPRENTI;
        for (LeagueTier tier : values()) {
            if (mmr >= tier.minMmr) {
                result = tier;
            }
        }
        return result;
    }
}
