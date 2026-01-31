# FinalBoss Clan - RuneLite Plugin

**Status:** 🚧 IN PROGRESS  
**Started:** 2026-01-31  
**Last Updated:** 2026-01-31  
**Owner:** Orgo (shokbotai)

---

## 📋 What This Is

A Plugin Hub compliant RuneLite plugin for the Final Boss OSRS clan. Provides:
- Sidebar panel showing online clan members
- Real-time activity status sharing (TOB, Bossing, Skilling, AFK, etc.)
- Discord integration (OAuth2 auth, RSN verification, announcements, DM pings)
- Drop logging with optional Discord announcements
- Session-based group loot tracking with split calculations

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FINAL BOSS SYSTEM                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  RuneLite   │◄──►│  Supabase   │◄──►│  Discord    │     │
│  │   Plugin    │    │   Backend   │    │    Bot      │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│        │                   │                  │             │
│   - UI Panel          - Auth (Discord)   - Announcements   │
│   - Clan Events       - RSN Binding      - DM Pings        │
│   - Status Set        - Status Sync      - Slash Commands  │
│   - Drop Capture      - Drop Storage     - Verification    │
│   - Session UX        - Sessions/Splits                    │
│                       - Real-time (WS)                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 📁 Repository Structure

```
finalboss-clan/
├── PROJECT.md              # This file - project status & context
├── DESIGN.md               # Full design document
├── DECISIONS.md            # Architecture decisions log
├── TODO.md                 # Current tasks and progress
├── plugin/                 # RuneLite plugin (Java/Gradle)
│   └── (to be scaffolded)
├── backend/                # Supabase project (SQL, Edge Functions)
│   └── (to be scaffolded)
├── bot/                    # Discord bot (TypeScript/Node)
│   └── (to be scaffolded)
└── docs/                   # Additional documentation
```

## 🔧 Tech Stack

| Component | Technology | Why |
|-----------|------------|-----|
| Plugin | Java 11 (target), JDK 21 (dev), Gradle | RuneLite requirement |
| Backend | Supabase (Postgres + Auth + Realtime + Edge Functions) | All-in-one, free tier, real-time built in |
| Bot | TypeScript + discord.js | Modern, type-safe, good DX |
| Hosting | Supabase (backend) + Railway/Fly.io (bot) | Simple, cheap |

## 🎯 Plugin Hub Compliance

**Must follow:**
- No automation or gameplay actions
- No input generation, prayer switching, click helpers
- Uses official RuneLite APIs only
- Transparent about external connectivity in description
- Minimal data collection with clear purpose

## 📊 Progress Tracker

### Phase 1: Foundation
- [x] Project setup and documentation
- [ ] GitHub repo created (shokbotai/finalboss-clan)
- [ ] RuneLite plugin skeleton
- [ ] Supabase project created
- [ ] Discord bot skeleton

### Phase 2: Core Plugin
- [ ] FinalBossPlugin.java (main entry)
- [ ] FinalBossConfig.java (settings)
- [ ] FinalBossPanel.java (sidebar UI)
- [ ] ClanRosterService.java (clan events)
- [ ] Basic panel showing clan members

### Phase 3: Backend Integration
- [ ] Supabase schema (users, statuses, drops, sessions)
- [ ] Discord OAuth2 flow
- [ ] RSN verification system
- [ ] ApiClient.java in plugin

### Phase 4: Status System
- [ ] Status sync service
- [ ] Real-time updates (Supabase Realtime)
- [ ] Status UI in panel

### Phase 5: Discord Bot
- [ ] Bot skeleton with discord.js
- [ ] Announcement system
- [ ] DM ping handling
- [ ] Slash commands

### Phase 6: Drops & Sessions
- [ ] Drop detection and logging
- [ ] Session management
- [ ] Split calculations
- [ ] Session UI

### Phase 7: Polish & Submit
- [ ] Testing
- [ ] Plugin Hub submission
- [ ] Documentation

## 🔑 Keys & Secrets (DO NOT COMMIT)

All secrets stored in:
- Plugin: RuneLite config (user's local)
- Backend: Supabase dashboard (environment variables)
- Bot: .env file (gitignored)

**Required secrets:**
- `SUPABASE_URL` - Supabase project URL
- `SUPABASE_ANON_KEY` - Supabase anon/public key
- `SUPABASE_SERVICE_KEY` - Supabase service role key (backend only)
- `DISCORD_CLIENT_ID` - Discord OAuth2 app ID
- `DISCORD_CLIENT_SECRET` - Discord OAuth2 secret
- `DISCORD_BOT_TOKEN` - Discord bot token

## 🧠 Context for Future Sessions

If you're picking this up mid-project:
1. Read this PROJECT.md first
2. Check TODO.md for current tasks
3. Check DECISIONS.md for why things are the way they are
4. The DESIGN.md has the full original spec

## 📝 Session Log

### 2026-01-31 - Project Kickoff
- Orgo sent design doc via iMessage
- Created project structure
- Setting up GitHub repo under shokbotai
- Next: Scaffold plugin, Supabase, and bot

---

*This file is the source of truth. Update it as progress is made.*
