# Project WEAVE — Build Plan & TODO

> A social network designed around human connection, not content consumption.
> **The unit is the relationship, not the post.**
>
> Flow: `person → intent → people → shared experience → relationship`

Built on the existing SecureMessage core (Firebase Auth, Firestore, E2EE messaging),
which becomes WEAVE's conversation/session layer. Visual language: the dark, warm,
minimal theme from the mockups (the app's original ObsidianVoid palette, re-tuned).

---

## Principles baked into the build (the "Algorithm Constitution")
- No infinite feed, no public follower/like counts, no streaks, no social ranking.
- Reactions express **intent** (Celebrate / I relate / I can help / Let's talk), not vanity.
- AI is a **social coordinator**, never a fake friend.
- "A great 7-minute session and then leaving" is success, not failure.
- Reciprocity & relationships over broadcasts.

---

## Six core primitives (data model)
- **Person** — human context (interests, availability, boundaries), not a profile.
- **Relationship** — edge between two people with history/reciprocity/layer.
- **Circle** — small recurring group.
- **Space** — broad interest/identity/location community.
- **Moment** — replaces the post; exists to *trigger interaction*.
- **Session** — bounded shared activity (chat/voice/study/play/meet).

---

## Stages (each stage compiles & runs)

### Stage 1 — Foundations: data + theme  ✅ DONE
- [x] WEAVE theme (dark warm minimal) in the design system — `ui/theme/Weave.kt`
- [x] `Person` model (+ availability) — `data/weave/WeaveModels.kt`
- [x] `Relationship` model + layers (Inner/Close/Friends/Acquaintances/Wider)
- [x] `Circle` model + repository
- [x] `Space` model + repository
- [x] `Moment` model (+ intent reactions) + repository
- [x] `Session` model + repository
- [x] Wire new repositories into AppContainer
- [x] Firestore rules for new collections (dry-run validated)
- [x] Compiles clean

### Stage 2 — Shell + Home command center  (mockup #3)  ✅ DONE
- [x] Bottom nav: Home · Explore · ➕ · Messages · Me — dark pill, `WeaveNavBar`
- [x] Home: greeting, Your circles, Happening now (live sessions), From your people (moments), "You're caught up"
- [x] No infinite scroll; bounded sections
- [x] Intent-first Moment composer (the ➕) — share/ask-help/invite/celebrate/looking-for
- [x] Intent reactions on moments (Celebrate / I relate / I can help / Let's talk)
- [x] Routed app entry to WeaveMainScreen (dark theme); Home live, other tabs placeheld
- [x] Built + installed on device

### Stage 3 — Moments  (mockup #4)
- [ ] Moment composer (intent-first)
- [ ] Moment detail + intent reactions (Celebrate / I relate / I can help / Let's talk)
- [ ] Reciprocal response thread

### Stage 4 — Circles & Explore/Spaces  (mockup #7, #8, #9)
- [ ] Circles list + circle detail
- [ ] Explore Spaces (For You / Interests / Location / University)
- [ ] Join / leave

### Stage 5 — Messages + Sessions  (mockup #5, #7)
- [ ] Messages list in WEAVE styling (reuse ConversationsViewModel)
- [ ] Live Session screen (participants, listeners, join/leave)
- [ ] Fold existing E2EE chat into WEAVE look

### Stage 6 — Nearby, AI coordinator, Onboarding, Profile  (#1, #2, #6, #11, #12)
- [ ] Splash + onboarding ("What brings you here today?")
- [ ] Nearby (people & activities around you)
- [ ] AI Assistant (social coordinator intents)
- [ ] Me / Profile (layers, interests — no follower counts)
- [ ] Introductions (AI human-to-human matching) — needs consent/safety model

---

## Known hard problems (tracked, not ignored)
- **Cold start:** relationship-first is empty on day one → university seeding, Nearby-first.
- **Invisible signals:** no vanity metrics, but people still need to sense a relationship is alive.
- **Opportunity Engine + safety:** matching humans needs mutual intent/availability/trust;
  Introductions are the killer feature *and* the biggest safety surface (needs consent/block/report).

---

## Status log
- Build repaired (dangling `journalAvatarBrushes`) → clean baseline. Starting Stage 1.
