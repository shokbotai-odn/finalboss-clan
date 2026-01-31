# TODO - FinalBoss Clan Plugin

**Current Phase:** 1 - Foundation  
**Last Updated:** 2026-01-31

---

## 🔴 In Progress

- [ ] Create GitHub repo (shokbotai/finalboss-clan)
- [ ] Scaffold RuneLite plugin skeleton
- [ ] Set up Supabase project
- [ ] Scaffold Discord bot

## 🟡 Up Next

- [ ] Basic FinalBossPlugin.java with panel registration
- [ ] FinalBossPanel.java showing placeholder UI
- [ ] ClanRosterService.java listening to clan events
- [ ] Display clan members in panel (no backend yet)

## 🟢 Completed

- [x] Project documentation (PROJECT.md, TODO.md, DECISIONS.md)
- [x] Design document saved (DESIGN.html)
- [x] Directory structure created

## 📋 Backlog

### Plugin
- [ ] FinalBossConfig.java with all settings
- [ ] MemberRow.java component
- [ ] StatusSyncService.java
- [ ] DropService.java
- [ ] SessionService.java
- [ ] ApiClient.java (Supabase HTTP client)
- [ ] Status chip UI
- [ ] Ping button functionality
- [ ] Drop detection from chat
- [ ] Session controls UI

### Backend (Supabase)
- [ ] Create Supabase project
- [ ] Database schema (users, statuses, drops, sessions)
- [ ] Row Level Security policies
- [ ] Discord OAuth2 edge function
- [ ] RSN verification edge function
- [ ] Status API edge functions
- [ ] Drops API edge functions
- [ ] Sessions API edge functions
- [ ] Real-time subscriptions setup

### Discord Bot
- [ ] Bot application setup in Discord Developer Portal
- [ ] discord.js skeleton
- [ ] Event handlers (ready, messageCreate)
- [ ] Announcement service
- [ ] DM ping service
- [ ] Slash commands (/status, /drops, /verify)
- [ ] Supabase client integration

### DevOps
- [ ] GitHub Actions CI for plugin
- [ ] Bot hosting (Railway/Fly.io)
- [ ] Environment variable management

---

## 🐛 Known Issues

(none yet)

## 💡 Ideas / Nice-to-Have

- [ ] Web dashboard for stats
- [ ] Mobile-friendly status page
- [ ] Historical drop charts
- [ ] Leaderboards

---

*Update this file as tasks are completed or added.*
