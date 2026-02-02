# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-02-01

### Added
- Initial release
- Clan roster panel showing online members
- Real-time status sync (Available, Bossing, TOB, COX, TOA, Skilling, AFK, Do Not Disturb)
- Wise Old Man group membership verification
- Drop logging with configurable value threshold
- Discord bot for drop announcements
- Supabase backend with PostgreSQL

### Technical
- Thread-safe API client with caching
- RSN normalization for matching (spaces, underscores, hyphens)
- Automatic status expiration
- Row Level Security on database tables
