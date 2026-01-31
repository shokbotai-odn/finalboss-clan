# Architecture Decisions Log

Track important decisions so future sessions understand *why* things are the way they are.

---

## ADR-001: Use Supabase for Backend

**Date:** 2026-01-31  
**Status:** Accepted

**Context:**  
Need a backend for auth, real-time sync, and data storage. Options: AWS, DigitalOcean, Firebase, Supabase.

**Decision:**  
Use Supabase.

**Rationale:**
- Built-in Discord OAuth2 support via Auth
- Real-time subscriptions out of the box (no WebSocket server needed)
- PostgreSQL with Row Level Security (proper auth)
- Edge Functions for custom API logic
- Generous free tier
- Orgo's preference

**Consequences:**
- Locked into Supabase ecosystem (acceptable)
- Need to learn Supabase patterns if unfamiliar

---

## ADR-002: Plugin Hub Compliance Strategy

**Date:** 2026-01-31  
**Status:** Accepted

**Context:**  
Must pass RuneLite Plugin Hub review. Strict rules about external connectivity and automation.

**Decision:**  
- NO gameplay automation whatsoever
- Clearly document all external connections in plugin description
- Use only official RuneLite APIs
- Minimal data collection (only what's needed)

**Rationale:**
- Plugin Hub is the only way to distribute widely
- Violating rules = rejection or ban
- Being transparent builds trust

**Consequences:**
- Some "nice to have" features may not be possible
- Must be careful about what data we send externally

---

## ADR-003: RSN Verification via In-Game Code

**Date:** 2026-01-31  
**Status:** Accepted

**Context:**  
Need to bind Discord accounts to RSNs securely to prevent spoofing.

**Decision:**  
Use in-game verification codes typed into clan chat.

**Flow:**
1. User authenticates with Discord
2. Backend generates short code (e.g., LINK-4F7K2)
3. User types code in clan chat
4. Plugin detects own message with code
5. Plugin confirms to backend → binding complete

**Rationale:**
- Only the actual account owner can type in that game session
- No external APIs needed (Jagex doesn't provide RSN verification)
- Simple UX

**Consequences:**
- Requires clan chat access (not a problem for clan members)
- One-time verification per RSN

---

## ADR-004: TypeScript for Discord Bot

**Date:** 2026-01-31  
**Status:** Accepted

**Context:**  
Need to choose language for Discord bot.

**Decision:**  
TypeScript with discord.js

**Rationale:**
- discord.js is the most popular, well-maintained library
- TypeScript catches errors at compile time
- Good ecosystem (Supabase JS client works well)
- Easy to host anywhere (Railway, Fly.io, etc.)

**Consequences:**
- Need Node.js runtime
- Orgo may need to learn some TS basics

---

## ADR-005: Project Structure - Monorepo

**Date:** 2026-01-31  
**Status:** Accepted

**Context:**  
Three components: plugin, backend, bot. Separate repos or monorepo?

**Decision:**  
Monorepo with subdirectories.

**Rationale:**
- Easier to keep in sync
- Single source of truth
- Shared documentation
- Simpler for a small team/solo dev

**Consequences:**
- Need to be careful with CI (only build what changed)
- Git history is shared

---

*Add new decisions as they come up. Format: ADR-XXX: Title*
