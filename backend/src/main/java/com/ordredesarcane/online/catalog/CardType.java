package com.ordredesarcane.online.catalog;

public enum CardType {
    MONSTER,
    FAMILIAR,
    SPELL_NORMAL,
    SPELL_REACTIVE,
    SPELL_ULTIMATE,
    CURSE,
    ARTIFACT;

    public boolean isCreature() {
        return this == MONSTER || this == FAMILIAR;
    }

    public boolean isSpellCategory() {
        return !isCreature();
    }
}
