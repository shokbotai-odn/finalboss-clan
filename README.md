# FinalBoss Clan

A RuneLite plugin for OSRS clans with real-time status sharing and drop logging.

## Features

- **Clan Roster** - See online clan members in a sidebar panel
- **Status Sync** - Share your activity status (TOB, COX, TOA, Bossing, Skilling, AFK, etc.)
- **Drop Logging** - Track notable drops and announce them to Discord
- **WOM Integration** - Membership verification via Wise Old Man groups

## Installation

### From Plugin Hub (Recommended)
1. Open RuneLite
2. Go to Configuration (wrench icon) > Plugin Hub
3. Search for "FinalBoss Clan"
4. Click Install

### Manual Installation
1. Clone this repository
2. Build with `./gradlew build`
3. Copy the jar from `build/libs/` to your RuneLite plugins folder

## Configuration

### Plugin Settings
Open RuneLite Configuration and find "FinalBoss Clan" to configure:

| Setting | Description | Default |
|---------|-------------|---------|
| Sync Status | Share your activity status with clan members | Enabled |
| Status Timeout | How long before your status expires (minutes) | 30 |
| Log Drops | Log notable drops to Discord | Enabled |
| Drop Value Threshold | Minimum GP value to log a drop | 1,000,000 |

**Note:** This plugin comes pre-configured for the FinalBoss clan. No additional setup required - just install and play!

## Project Structure

```
finalboss-clan/
├── plugin/         # RuneLite plugin (Java)
├── backend/        # Supabase backend (SQL migrations)
├── bot/            # Discord bot (TypeScript)
└── docs/           # Documentation
```

## Setting Up Your Own Backend

### Prerequisites
- [Supabase](https://supabase.com) account (free tier works)
- [Discord Developer Portal](https://discord.com/developers/applications) application
- [Wise Old Man](https://wiseoldman.net) group for your clan

### 1. Supabase Setup

1. Create a new Supabase project
2. Go to SQL Editor and run the migration in `backend/supabase/migrations/001_initial_schema.sql`
3. Copy your project URL and anon key from Settings > API

### 2. Discord Bot Setup

1. Create a Discord application at the Developer Portal
2. Add a Bot to your application and copy the token
3. Copy `.env.example` to `.env` in the `bot/` directory
4. Fill in your credentials:
   ```
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   DISCORD_BOT_TOKEN=your-bot-token
   ANNOUNCEMENT_CHANNEL_ID=your-channel-id
   ```
5. Install dependencies and run:
   ```bash
   cd bot
   npm install
   npm run build
   npm start
   ```

### 3. Plugin Configuration

To use your own backend instead of the default FinalBoss clan backend:

1. Fork the plugin repository
2. Update the hardcoded values in `FinalBossConfig.java`:
   - `womGroupId()` - Your clan's WOM group ID
   - `apiUrl()` - Your Supabase project URL
   - `apiKey()` - Your Supabase anon key
3. Build and distribute your customized plugin

## Development

### Prerequisites
- JDK 21 (for development)
- Node.js 18+ (for Discord bot)

### Building the Plugin

```bash
cd plugin
./gradlew build
```

### Running Locally

Test with RuneLite's developer mode:

```bash
cd plugin
./gradlew run
```

### Running the Discord Bot

```bash
cd bot
npm install
npm run dev
```

## Plugin Hub Compliance

This plugin follows RuneLite Plugin Hub guidelines:
- No automation or gameplay actions
- Uses only official RuneLite APIs
- All external connections are documented
- Data collection is opt-in via WOM membership verification

## Troubleshooting

### "Not a clan member" error
- Ensure your RSN is added to your Wise Old Man group
- Check that the WOM Group ID in settings matches your group
- RSN matching handles spaces, underscores, and hyphens equivalently

### "Failed to update status" error
- Verify your API URL and Key are correct in settings
- Check that the Supabase project is accessible
- Ensure the database migrations have been run

### Drops not appearing in Discord
- Verify drop logging is enabled in settings
- Check the drop value threshold
- Ensure the Discord bot is running and connected
- Verify the announcement channel ID is correct

### Status not syncing
- Ensure you're logged into OSRS
- Check that status sync is enabled
- Verify WOM membership verification passed (green checkmark in panel)

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License

BSD 2-Clause License - see [LICENSE](LICENSE)

## Credits

- [RuneLite](https://runelite.net) - OSRS client
- [Wise Old Man](https://wiseoldman.net) - Clan tracking
- [Supabase](https://supabase.com) - Backend infrastructure
