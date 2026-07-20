# Brown Noise tool — working context

Session context for building the Brown Noise ambient-audio tool in this `tool/`
module. Read this first when picking the work back up.

## ⏭️ NEXT STEP (start here)

**Add GitHub Packages credentials, then build.** The full build is blocked only
on a missing token — not on the code.

1. In the checkout's `local.properties` (gitignored), fill in:
   ```
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=YOUR_GITHUB_TOKEN   # PAT with read:packages scope
   ```
   (or export `GITHUB_ACTOR` / `GITHUB_TOKEN`).
2. Build from the worktree: `./gradlew :tool:assembleDebug`
3. Then try it on the LightOS emulator (see `docs/system_app`).

Why it's needed: `:sdk:client` → `:sdk:ui` depends on
`com.thelightphone.lp3keyboard:ui`, hosted on GitHub Packages
(`maven.pkg.github.com/lightphone/light-keyboard`). Without a token, resolution
fails with `Username must not be null!`. This is the documented token
requirement from the top-level README.

## What this tool is

A single-purpose ambient / brown-noise player. Tap Play → soft brown noise;
tap Stop → silence. Ported from the standalone `ambient_lp` Android app
(`~/Developer/ambient_lp`) onto the Light SDK.

## What was ported vs. removed

| ambient_lp piece | Outcome here |
|---|---|
| `BrownNoiseGenerator.kt` (AudioTrack DSP) | **Ported ~verbatim** → `tool/.../BrownNoiseGenerator.kt`. `android.media.*` is sandbox-permitted. Added a write-loop guard + idempotent `stop()`. |
| `MainActivity.kt` | **Replaced** by `HomeScreen` (`LightScreen` + `HomeScreenViewModel`, Compose). |
| `NoiseService.kt` (foreground service + wake lock) | **Removed entirely** — forbidden by the sandbox (see below). |
| `AndroidManifest.xml` | **Deleted** — the build plugin generates it from `lighttool.toml`. |
| `build.gradle` (API 27) | **N/A** — uses the module's existing `build.gradle.kts` + `light.sdk` plugin. |

## ⚠️ Core limitation: foreground-only audio

**The SDK offers no way to play audio with the screen off / app backgrounded.**
So `HomeScreenViewModel` stops playback in `onAppPause()` and `onCleared()` to
avoid a silent `AudioTrack`/thread lingering off-screen. The UI says so:
"Stops when you leave the tool." This is a deliberate stopgap.

The real fix needs a **LightOS-side bridge method** exposing the background
audio that Music/Podcasts already use. A feature-request draft for the Light
team lives alongside this file at `tool/sdk-request-background-audio.md`.
Summary of the ask: add a
`LightServiceMethod` that delegates playback to LightOS (same pattern as
`SetRingtone` handing over a URI), gated behind a
`BACKGROUND_AUDIO` permission. Easiest shape: hand LightOS a short seamlessly-
loopable file and have the OS loop it (Option A). This request is **still
pending** — send it before relying on background playback.

## SDK sandbox constraints (learned — enforced by `:plugin` + `:lint-rules`)

The plugin scans tool source and fails the build on:
- **Blocked imports**: `android.app.*`, `android.content.{Context,Intent,
  ComponentName,BroadcastReceiver,ContentProvider,ServiceConnection}`,
  `androidx.activity.*`, `androidx.appcompat.*`, `java.lang.reflect.*`,
  `java.lang.invoke.*`, `kotlin.reflect.*`.
- **Blocked calls**: `startService`, `bindService`, `startActivity`,
  `registerReceiver`, `getSystemService`, `contentResolver`, `LocalContext`,
  reflection, casts to Activity/Service/etc.
- **Generated manifest**: only `LightSdkApplication` + `LightActivity` +
  `LightSdkReceiver`. A tool **cannot declare its own `<service>`/`<activity>`**.
- **Background work** = `LightWork` only (WorkManager: deferred, 15-min min
  periodic). Not usable for continuous real-time audio.
- **Allowed permissions** (`LightToolPolicy.ALLOWED_PERMISSIONS`): INTERNET,
  ACCESS_NETWORK_STATE, WAKE_LOCK, VIBRATE, POST_NOTIFICATIONS, CAMERA,
  RECORD_AUDIO, READ_MEDIA_AUDIO, ACCESS_FINE/COARSE_LOCATION, NFC.
  Note: `WAKE_LOCK` is allowlisted but currently **unusable** (no
  `getSystemService`) — possibly a sign a background/keep-alive path is planned.
- **Allowed dependencies** are allowlisted in `LightSdkPlugin.kt`
  (`ALLOWED_DEPENDENCIES`).

App model: subclass `LightScreen`/`LightViewModel`, UI in Compose with
`:sdk:ui` components (`LightText`, `LightIcon`, `LightTheme`, `gridUnitsAsDp`,
`lightClickable`), navigate with `navigateTo`, talk to LightOS via
`callRemoteServiceMethod`. `@InitialScreen` marks the boot screen (exactly one);
`@EntryPoint` marks an optional `LightEntryPoint` object.

## Verification status

- ✅ Static sandbox self-check: `BrownNoiseGenerator.kt` and `HomeScreen.kt`
  trip no blocked imports/patterns; `@InitialScreen`/`@EntryPoint` present.
- ⏳ Full compile: **not yet run to completion** — blocked on the token above.

## File map (this change)

- `tool/lighttool.toml` — id `com.thelightphone.brownnoise`, label "Brown
  Noise", `permissions = []`.
- `tool/src/main/kotlin/com/thelightphone/sample/BrownNoiseGenerator.kt` — DSP.
- `tool/src/main/kotlin/com/thelightphone/sample/HomeScreen.kt` — UI + VM.
- `tool/src/main/kotlin/com/thelightphone/sample/ToolEntryPoint.kt` — unchanged
  scaffold entry point.
- (`DetailScreen.kt` from the sample was deleted — it was orphaned.)
