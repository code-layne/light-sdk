# Feature request: expose LightOS's background audio to tools via a bridge method

_Filed against `light-sdk`. Context: building an ambient-noise tool for the Light Phone 3._

## TL;DR

LightOS already does background audio for Music and Podcasts. That capability
just isn't reachable from an SDK tool — there's no `LightServiceMethod` that
exposes it. We'd like a bridge method that lets a tool ask LightOS to play/loop
audio on its behalf, following the **same delegation pattern as `SetRingtone`**,
so the OS owns playback and the tool sandbox stays fully intact.

## What we're building

A single-purpose ambient / brown-noise player. Tap **Play**, a soft noise runs
continuously (for focus or sleep); tap **Stop**, silence. No feeds, no
notifications to chase, no reason to look at the screen — the screen should be
**off** while it plays. We think this is squarely in the Light ethos: it's a
tool that helps you *put the phone down*, not pick it up.

## The gap: the capability exists, but there's no bridge to it

We want to be precise, because we don't think this is a sandbox-loosening
request — it's a missing bridge method.

- **The OS clearly supports background audio.** Music and Podcasts play with the
  screen off. That machinery (foreground service, wake lock, media session)
  lives inside LightOS.
- **SDK tools can't reach it.** A tool talks to LightOS only through
  `callRemoteServiceMethod()` over the bound-service bridge
  (`LightSdkService`). The full set of things a tool can request is the
  `LightServiceMethod` list — `GetToken`, `GetVersion`, `SetRingtone`,
  `GetKeyboardOptions`, `GetUserPreferences`, `GetPermission`,
  `RequestPermissionComponent`. **None of them play or manage audio.**
- **Doing it tool-side is (correctly) blocked.** A tool can't stand up its own
  foreground service: `startService()` / `getSystemService()` are blocked
  patterns, `android.app.*` is a blocked import, and the generated manifest
  declares no tool-owned `<service>`. We're *not* asking you to change that —
  the tool shouldn't hold the wake lock. LightOS should.
- **`WAKE_LOCK` is allowlisted but currently unusable** (no way to acquire one
  without `getSystemService`), which makes us think an audio/keep-alive story
  may already be partly anticipated.

Net effect: with `AudioTrack` we can generate the noise, but it only plays while
our `LightScreen` is foregrounded. Screen off or user leaves → playback stops,
which defeats a sleep/focus app.

## What we're asking for

A new `LightServiceMethod` that delegates playback to LightOS, mirroring how
`SetRingtone` hands the OS a URI and lets the OS own the audio. `SetRingtone`
is the precedent we're modeling on:

```kotlin
object SetRingtone : LightServiceMethod<Request, Unit> {
    data class Request(val type: Int, val uri: String)   // tool hands over a URI; OS plays it
}
```

Two possible shapes, easiest first:

### Option A — loop a file (likely feasible today)

The tool ships a short, seamlessly-loopable audio file (e.g. ~30s of noise)
into its `fileShare` dir and asks LightOS to loop it in the background:

```kotlin
object PlayLooping : LightServiceMethod<Request, Unit> {
    data class Request(val uri: String, val label: String)  // label -> ongoing notification
}
object StopPlayback : LightServiceMethod<Unit, Unit>
```

This reuses the exact `SetRingtone` model (tool provides a URI, OS plays it) and
covers our use case completely. We suspect it's close to something you can
already do internally.

### Option B — stream generated PCM (richer, more work)

For tools that generate audio at runtime, a streamed source LightOS pulls from
and plays through its existing media pipeline. More flexible, but strictly
more than we need — Option A is enough for us.

We're not attached to these exact signatures; the essential capability is
**LightOS plays/loops tool-provided audio in the background until the tool (or
the user) stops it.** Gating it behind a permission
(e.g. `com.thelightphone.permission.BACKGROUND_AUDIO`) so it surfaces in review
and to users seems right to us.

## Why this fits the platform

- **No new tool capability, no sandbox hole.** The OS keeps holding the service
  and wake lock; the tool only sends a URI + start/stop, exactly like
  `SetRingtone`. The security model is unchanged.
- **Precedent already exists.** `SetRingtone` shows tools can drive the audio
  system through a delegating bridge method — this extends the same idea from
  "set the ringtone" to "play in the background."
- **On-ethos.** Calm, single-purpose, and a reason to leave the screen dark.
- **Observable.** Permission-gated, one active playback, an ongoing
  notification the user can stop from — no silent background work.

## Questions for the team

1. Is there an intended path for tool-driven background audio we've missed?
2. Could Option A (loop a tool-provided file via a `SetRingtone`-style method)
   be exposed relatively easily, given the OS already plays ringtones and media?
3. Is the allowlisted-but-unusable `WAKE_LOCK` a sign this is already planned?
   If so, what's the intended API and rough timeline?
4. If we prototyped a method + emulator handler against `:sdk:server`, is that a
   contribution you'd be open to reviewing?

Happy to jump on a call or share a prototype. Thanks for building this — the
sandbox model is genuinely nice to work within.

— [YOUR NAME], [contact / GitHub handle]
