-- V2: Core domain tables for L'Ordre des Arcanes Online (PostgreSQL 17)

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(24) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    mmr_1v1 INT NOT NULL DEFAULT 1000,
    mmr_2v2 INT NOT NULL DEFAULT 1000,
    league_tier VARCHAR(32) NOT NULL DEFAULT 'APPRENTI',
    golden_shards INT NOT NULL DEFAULT 0,
    arcane_crowns INT NOT NULL DEFAULT 0,
    equipped_card_back VARCHAR(64) NOT NULL DEFAULT 'card_back_default',
    equipped_board_skin VARCHAR(64) NOT NULL DEFAULT 'board_sanctuary',
    equipped_pet VARCHAR(64) NOT NULL DEFAULT 'pet_none',
    equipped_energy_skin VARCHAR(64) NOT NULL DEFAULT 'energy_arcane',
    equipped_avatar VARCHAR(64) NOT NULL DEFAULT 'avatar_initiate',
    equipped_league_frame VARCHAR(64) NOT NULL DEFAULT 'frame_apprenti',
    equipped_title VARCHAR(64) NOT NULL DEFAULT 'Apprenti Arcane',
    equipped_finisher VARCHAR(64) NOT NULL DEFAULT 'finisher_arcane_burst',
    faction_wins JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cards_catalog (
    card_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    faction VARCHAR(16) NOT NULL CHECK (faction IN ('FIRE', 'ICE', 'SHADOW', 'LIGHT')),
    card_type VARCHAR(24) NOT NULL CHECK (card_type IN (
        'MONSTER', 'FAMILIAR', 'SPELL_NORMAL', 'SPELL_REACTIVE', 'SPELL_ULTIMATE', 'CURSE', 'ARTIFACT'
    )),
    is_epic BOOLEAN NOT NULL DEFAULT false,
    is_ramp BOOLEAN NOT NULL DEFAULT false,
    energy_cost INT NOT NULL CHECK (energy_cost >= 1 AND energy_cost <= 10),
    base_atk INT,
    base_def INT,
    description TEXT NOT NULL,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    abilities_script JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX idx_cards_catalog_faction ON cards_catalog(faction);
CREATE INDEX idx_cards_catalog_type ON cards_catalog(card_type);

CREATE TABLE decks (
    deck_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    primary_faction VARCHAR(16) NOT NULL CHECK (primary_faction IN ('FIRE', 'ICE', 'SHADOW', 'LIGHT')),
    secondary_faction VARCHAR(16) CHECK (secondary_faction IS NULL OR secondary_faction IN ('FIRE', 'ICE', 'SHADOW', 'LIGHT')),
    is_favorite BOOLEAN NOT NULL DEFAULT false,
    is_valid_antimeta BOOLEAN NOT NULL DEFAULT false,
    is_starter BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_decks_user_id ON decks(user_id);

CREATE TABLE deck_cards (
    deck_id UUID NOT NULL REFERENCES decks(deck_id) ON DELETE CASCADE,
    card_id VARCHAR(32) NOT NULL REFERENCES cards_catalog(card_id),
    is_foil BOOLEAN NOT NULL DEFAULT false,
    position INT NOT NULL,
    PRIMARY KEY (deck_id, card_id)
);

CREATE TABLE user_cosmetics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cosmetic_type VARCHAR(32) NOT NULL CHECK (cosmetic_type IN (
        'CARD_FOIL', 'CARD_BACK', 'BOARD_SKIN', 'COMPANION_PET', 'ENERGY_SKIN', 'MAGE_PROFILE', 'EMOTE_FINISHER'
    )),
    item_id VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, cosmetic_type, item_id)
);

CREATE INDEX idx_user_cosmetics_user ON user_cosmetics(user_id);

CREATE TABLE friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'BLOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (requester_id <> addressee_id),
    UNIQUE (requester_id, addressee_id)
);

CREATE INDEX idx_friendships_addressee ON friendships(addressee_id);

CREATE TABLE matches (
    match_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mode_1v1_2v2 VARCHAR(8) NOT NULL CHECK (mode_1v1_2v2 IN ('1V1', '2V2')),
    is_ranked BOOLEAN NOT NULL DEFAULT false,
    is_ai_match BOOLEAN NOT NULL DEFAULT false,
    team_a_ids JSONB NOT NULL,
    team_b_ids JSONB NOT NULL,
    winner_team VARCHAR(16),
    end_reason VARCHAR(32),
    turn_count INT NOT NULL DEFAULT 0,
    replay_log JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE TABLE tournaments (
    tournament_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL,
    max_players INT NOT NULL CHECK (max_players IN (8, 16)),
    status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED')),
    bracket_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE TABLE tournament_participants (
    tournament_id UUID NOT NULL REFERENCES tournaments(tournament_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    deck_1_id UUID NOT NULL REFERENCES decks(deck_id),
    deck_2_id UUID NOT NULL REFERENCES decks(deck_id),
    seed INT NOT NULL,
    eliminated BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (tournament_id, user_id)
);
