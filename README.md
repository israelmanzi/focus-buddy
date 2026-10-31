# Focus Buddy

A cozy focus timer with a companion, **Pip**, for Android and Linux desktop. Free and
complete: no accounts, no ads, no purchases. Everything (rooms, decor, colours, outfits) is
earned with acorns from focusing. Optional sync goes through a tiny server you host yourself.

See [docs/SPEC.md](docs/SPEC.md) for the design.

## Layout

| Module | What |
|---|---|
| `core/` | Event model, catalog, rewards, stats (Kotlin Multiplatform, unit-tested) |
| `composeApp/` | Compose Multiplatform app: shared UI in `commonMain`, Android service/blocker in `androidMain`, desktop entry in `desktopMain` |
| `server/` | Ktor + SQLite sync server |
| `deploy/` | Server install (systemd over Tailscale) and desktop install scripts |

## Build

Requires JDK 21 and the Android SDK (`local.properties` → `sdk.dir`).

```sh
./gradlew :core:jvmTest :server:test           # tests
./gradlew :composeApp:assembleRelease           # APK → composeApp/build/outputs/apk/release/
deploy/install-desktop.sh                       # desktop app → app menu + ~/.local/bin/focus-buddy
deploy/install-server.sh <ssh-host>             # sync server (see deploy/README.md)
```

Install the APK with `adb install -r composeApp-release.apk` or by opening it on the phone.

## Sync

1. Put the server, laptop and phone on one Tailscale network.
2. Run `deploy/install-server.sh <host>`; it prints a URL and token.
3. In each app: Settings → Sync → enter URL and token → Save & sync.

Data is an append-only event log, so devices merge without conflicts; each device also works
fully offline and catches up later. Backups (Settings → Backup) are plain JSON event logs.

## Sounds

All audio is synthesized at runtime (no asset files): a chime on start, a three-note finish, a
soft looping pad during sessions, toggled in Settings → Sound.

## Development

`BUDDY_PREVIEW=/some/dir ./gradlew :composeApp:run` renders Pip and every screen to PNGs
instead of opening a window. `./gradlew :composeApp:desktopTest` renders the decor catalogue to
`docs/art/decor-preview.png`.
