/**
 * FinalBoss Clan Discord Bot
 * 
 * WHAT THIS DOES:
 * - Announces notable drops to a Discord channel
 * - Sends DM pings when clan members request
 * - Provides slash commands for status/drops/verify
 * 
 * HOW IT WORKS:
 * - Connects to Discord via discord.js
 * - Subscribes to Supabase real-time for drop notifications
 * - Posts formatted messages to configured channel
 */

import { Client, GatewayIntentBits, TextChannel, EmbedBuilder } from 'discord.js';
import { createClient, SupabaseClient, RealtimeChannel } from '@supabase/supabase-js';
import * as dotenv from 'dotenv';

// Load environment variables
dotenv.config({ path: '../.env' });

// Configuration
const DISCORD_TOKEN = process.env.DISCORD_BOT_TOKEN;
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://snpfnlvfxpfqryvzksrp.supabase.co';
const SUPABASE_KEY = process.env.SUPABASE_ANON_KEY || '';
const ANNOUNCEMENT_CHANNEL_ID = process.env.ANNOUNCEMENT_CHANNEL_ID || '';

// Validate required config
if (!DISCORD_TOKEN) {
    console.error('❌ DISCORD_BOT_TOKEN is required in .env');
    process.exit(1);
}

// Initialize Discord client
const discord = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent,
    ],
});

// Initialize Supabase client
const supabase: SupabaseClient = createClient(SUPABASE_URL, SUPABASE_KEY);

// Real-time subscription
let dropsSubscription: RealtimeChannel | null = null;

/**
 * Format GP value with K/M/B suffix
 */
function formatGp(value: number): string {
    if (value >= 1_000_000_000) {
        return `${(value / 1_000_000_000).toFixed(2)}B`;
    } else if (value >= 1_000_000) {
        return `${(value / 1_000_000).toFixed(1)}M`;
    } else if (value >= 1_000) {
        return `${(value / 1_000).toFixed(0)}K`;
    }
    return `${value}`;
}

/**
 * Create a drop announcement embed
 */
function createDropEmbed(drop: any): EmbedBuilder {
    const embed = new EmbedBuilder()
        .setColor(0xFFD700) // Gold color
        .setTitle('🎉 Notable Drop!')
        .addFields(
            { name: 'Player', value: drop.rsn || 'Unknown', inline: true },
            { name: 'Item', value: drop.item_name || 'Unknown', inline: true },
            { name: 'Value', value: formatGp(drop.value || 0), inline: true },
            { name: 'Source', value: drop.source || 'Unknown', inline: true },
        )
        .setTimestamp()
        .setFooter({ text: 'FinalBoss Clan' });

    if (drop.quantity > 1) {
        embed.addFields({ name: 'Quantity', value: `${drop.quantity}`, inline: true });
    }

    return embed;
}

/**
 * Announce a drop to the configured channel
 */
async function announceDrop(drop: any): Promise<void> {
    if (!ANNOUNCEMENT_CHANNEL_ID) {
        console.log('⚠️ No announcement channel configured, skipping');
        return;
    }

    try {
        const channel = await discord.channels.fetch(ANNOUNCEMENT_CHANNEL_ID);
        if (!channel || !(channel instanceof TextChannel)) {
            console.error('❌ Announcement channel not found or not a text channel');
            return;
        }

        const embed = createDropEmbed(drop);
        await channel.send({ embeds: [embed] });
        console.log(`✅ Announced drop: ${drop.item_name} by ${drop.rsn}`);
    } catch (error) {
        console.error('❌ Failed to announce drop:', error);
    }
}

/**
 * Subscribe to real-time drop notifications
 */
function subscribeToDrops(): void {
    console.log('📡 Subscribing to drop notifications...');

    dropsSubscription = supabase
        .channel('drops')
        .on(
            'postgres_changes',
            {
                event: 'INSERT',
                schema: 'public',
                table: 'drops',
            },
            (payload) => {
                console.log('🎁 New drop received:', payload.new);
                announceDrop(payload.new);
            }
        )
        .subscribe((status) => {
            console.log(`📡 Subscription status: ${status}`);
        });
}

// Discord event handlers
discord.once('ready', () => {
    console.log(`✅ Logged in as ${discord.user?.tag}`);
    console.log(`📊 Serving ${discord.guilds.cache.size} guild(s)`);

    // Subscribe to real-time drops
    subscribeToDrops();
});

discord.on('error', (error) => {
    console.error('❌ Discord error:', error);
});

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n🛑 Shutting down...');
    
    if (dropsSubscription) {
        await supabase.removeChannel(dropsSubscription);
    }
    
    discord.destroy();
    process.exit(0);
});

// Start the bot
console.log('🚀 Starting FinalBoss Bot...');
discord.login(DISCORD_TOKEN);
