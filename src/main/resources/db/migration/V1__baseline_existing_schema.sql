-- V1__baseline_existing_schema.sql
-- Baseline migration for tables already created by Hibernate ddl-auto.
-- These CREATE statements match the existing database schema.
-- If running against an existing database, use flyway baseline to skip this.

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    chess_com_username VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL,
    last_synced_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chess_games (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    chess_com_game_id   VARCHAR(255) NOT NULL,
    played_at           TIMESTAMP NOT NULL,
    time_class          VARCHAR(255) NOT NULL,
    time_control        VARCHAR(255),
    user_color          VARCHAR(255) NOT NULL,
    user_rating         INTEGER NOT NULL,
    opponent_username   VARCHAR(255) NOT NULL,
    opponent_rating     INTEGER NOT NULL,
    result              VARCHAR(255) NOT NULL,
    termination         VARCHAR(255),
    eco_code            VARCHAR(255),
    opening_name        VARCHAR(255),
    pgn                 TEXT,
    num_moves           INTEGER,
    accuracy            DOUBLE PRECISION,
    raw_json            TEXT,
    game_url            VARCHAR(255),
    UNIQUE (user_id, chess_com_game_id)
);

CREATE TABLE IF NOT EXISTS sync_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    status              VARCHAR(255),
    archives_processed  INTEGER,
    games_fetched       INTEGER,
    new_games_saved     INTEGER,
    error_message       TEXT,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP
);