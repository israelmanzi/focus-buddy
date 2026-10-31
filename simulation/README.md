# Focus Buddy simulation suite

Two things live here:

1. **`run.sh`** — a one-week usage simulation against a throwaway data dir.
2. Notes on the full user-testing suite (the tests themselves live next to the code).

## Run the weekly simulation

```sh
simulation/run.sh
```

It compiles `composeApp`'s desktop test sources once (via the existing Gradle build, using
`classpath.init.gradle` only to print the runtime classpath), then starts a plain JVM running
`buddy.app.sim.WeeklySimulationKt`. No new Gradle module, task or product change is involved.

The simulation creates a temporary data dir, drives the real `AppController` through seven
simulated days (countdown, count-up, abandoned sessions, deep focus, breaks, tag creation,
purchases and equipping), prints a report (sessions, minutes, acorns earned/spent, purchases,
streaks, per-day/per-tag totals, 28-day tracker) and finishes with a backup export/import round
trip. The temp dir is deleted on exit.

The simulation uses the same `Harness` as the tests: an injected wall clock and a
`StandardTestDispatcher`, so a week passes in milliseconds with no real sleeping or network.

## Full suite

```sh
./gradlew :core:jvmTest :server:test :composeApp:desktopTest
```

| Where | What |
|---|---|
| `core/src/commonTest/.../StatsTest.kt` | streaks across midnight/DST, tracker, range totals, per-tag |
| `core/src/commonTest/.../EventOrderingTest.kt` | fold determinism, tie-breaks, skew semantics |
| `composeApp/src/desktopTest/.../sim/` | fake platform + clock harness, session/economy/backup scenarios, real-server two-device sync, UI smoke renders |
| `server/src/test/.../SyncServerTest.kt` | HTTP auth, dedupe, cursor paging (including >5000 events) |

UI smoke renders every screen and edge state (0 events, huge acorn counts, 24-char names, long
tag lists, 6-hour and grace-period sessions, break screens) to `composeApp/build/ui-smoke/*.png`.

Tests whose name starts with `productBug_` assert the behaviour the app promises and are expected
to fail until the product is fixed; each failure message contains the reproduction and the
expected vs actual behaviour. They are never skipped or weakened.

Known product bugs reproduced by the suite:

| Test | Expected | Actual |
|---|---|---|
| `BackupAndOrderingTest.productBug_sameMillisecondBuyThenEquipCanLoseTheEquip` | buy then equip emitted in one millisecond stays equipped | the equip is dropped in ~half of runs (same-ts events sort by random UUID) |
| `BackupAndOrderingTest.productBug_sameTimestampEquipBeforeBuyIsIgnoredByTheFold` | causally ordered same-ms events apply in emission order | an equip sorted before its buy is ignored |
| `BackupAndOrderingTest.productBug_duplicateIdsInsideOneBackupShouldBeCountedOnce` | a backup containing duplicate ids imports/appends one event | counts 2 and appends 2 JSON lines |
| `SyncSimulationTest.productBug_clockSkewSilentlyDropsAnAffordablePurchase` | a purchase accepted against synced acorns survives the fold | it is dropped when this device's clock is behind the earning device |
| `SyncSimulationTest.productBug_conflictingDuplicateIdsDivergePermanently` | devices converge under the event-log contract | each device keeps its own payload for the same id |
| `SyncSimulationTest.productBug_unknownPayloadTypeBlocksSyncPermanently` | an unknown payload type is skipped and sync continues | the whole page fails to decode, so the cursor never advances and sync retries forever |

## Screen-off behaviour

Screen-off is handled entirely on the Android side: `MainActivity` filters the `screenOff` flag
before calling `AppController.onAppBackgrounded()`, so turning the screen off is not treated as
leaving a session. `Platform` has no screen-off callback to test on the JVM; only the
background/grace path is covered by the controller tests.
