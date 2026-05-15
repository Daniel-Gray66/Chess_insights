-- Add supabase_id column for Supabase Auth users
ALTER TABLE users ADD COLUMN supabase_id VARCHAR(255) UNIQUE;

-- Make password nullable (OAuth users won't have one)
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Add email column
ALTER TABLE users ADD COLUMN email VARCHAR(255);

-- Add index for supabase_id lookups
CREATE INDEX idx_users_supabase_id ON users(supabase_id);