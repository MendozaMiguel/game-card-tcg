-- V4: Match source classification, light game summaries, and per-player participant index

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS is_custom_room BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS match_source VARCHAR(24) NOT NULL DEFAULT 'CASUAL',
    ADD COLUMN IF NOT EXISTS tournament_id UUID REFERENCES tournaments(tournament_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS tournament_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS match_summary JSONB NOT NULL DEFAULT '{}'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_matches_source'
    ) THEN
        ALTER TABLE matches
            ADD CONSTRAINT chk_matches_source
            CHECK (match_source IN ('RANKED', 'CASUAL', 'CUSTOM', 'AI', 'TOURNAMENT'));
    END IF;
END $$;

UPDATE matches
SET match_source = CASE
    WHEN tournament_id IS NOT NULL THEN 'TOURNAMENT'
    WHEN is_custom_room THEN 'CUSTOM'
    WHEN is_ai_match THEN 'AI'
    WHEN is_ranked THEN 'RANKED'
    ELSE 'CASUAL'
END;

CREATE INDEX IF NOT EXISTS idx_matches_created_at_desc
    ON matches(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_matches_source_created
    ON matches(match_source, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_matches_tournament_id
    ON matches(tournament_id)
    WHERE tournament_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS match_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches(match_id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(64) NOT NULL,
    is_bot BOOLEAN NOT NULL DEFAULT false,
    team_id VARCHAR(16) NOT NULL CHECK (team_id IN ('TEAM_A', 'TEAM_B')),
    seat_index INT NOT NULL DEFAULT 0,
    won BOOLEAN NOT NULL DEFAULT false,
    primary_faction VARCHAR(16) CHECK (primary_faction IS NULL OR primary_faction IN ('FIRE', 'ICE', 'SHADOW', 'LIGHT')),
    final_lp INT,
    mmr_delta INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_match_participants_seat UNIQUE (match_id, seat_index)
);

CREATE INDEX IF NOT EXISTS idx_match_participants_user_recent
    ON match_participants(user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_match_participants_match_seat
    ON match_participants(match_id, seat_index);

-- Backfill Team A participants from existing matches rows
INSERT INTO match_participants (
    match_id, user_id, username, is_bot, team_id, seat_index, won, primary_faction, final_lp, mmr_delta, created_at
)
SELECT
    m.match_id,
    u.id,
    COALESCE(u.username, 'Mage Automate'),
    (u.id IS NULL),
    'TEAM_A',
    (elem.ord - 1)::int * 2,
    (m.winner_team = 'TEAM_A'),
    NULL,
    NULL,
    0,
    m.created_at
FROM matches m
CROSS JOIN LATERAL jsonb_array_elements_text(m.team_a_ids) WITH ORDINALITY AS elem(player_id_text, ord)
LEFT JOIN users u ON u.id::text = elem.player_id_text
ON CONFLICT (match_id, seat_index) DO NOTHING;

-- Backfill Team B participants from existing matches rows
INSERT INTO match_participants (
    match_id, user_id, username, is_bot, team_id, seat_index, won, primary_faction, final_lp, mmr_delta, created_at
)
SELECT
    m.match_id,
    u.id,
    COALESCE(u.username, 'Mage Automate'),
    (u.id IS NULL),
    'TEAM_B',
    ((elem.ord - 1)::int * 2) + 1,
    (m.winner_team = 'TEAM_B'),
    NULL,
    NULL,
    0,
    m.created_at
FROM matches m
CROSS JOIN LATERAL jsonb_array_elements_text(m.team_b_ids) WITH ORDINALITY AS elem(player_id_text, ord)
LEFT JOIN users u ON u.id::text = elem.player_id_text
ON CONFLICT (match_id, seat_index) DO NOTHING;

-- Backfill lightweight overview metadata on existing matches rows
UPDATE matches m
SET match_summary = jsonb_build_object(
    'startingLp', CASE WHEN m.mode_1v1_2v2 = '1V1' THEN 25 ELSE 40 END,
    'teamANames', COALESCE((
        SELECT jsonb_agg(p.username ORDER BY p.seat_index)
        FROM match_participants p
        WHERE p.match_id = m.match_id AND p.team_id = 'TEAM_A'
    ), '[]'::jsonb),
    'teamBNames', COALESCE((
        SELECT jsonb_agg(p.username ORDER BY p.seat_index)
        FROM match_participants p
        WHERE p.match_id = m.match_id AND p.team_id = 'TEAM_B'
    ), '[]'::jsonb),
    'durationSeconds', CASE
        WHEN m.finished_at IS NOT NULL THEN GREATEST(0, EXTRACT(EPOCH FROM (m.finished_at - m.created_at))::int)
        ELSE NULL
    END
)
WHERE m.match_summary = '{}'::jsonb;
