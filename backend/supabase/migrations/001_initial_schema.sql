-- FinalBoss Clan - Initial Database Schema
-- Run this in Supabase SQL Editor (Dashboard → SQL Editor → New Query)
--
-- This creates all the tables needed for the plugin:
-- 1. users - Discord to RSN mapping
-- 2. statuses - Activity status tracking  
-- 3. drops - Loot logging
-- 4. sessions - Group loot sessions
-- 5. session_participants - Who's in each session

-- ============================================================================
-- USERS TABLE
-- Links Discord accounts to RuneScape names (RSNs)
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discord_id TEXT UNIQUE NOT NULL,
    rsn TEXT,
    verified BOOLEAN DEFAULT FALSE,
    verification_code TEXT,
    verification_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for fast RSN lookups
CREATE INDEX IF NOT EXISTS idx_users_rsn ON users(rsn);

-- ============================================================================
-- STATUSES TABLE
-- Tracks what each user is doing (TOB, Bossing, AFK, etc.)
-- ============================================================================

CREATE TABLE IF NOT EXISTS statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    note TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    
    -- Only one status per user
    UNIQUE(user_id)
);

-- Index for finding active (non-expired) statuses
CREATE INDEX IF NOT EXISTS idx_statuses_expires ON statuses(expires_at);

-- ============================================================================
-- DROPS TABLE
-- Logs notable drops for tracking and announcements
-- ============================================================================

CREATE TABLE IF NOT EXISTS drops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    rsn TEXT NOT NULL,
    item_id INTEGER NOT NULL,
    item_name TEXT NOT NULL,
    quantity INTEGER DEFAULT 1,
    value BIGINT,  -- GP value (use BIGINT for expensive items)
    source TEXT,   -- e.g., "Theatre of Blood", "Zulrah"
    session_id UUID,  -- Optional: links to a group session
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for finding drops by user
CREATE INDEX IF NOT EXISTS idx_drops_user ON drops(user_id);
-- Index for finding drops by session
CREATE INDEX IF NOT EXISTS idx_drops_session ON drops(session_id);
-- Index for recent drops
CREATE INDEX IF NOT EXISTS idx_drops_created ON drops(created_at DESC);

-- ============================================================================
-- SESSIONS TABLE
-- Group loot tracking sessions for split calculations
-- ============================================================================

CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leader_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    activity TEXT NOT NULL,  -- e.g., "TOB", "COX", "Nex"
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    total_value BIGINT DEFAULT 0,  -- Calculated when session ends
    notes TEXT
);

-- Index for finding active sessions
CREATE INDEX IF NOT EXISTS idx_sessions_active ON sessions(ended_at) WHERE ended_at IS NULL;

-- ============================================================================
-- SESSION PARTICIPANTS TABLE
-- Who participated in each session (for split calculations)
-- ============================================================================

CREATE TABLE IF NOT EXISTS session_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Each user can only be in a session once
    UNIQUE(session_id, user_id)
);

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- Ensures users can only access their own data
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE statuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE drops ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_participants ENABLE ROW LEVEL SECURITY;

-- Users: Anyone can read (for roster display), only owner can update
CREATE POLICY "Users are viewable by everyone" ON users
    FOR SELECT USING (true);

CREATE POLICY "Users can update own record" ON users
    FOR UPDATE USING (auth.uid()::text = discord_id);

-- Statuses: Anyone can read (for status display)
CREATE POLICY "Statuses are viewable by everyone" ON statuses
    FOR SELECT USING (true);

CREATE POLICY "Users can manage own status" ON statuses
    FOR ALL USING (user_id IN (SELECT id FROM users WHERE discord_id = auth.uid()::text));

-- Drops: Anyone can read (for leaderboards), owner can insert
CREATE POLICY "Drops are viewable by everyone" ON drops
    FOR SELECT USING (true);

CREATE POLICY "Users can insert own drops" ON drops
    FOR INSERT WITH CHECK (user_id IN (SELECT id FROM users WHERE discord_id = auth.uid()::text));

-- Sessions: Anyone can read
CREATE POLICY "Sessions are viewable by everyone" ON sessions
    FOR SELECT USING (true);

CREATE POLICY "Users can manage own sessions" ON sessions
    FOR ALL USING (leader_user_id IN (SELECT id FROM users WHERE discord_id = auth.uid()::text));

-- Session participants: Anyone can read
CREATE POLICY "Session participants are viewable by everyone" ON session_participants
    FOR SELECT USING (true);

-- ============================================================================
-- FUNCTIONS
-- Helper functions for common operations
-- ============================================================================

-- Function to clean up expired statuses (run via cron)
CREATE OR REPLACE FUNCTION cleanup_expired_statuses()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM statuses WHERE expires_at < NOW();
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate session total when ended
CREATE OR REPLACE FUNCTION calculate_session_total(session_uuid UUID)
RETURNS BIGINT AS $$
DECLARE
    total BIGINT;
BEGIN
    SELECT COALESCE(SUM(value * quantity), 0) INTO total
    FROM drops
    WHERE session_id = session_uuid;
    
    UPDATE sessions SET total_value = total WHERE id = session_uuid;
    RETURN total;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- REALTIME
-- Enable realtime for status updates
-- ============================================================================

-- Note: Run this in Supabase Dashboard → Database → Replication
-- Or use: ALTER PUBLICATION supabase_realtime ADD TABLE statuses;

-- ============================================================================
-- DONE!
-- ============================================================================

-- Verify tables were created
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('users', 'statuses', 'drops', 'sessions', 'session_participants');
