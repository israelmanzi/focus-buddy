# Focus Buddy — spec

An original, free, offline-first focus companion for Android and Linux desktop, with optional
self-hosted sync. Inspired by the *genre* of cozy focus timers (Focus Friend, Forest); no code,
art or text is taken from any of them. `docs/reference/` holds local screenshots used only as UX
reference and is git-ignored.

## Principles
- Everything is earned by focusing. No purchases, no premium currency, no ads, no accounts.
- Works fully offline. Sync is optional and goes to a server you own.
- One codebase (Kotlin Compose Multiplatform) for Android and desktop.

## Companion
**Pip**, a round, fluffy dumpling-shaped creature with small ears, drawn procedurally.
- Idle: wanders the room, sits, naps, looks at a tiny phone.
- Focusing: reads a book (page flips); occasional sips of tea.
- Interrupted: drops the book, sad face, "you left me…" bubble.
- Complete: happy hop, hands over acorns.
- Break: lounges on a cushion.
- Customisable: name, body colour (skins), hat, neck item, glasses.

## Focus loop
- Duration 5–360 min (slider + −/+ buttons, 5-min steps), count-down or count-up (stopwatch).
- Tags: unlimited, user-defined, coloured; default tag "Focus".
- First 10 s: "Cancel (n)" with no penalty. After that: "Give up" ends the session as abandoned.
- Leaving the app is allowed: using Chrome, notes or another productive app keeps the session
  counting. Only Deep Focus (opt-in) restricts apps, and that is enforced by the foreground service.
- A running session writes a heartbeat every 30 s. If the process dies (crash, force stop, reboot),
  the next launch ends the session at its last heartbeat instead of letting wall-clock time run:
  a count-up session can no longer "complete" at the six-hour cap while the app was dead.
- Abandoning keeps the minutes already focused (1 acorn each, no completion bonus) once the
  session is past 10 % of the plan and at least five minutes; shorter abandons leave no trace.
- Desktop: leaving the window does not fail a session (you work in other apps there).
- The room sky follows local time and season: sunrise/sunset are modelled from the day of the year
  (northern-hemisphere approximation), so evenings turn dark when the sun actually sets.
- Deep Focus (Android): during a session, opening a blocked app shows a full-screen overlay
  that sends you back. Block list *or* allow list mode.
- Breaks: after completion, offer a 5/10/15-min break or skip.
- Keep-awake option while focusing.
- In-app updates: checks the project's GitHub releases on launch and shows an update chip; on
  Android it downloads and hands the APK to the system installer; on desktop it opens the release
  page (the RPM/DEB are installed by the system package manager).

## Rewards and economy (single currency: acorns)
- 1 acorn per focused minute, +20 % bonus for completing the planned duration.
- Count-up sessions earn per minute (≥ 5 min to count).
- Abandoned sessions earn nothing.
- Spending: decor items, room unlocks, skins, outfits. Prices tuned so a few days of normal use
  unlocks a room and a week or two unlocks most of the catalogue.

## Rooms and decor
- Rooms: Living room (free), Study, Bedroom, Garden porch (unlocked with acorns).
- Each room has fixed decor slots (wall art, window, rug, plant, lamp, seat, shelf, desk…).
- Each slot offers themes (Basic, Cozy, Garden, Retro, Night) × colour variants.
- Decorate mode: tap a slot → picker (theme tabs, variant grid, preview, buy/equip).
  Owned items equip for free.

## Stats
- Summary: 28-day tracker grid (none / focus / deep-focus), total minutes, total sessions,
  current and best streak.
- Day / week / month / year bar charts, per-tag breakdown.

## Settings
- Sound (ambient music, SFX), screen on, grace period, default duration, Deep Focus setup
  (permissions, app picker, block/allow mode), sync (server URL, token, sync now),
  backup export/import (JSON file).

## Data and sync
- A `revoke` event withdraws an earlier session event on every device (used to remove test or
  mistaken sessions); revoked sessions are ignored by the fold, so acorns and stats follow.
- State is an append-only **event log**: each event has a UUID, device id, timestamp and a
  payload (session completed/abandoned, item bought, slot equipped, room unlocked, skin bought,
  companion edited, tag created/edited/deleted).
- App state = deterministic fold over events sorted by (timestamp, id). Purchases are validated
  in the fold (not enough acorns or already owned → ignored), so every device converges to the
  same state.
- Sync server: tiny Ktor + SQLite service. `POST /v1/sync {since, events}` stores new events
  (dedupe by id) and returns everything after `since`. Bearer token auth; bound to the Tailscale
  interface on the VPS.
- Device-local settings (sounds, blocklist, grace) are not synced.
