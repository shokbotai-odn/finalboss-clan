/**
 * FinalBoss Clan Discord Bot
 *
 * A real-time Discord bot that announces notable loot drops from the
 * FinalBoss clan's RuneLite plugin.
 *
 * ============================================================================
 * ARCHITECTURE OVERVIEW
 * ============================================================================
 *
 * Data Flow:
 *   RuneLite Plugin -> Supabase (drops table) -> Real-time subscription -> Discord
 *
 * Components:
 *   1. Discord Client (discord.js v14)
 *      - Connects to Discord with minimal intents
 *      - Posts formatted embed messages to announcement channel
 *
 *   2. Supabase Real-time Subscription
 *      - Listens to INSERT events on the 'drops' table
 *      - Triggers announceDrop() for each new drop
 *
 * ============================================================================
 * FEATURES
 * ============================================================================
 *
 * Current:
 *   - Real-time drop announcements with gold-colored embeds
 *   - GP value formatting (K/M/B suffixes)
 *   - Graceful shutdown handling
 *
 * Planned:
 *   - DM pings for clan members
 *   - Slash commands for status/drops/verify
 *   - Discord OAuth integration
 *
 * ============================================================================
 * CONFIGURATION (Environment Variables)
 * ============================================================================
 *
 * Required:
 *   DISCORD_BOT_TOKEN        - Discord bot token from Developer Portal
 *
 * Optional:
 *   SUPABASE_URL            - Supabase project URL
 *   SUPABASE_ANON_KEY       - Supabase anonymous key
 *   ANNOUNCEMENT_CHANNEL_ID - Discord channel ID for drop announcements
 *
 * ============================================================================
 * RUNNING THE BOT
 * ============================================================================
 *
 *   npm run dev    # Development with hot reload (ts-node)
 *   npm start      # Production (requires build first)
 *
 */

import { Client, GatewayIntentBits, TextChannel, EmbedBuilder } from 'discord.js';
import { createClient, SupabaseClient, RealtimeChannel } from '@supabase/supabase-js';
import * as dotenv from 'dotenv';

// ============================================================================
// CONFIGURATION
// ============================================================================

// Load environment variables from ../.env (relative to bot/ directory)
dotenv.config({ path: '../.env' });

// Discord bot token (required) - obtain from Discord Developer Portal
const DISCORD_TOKEN = process.env.DISCORD_BOT_TOKEN;

// Supabase configuration - connects to the clan's backend database
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://snpfnlvfxpfqryvzksrp.supabase.co';
const SUPABASE_KEY = process.env.SUPABASE_ANON_KEY || '';

// Discord channel ID where drop announcements are posted
const ANNOUNCEMENT_CHANNEL_ID = process.env.ANNOUNCEMENT_CHANNEL_ID || '';

// Validate required configuration
if (!DISCORD_TOKEN) {
    console.error('❌ DISCORD_BOT_TOKEN is required in .env');
    process.exit(1);
}

// ============================================================================
// CLIENT INITIALIZATION
// ============================================================================

/**
 * Discord.js client with minimal required intents:
 * - Guilds: Required for basic guild (server) functionality
 * - GuildMessages: Required to send messages to channels
 * - MessageContent: Required to read message content (for future commands)
 */
const discord = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent,
    ],
});

/**
 * Supabase client for database access and real-time subscriptions.
 * Uses the anonymous key which has limited permissions (defined by RLS policies).
 */
const supabase: SupabaseClient = createClient(SUPABASE_URL, SUPABASE_KEY);

/**
 * Active real-time subscription to the drops table.
 * Stored here so we can clean it up on shutdown.
 */
let dropsSubscription: RealtimeChannel | null = null;

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Format GP (gold piece) values with human-readable suffixes.
 *
 * Examples:
 *   1,500,000,000 -> "1.50B"
 *   50,000,000    -> "50.0M"
 *   100,000       -> "100K"
 *   500           -> "500"
 *
 * @param value - GP value to format
 * @returns Formatted string with appropriate suffix
 */
function formatGp(value: number): string {
    if (value >= 1_000_000_000) {
        return `${(value / 1_000_000_000).toFixed(2)}B`;  // Billions
    } else if (value >= 1_000_000) {
        return `${(value / 1_000_000).toFixed(1)}M`;      // Millions
    } else if (value >= 1_000) {
        return `${(value / 1_000).toFixed(0)}K`;         // Thousands
    }
    return `${value}`;                                    // Raw number
}

// ============================================================================
// EMBED BUILDERS
// ============================================================================

/**
 * Create a Discord embed for announcing a notable drop.
 *
 * Embed Layout:
 *   Title: "🎉 Notable Drop!"
 *   Fields (inline):
 *     - Player: RSN of player who got the drop
 *     - Item: Name of the item
 *     - Value: Formatted GP value
 *     - Source: Boss/activity name
 *     - Quantity: (only shown if > 1)
 *   Footer: "FinalBoss Clan"
 *   Color: Gold (#FFD700)
 *
 * @param drop - Drop record from Supabase
 * @returns Discord EmbedBuilder ready to send
 */
function createDropEmbed(drop: any): EmbedBuilder {
    const embed = new EmbedBuilder()
        .setColor(0xFFD700) // Gold color - matches OSRS gold/loot theme
        .setTitle('🎉 Notable Drop!')
        .addFields(
            { name: 'Player', value: drop.rsn || 'Unknown', inline: true },
            { name: 'Item', value: drop.item_name || 'Unknown', inline: true },
            { name: 'Value', value: formatGp(drop.value || 0), inline: true },
            { name: 'Source', value: drop.source || 'Unknown', inline: true },
        )
        .setTimestamp()
        .setFooter({ text: 'FinalBoss Clan' });

    // Only show quantity if more than 1 (e.g., noted items, stackables)
    if (drop.quantity > 1) {
        embed.addFields({ name: 'Quantity', value: `${drop.quantity}`, inline: true });
    }

    return embed;
}

// ============================================================================
// DROP ANNOUNCEMENT
// ============================================================================

/**
 * Announce a drop to the configured Discord channel.
 *
 * This function:
 * 1. Validates the announcement channel is configured
 * 2. Fetches the channel from Discord
 * 3. Verifies it's a text channel (not voice, DM, etc.)
 * 4. Sends the embed message
 *
 * Errors are logged but not thrown (fire-and-forget pattern).
 *
 * @param drop - Drop record from Supabase real-time event
 */
async function announceDrop(drop: any): Promise<void> {
    // Skip if no announcement channel is configured
    if (!ANNOUNCEMENT_CHANNEL_ID) {
        console.log('⚠️ No announcement channel configured, skipping');
        return;
    }

    try {
        // Fetch the channel from Discord
        const channel = await discord.channels.fetch(ANNOUNCEMENT_CHANNEL_ID);

        // Verify it's a text channel we can send messages to
        if (!channel || !(channel instanceof TextChannel)) {
            console.error('❌ Announcement channel not found or not a text channel');
            return;
        }

        // Build and send the embed
        const embed = createDropEmbed(drop);
        await channel.send({ embeds: [embed] });

        console.log(`✅ Announced drop: ${drop.item_name} by ${drop.rsn}`);
    } catch (error) {
        console.error('❌ Failed to announce drop:', error);
    }
}

// ============================================================================
// SUPABASE REAL-TIME SUBSCRIPTION
// ============================================================================

/**
 * Subscribe to real-time drop notifications from Supabase.
 *
 * This sets up a PostgreSQL change listener on the 'drops' table:
 * - Listens only for INSERT events (new drops)
 * - Triggers announceDrop() for each new record
 * - Logs subscription status changes
 *
 * The subscription uses Supabase's real-time infrastructure which
 * maintains a persistent WebSocket connection to the database.
 */
function subscribeToDrops(): void {
    console.log('📡 Subscribing to drop notifications...');

    dropsSubscription = supabase
        .channel('drops')  // Channel name (can be anything, used for unsubscribing)
        .on(
            'postgres_changes',  // Listen to PostgreSQL changes
            {
                event: 'INSERT',      // Only new rows (not UPDATE/DELETE)
                schema: 'public',     // Public schema
                table: 'drops',       // The drops table
            },
            (payload) => {
                // payload.new contains the inserted row
                console.log('🎁 New drop received:', payload.new);
                announceDrop(payload.new);
            }
        )
        .subscribe((status) => {
            // Status: SUBSCRIBED, TIMED_OUT, CLOSED, CHANNEL_ERROR
            console.log(`📡 Subscription status: ${status}`);
        });
}

// ============================================================================
// DISCORD EVENT HANDLERS
// ============================================================================

/**
 * 'ready' event - Fired once when the bot successfully connects to Discord.
 * This is where we initialize the Supabase real-time subscription.
 */
discord.once('ready', () => {
    console.log(`✅ Logged in as ${discord.user?.tag}`);
    console.log(`📊 Serving ${discord.guilds.cache.size} guild(s)`);

    // Start listening for drops once Discord connection is established
    subscribeToDrops();
});

/**
 * 'error' event - Fired when Discord.js encounters an error.
 * Logs the error for debugging; connection errors trigger automatic reconnect.
 */
discord.on('error', (error) => {
    console.error('❌ Discord error:', error);
});

// ============================================================================
// GRACEFUL SHUTDOWN
// ============================================================================

/**
 * Handle SIGINT (Ctrl+C) for graceful shutdown.
 *
 * Cleanup steps:
 * 1. Unsubscribe from Supabase real-time channel
 * 2. Destroy Discord client connection
 * 3. Exit process
 */
process.on('SIGINT', async () => {
    console.log('\n🛑 Shutting down...');

    // Clean up Supabase real-time subscription
    if (dropsSubscription) {
        await supabase.removeChannel(dropsSubscription);
    }

    // Disconnect from Discord
    discord.destroy();

    process.exit(0);
});

// ============================================================================
// ENTRY POINT
// ============================================================================

console.log('🚀 Starting FinalBoss Bot...');

// Authenticate with Discord and start the bot
// The 'ready' event handler will trigger once connected
discord.login(DISCORD_TOKEN);
