-- V2__create_repertoire_tables.sql
-- Adds opening repertoire support: repertoires, lines, individual moves, and opening reference data.

-- Shared reference table for ECO opening codes
CREATE TABLE openings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eco_code    VARCHAR(5) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    variation   VARCHAR(255),
    pgn         TEXT,
    UNIQUE (eco_code, variation)
);

-- A player's repertoire (e.g. "Black vs 1.d4 - Chigorin QGD")
CREATE TABLE repertoires (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    color       VARCHAR(10) NOT NULL CHECK (color IN ('WHITE', 'BLACK')),
    root_move   VARCHAR(10),
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_repertoires_player ON repertoires(player_id);
CREATE INDEX idx_repertoires_player_color ON repertoires(player_id, color);

-- A specific prepared line within a repertoire
CREATE TABLE repertoire_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repertoire_id   UUID NOT NULL REFERENCES repertoires(id) ON DELETE CASCADE,
    opening_id      UUID REFERENCES openings(id),
    line_name       VARCHAR(255) NOT NULL,
    pgn             TEXT NOT NULL,
    notes           TEXT,
    drill_priority  INTEGER NOT NULL DEFAULT 5,
    times_drilled   INTEGER NOT NULL DEFAULT 0,
    last_drilled_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_rep_lines_repertoire ON repertoire_lines(repertoire_id);
CREATE INDEX idx_rep_lines_drill ON repertoire_lines(repertoire_id, drill_priority DESC, last_drilled_at ASC NULLS FIRST);

-- Individual moves within a line (parsed from PGN for drill mode)
CREATE TABLE line_moves (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id     UUID NOT NULL REFERENCES repertoire_lines(id) ON DELETE CASCADE,
    move_number INTEGER NOT NULL,
    move_uci    VARCHAR(5) NOT NULL,
    move_san    VARCHAR(10) NOT NULL,
    fen_after   VARCHAR(255) NOT NULL,
    annotation  TEXT
);

CREATE INDEX idx_line_moves_line ON line_moves(line_id, move_number);