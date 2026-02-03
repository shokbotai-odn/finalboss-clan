# FinalBoss Clan

A RuneLite plugin for the FinalBoss OSRS clan with real-time status sharing and drop logging.

## Features

- **Clan Roster** - See online clan members in a sidebar panel
- **Status Sync** - Share your activity status (TOB, COX, TOA, Bossing, Skilling, AFK, etc.)
- **Drop Logging** - Track notable drops and announce them to Discord
- **WOM Integration** - Membership verification via Wise Old Man

## Installation

### From Plugin Hub
1. Open RuneLite
2. Go to Configuration (wrench icon) > Plugin Hub
3. Search for "FinalBoss Clan"
4. Click Install

## Configuration

Open RuneLite Configuration and find "FinalBoss Clan":

| Setting | Description | Default |
|---------|-------------|---------|
| Sync Status | Share your activity status with clan members | Enabled |
| Status Timeout | How long before your status expires (minutes) | 30 |
| Log Drops | Log notable drops to Discord | Enabled |
| Drop Value Threshold | Minimum GP value to log a drop | 1,000,000 |

## Development

### Prerequisites
- JDK 11+

### Running Locally

```bash
./gradlew run
```

This launches RuneLite in developer mode with the plugin loaded.

### Building

```bash
./gradlew build
```

## Troubleshooting

### "Not a clan member" error
- Ensure your RSN is added to the Wise Old Man group
- RSN matching handles spaces, underscores, and hyphens equivalently

### Drops not appearing in Discord
- Verify drop logging is enabled in settings
- Check the drop value threshold

### Status not syncing
- Ensure you're logged into OSRS
- Check that status sync is enabled
- Verify WOM membership passed (green checkmark in panel)

## License

BSD 2-Clause License

## Credits

- [RuneLite](https://runelite.net)
- [Wise Old Man](https://wiseoldman.net)
- [Supabase](https://supabase.com)
