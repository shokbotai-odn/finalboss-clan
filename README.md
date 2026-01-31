# FinalBoss Clan

A RuneLite Plugin Hub plugin for the Final Boss OSRS clan.

## Features

- **Clan Roster** - See online clan members in a sidebar panel
- **Status Sync** - Share your activity status (TOB, Bossing, Skilling, AFK, etc.)
- **Discord Integration** - Authentication and notifications via Discord
- **Drop Logging** - Track notable drops across the clan
- **Sessions** - Group loot tracking with split calculations

## Project Structure

```
finalboss-clan/
├── plugin/         # RuneLite plugin (Java/Gradle)
├── backend/        # Supabase project (SQL, Edge Functions)
├── bot/            # Discord bot (TypeScript)
└── docs/           # Documentation
```

## Development

### Prerequisites

- JDK 21 (for development)
- Node.js 18+ (for Discord bot)
- Supabase account

### Building the Plugin

```bash
cd plugin
./gradlew build
```

### Running Locally

The plugin can be tested with RuneLite's developer mode:

```bash
./gradlew runClient
```

## Plugin Hub Compliance

This plugin is designed to be Plugin Hub compliant:
- No automation or gameplay actions
- Uses only official RuneLite APIs
- All external connections are documented
- Data collection is opt-in via authentication

## License

BSD 2-Clause License

## Contributing

Contributions welcome! Please read the project documentation in `PROJECT.md` first.
