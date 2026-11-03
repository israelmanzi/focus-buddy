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

1. Run `deploy/install-server.sh <host> --funnel` on the VPS. It prints an HTTPS URL
   (`https://<host>.<tailnet>.ts.net`) and token that work from anywhere; without `--funnel`
   the server stays reachable only inside your Tailscale network.
2. In each app: Settings → Sync → enter the URL and token → Save.

Data is an append-only event log, so devices merge without conflicts; each device also works
fully offline and catches up later. Backups (Settings → Backup) are plain JSON event logs.

## Desktop specifics

- Remembers window size and position; minimum 360×560.
- Space opens the focus sheet, Esc closes whatever is open.
- Optional keep-awake (Settings → Keep screen on) blocks idle sleep through logind while focusing.
- Closing the window quits. A StatusNotifierItem tray icon (XFCE, KDE, GNOME with an SNI extension) offers open/start/stop/quit with a transparent icon; it is simply absent where no StatusNotifier host runs.
- Session-finished and break-over notifications use the freedesktop notification service.

## Releases (CI)

`.github/workflows/release.yml` builds, on every `v*` tag (or manually from Actions):

- `focus-buddy.apk` (release, R8)
- `focus-buddy-*.rpm` and `.deb` for Linux desktop (jpackage)
- the desktop uber jar and `focus-buddy-server.jar`
- `SHA256SUMS`, published as a GitHub release

Apps check the latest release on launch. The Android app shows an update chip, downloads the new
APK and hands it to the system installer; the desktop app links to the release page. Note that an
APK can only replace one signed with the same key, so a debug-signed side-loaded build cannot
update to a CI-signed release; install the CI APK once to enter that track.

Add `BUDDY_KEYSTORE_BASE64`, `BUDDY_KEYSTORE_PASSWORD`, `BUDDY_KEY_ALIAS` and
`BUDDY_KEY_PASSWORD` as repository secrets to sign the APK with your own key. Without them the
APK is debug-signed: installable, but it cannot be installed over a differently signed build.

## Who owns the data

Every install starts empty and private: the event log lives in the app's own storage, nothing is
bundled in the APK or the packages. Sync is opt-in and blank by default. The sync server is
single-user: anyone who enters its URL and token merges into the same event log, so treat the
token as the account. For another person, deploy another server (`deploy/install-server.sh`).

## Sounds

All audio is synthesized at runtime (no asset files): a chime on start, a three-note finish, a
soft looping pad during sessions, toggled in Settings → Sound.

## Development

`BUDDY_PREVIEW=/some/dir ./gradlew :composeApp:run` renders Pip and every screen to PNGs
instead of opening a window. `./gradlew :composeApp:desktopTest` renders the decor catalogue to
`docs/art/decor-preview.png`.
