-- ============================================================================
-- FinalBoss Clan - Database Schema
-- ============================================================================
--
-- This schema defines the database structure for the FinalBoss clan plugin.
-- It provides storage for:
--   1. Player activity statuses (what clan members are doing)
--   2. Notable drop logs (valuable items received from bosses/activities)
--
-- SETUP INSTRUCTIONS:
--   Run this in Supabase SQL Editor (Dashboard → SQL Editor → New Query)
--   After running, enable Realtime for the 'drops' table:
--     Database → Replication → Supabase Realtime → Enable for 'drops'
--
-- ARCHITECTURE NOTES:
--   - Uses RSN (RuneScape Name) directly - no Discord integration required
--   - WOM membership validation happens in the RuneLite plugin
--   - RLS policies allow public read, authenticated write via anon key
--   - The Discord bot subscribes to real-time changes on 'drops' table
--
-- ============================================================================

-- ============================================================================
-- STATUSES TABLE
-- ============================================================================
-- Tracks what each player is currently doing.
--
-- Use cases:
--   - Display clan roster with activity status (TOB, Bossing, AFK, etc.)
--   - Allow clan members to see who is available for activities
--   - Auto-expire statuses to prevent stale data
--
-- Columns:
--   id         - UUID primary key (auto-generated)
--   rsn        - RuneScape name (unique, used as upsert key)
--   status     - Activity status (Available, Bossing, TOB, COX, TOA, Skilling, AFK, Do Not Disturb)
--   note       - Optional details about the activity (e.g., "Farming herbs")
--   updated_at - When the status was last changed
--   expires_at - When this status should auto-expire (null = never)
-- ============================================================================

CREATE TABLE IF NOT EXISTS statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rsn TEXT NOT NULL UNIQUE,          -- RuneScape name (unique constraint for upsert)
    status TEXT NOT NULL,              -- Activity status enum value
    note TEXT,                         -- Optional user note
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ             -- NULL = never expires
);

-- Index for efficiently finding non-expired statuses
-- Used when filtering out expired records in API queries
CREATE INDEX IF NOT EXISTS idx_statuses_expires ON statuses(expires_at);

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) - STATUSES
-- ============================================================================
-- RLS controls access to table rows based on policies.
--
-- Security Model:
--   - Public READ: Anyone can see statuses (needed for roster display)
--   - Anon key WRITE: Insert/update allowed with Supabase anon key
--   - Membership validation is done CLIENT-SIDE in the RuneLite plugin
--     (plugin checks WOM membership before allowing status updates)
--
-- Note: This is a permissive model. For stricter security, consider:
--   - Adding a Discord OAuth flow for verified user authentication
--   - Implementing server-side WOM validation via Edge Functions
-- ============================================================================

ALTER TABLE statuses ENABLE ROW LEVEL SECURITY;

-- Anyone can read statuses (needed for roster display in all clan members' plugins)
CREATE POLICY "Statuses are viewable by everyone" ON statuses
    FOR SELECT USING (true);

-- Allow INSERT with anon key (plugin validates WOM membership before calling)
CREATE POLICY "Allow status upsert with anon key" ON statuses
    FOR INSERT WITH CHECK (true);

-- Allow UPDATE with anon key (for status changes on existing records)
CREATE POLICY "Allow status update with anon key" ON statuses
    FOR UPDATE USING (true);

-- ============================================================================
-- DROPS TABLE
-- ============================================================================
-- Logs notable loot drops for tracking and Discord announcements.
--
-- Use cases:
--   - Discord bot announces new drops in real-time
--   - Track clan loot history for leaderboards
--   - Analyze drop rates and clan activity
--
-- Columns:
--   id         - UUID primary key (auto-generated)
--   rsn        - Player who received the drop
--   item_id    - OSRS item ID (for wiki lookup/icons)
--   item_name  - Display name of the item
--   quantity   - Number of items (usually 1, can be more for stackables)
--   value      - Total GP value (quantity * price)
--   source     - Boss/activity name (e.g., "Vorkath", "Chambers of Xeric")
--   created_at - When the drop was logged
--
-- Real-time Integration:
--   Enable Supabase Realtime on this table to allow the Discord bot
--   to receive instant notifications when new drops are inserted.
--   (Database → Replication → Enable for 'drops')
-- ============================================================================

CREATE TABLE IF NOT EXISTS drops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rsn TEXT NOT NULL,                 -- Player's RuneScape name
    item_id INTEGER NOT NULL,          -- OSRS item ID
    item_name TEXT NOT NULL,           -- Display name
    quantity INTEGER DEFAULT 1,        -- Stack size
    value BIGINT,                      -- Total GP value
    source TEXT,                       -- Boss/activity name
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for fetching recent drops (leaderboards, history)
CREATE INDEX IF NOT EXISTS idx_drops_created ON drops(created_at DESC);

-- Index for player-specific drop queries
CREATE INDEX IF NOT EXISTS idx_drops_rsn ON drops(rsn);

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) - DROPS
-- ============================================================================
-- Similar permissive model to statuses table.
-- Plugin validates WOM membership before logging drops.
-- ============================================================================

ALTER TABLE drops ENABLE ROW LEVEL SECURITY;

-- Anyone can read drops (for leaderboards, history views)
CREATE POLICY "Drops are viewable by everyone" ON drops
    FOR SELECT USING (true);

-- Allow INSERT with anon key (plugin validates WOM membership before calling)
CREATE POLICY "Allow drop insert with anon key" ON drops
    FOR INSERT WITH CHECK (true);

-- ============================================================================
-- DATABASE FUNCTIONS
-- ============================================================================

-- ============================================================================
-- cleanup_expired_statuses()
-- ============================================================================
-- Removes status records that have passed their expiration time.
--
-- Usage:
--   Call periodically via Supabase cron (pg_cron extension) or
--   manually via SQL: SELECT cleanup_expired_statuses();
--
-- Recommended cron schedule: Every 5 minutes
--   SELECT cron.schedule('cleanup-statuses', '*/5 * * * *',
--     'SELECT cleanup_expired_statuses()');
--
-- Returns: Number of deleted records
-- ============================================================================
CREATE OR REPLACE FUNCTION cleanup_expired_statuses()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete all statuses where expires_at is in the past
    DELETE FROM statuses WHERE expires_at < NOW();

    -- Get the number of rows deleted
    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
