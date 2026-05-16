-- Add visibility column to repertoires (default PRIVATE)
ALTER TABLE repertoires ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';

-- Create bookmarks table
CREATE TABLE repertoire_bookmarks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repertoire_id UUID NOT NULL REFERENCES repertoires(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, repertoire_id)
);

-- Index for fast lookups
CREATE INDEX idx_repertoires_visibility ON repertoires(visibility);
CREATE INDEX idx_bookmarks_user ON repertoire_bookmarks(user_id);
CREATE INDEX idx_bookmarks_repertoire ON repertoire_bookmarks(repertoire_id);