# TODO - FinalBoss Clan Plugin

**Current Phase:** 1 - Foundation  
**Last Updated:** 2026-01-31

---

## 🔴 In Progress

- [ ] Test Discord OAuth2 flow
- [ ] Scaffold Discord bot
- [ ] Test plugin builds with Gradle
- [ ] Wire up plugin login button to OAuth flow

## 🟡 Up Next

- [ ] Create Supabase project and get credentials
- [ ] Design database schema (users, statuses, drops, sessions)
- [ ] Create Discord application in Developer Portal
- [ ] Scaffold Discord bot with discord.js
- [ ] Implement ApiClient.java in plugin for backend calls

## 🟢 Completed

- [x] Project documentation (PROJECT.md, TODO.md, DECISIONS.md)
- [x] Design document saved (DESIGN.html)
- [x] Directory structure created
- [x] GitHub repo created (shokbotai-odn/finalboss-clan)
- [x] RuneLite plugin skeleton
  - [x] build.gradle with proper Java 11 target
  - [x] FinalBossPlugin.java (main entry, event handlers)
  - [x] FinalBossConfig.java (all settings sections)
  - [x] ClanRosterService.java (clan member access)
  - [x] FinalBossPanel.java (sidebar UI with member list)
- [x] Supabase project created + database schema deployed
- [x] ApiClient.java for Supabase HTTP calls
- [x] StatusRecord.java and DropRecord.java models
- [x] Discord application created
- [x] Discord OAuth2 configured in Supabase

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
