# Immich Share

An Android share target for [Immich](https://immich.app). Share photos from any app —
Gallery, Google Photos, Signal, a browser — straight into a self-hosted Immich server,
with all original metadata intact.

This repo holds the Kotlin source. Following the `priority-calls` precedent, only the
built APK and a landing page are published to `remy/tools`.

**Status: build toolchain and skeleton only.** The share flow, settings screen and upload
worker described in the spec are not implemented yet — the source tree currently contains
the scaffolding those pieces slot into, plus the two metadata helpers that must not
regress (`share/Staging.kt`).

## Requirements

- JDK 21
- Android SDK (installed by `./scripts/setup-android-sdk.sh`)
- No Android Studio needed — the Gradle wrapper is committed

## Build

```sh
./scripts/setup-android-sdk.sh   # installs SDK packages, writes local.properties
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
```

Other useful tasks:

```sh
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android lint; fails the build on errors
./gradlew assembleRelease     # R8 + resource shrinking, produces an unsigned APK
```

The release APK is unsigned. Signing happens locally or in CI from a keystore that is
never committed — see `.gitignore`.

## Toolchain

Versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and the
Gradle wrapper properties. The combination below is verified: it produces a debug APK,
passes lint, and survives R8 in release.

| Component | Version | Note |
| --- | --- | --- |
| Android Gradle Plugin | 9.3.1 | Kotlin support is built in — no `org.jetbrains.kotlin.android` plugin |
| Gradle | 9.6.1 | AGP 9.3.1 needs Gradle 9.x; 9.6.1 is pinned rather than tracking latest |
| Kotlin | 2.4.10 | Resolved onto the plugin classpath by the Compose/serialization plugins |
| JDK | 21 | Bytecode target is 17 |
| compileSdk | 37.1 | |
| targetSdk | 36 | |
| minSdk | 26 | Android 8.0, matching the `priority-calls` precedent |
| Compose BOM | 2026.06.01 | |

### Toolchain notes

Three things differ from the original spec, all found by building it:

- **The Compose compiler plugin is still required under AGP 9.** Built-in Kotlin support
  does not extend to Compose: enabling `buildFeatures.compose` without
  `org.jetbrains.kotlin.plugin.compose` fails at configuration time. The serialization
  plugin is applied conventionally too, and both coexist with AGP 9's built-in Kotlin.
- **compileSdk is 37, not 36.** The current stable AndroidX line (core-ktx 1.19,
  lifecycle 2.11, activity 1.13) refuses to compile against anything lower. `targetSdk`
  deliberately stays at 36: raising it opts the app in to new runtime behaviour, which
  needs testing on a device, and that is a separate decision from what to compile against.
- **`ExifInterface.getDateTimeOriginal()` is `@RestrictTo(LIBRARY)`** as of exifinterface
  1.4.2. It is public in the bytecode and used deliberately (with a lint suppression), as
  the spec intends — but it is not guaranteed API. Keep the version pinned and re-check on
  every upgrade. See `share/Staging.kt`.

## Layout

```
app/src/main/java/app/immichshare/
├── ImmichShareApp.kt       # Application: notification channel
├── MainActivity.kt         # launcher / settings / onboarding
├── data/                   # DataStore settings, Retrofit API, DTOs, client
├── share/                  # ShareActivity, byte staging, EXIF helpers
├── ui/                     # Material 3 theme, thumbnails
└── upload/                 # UploadWorker
```

## The parts that are easy to get wrong

The spec documents four traps that silently destroy metadata or break uploads. The two
already encoded here:

- **URI grants die with the Activity.** An `ACTION_SEND` `content://` grant is scoped to
  the receiving activity and cannot be made persistable, so bytes must be staged to
  app-private storage in `ShareActivity.onCreate`, before the confirm sheet renders.
  Passing the URI to `WorkManager` and reading it later throws `SecurityException` —
  intermittently, which is what makes it dangerous.
- **Android redacts GPS by default.** `MediaProvider` strips GPS tags unless the app holds
  `ACCESS_MEDIA_LOCATION` *and* asks for the original via `MediaStore.setRequireOriginal`,
  which only exists from API 29 and only accepts MediaStore URIs. A redaction failure must
  degrade visibly, never block the upload.

Two more apply to code not yet written: EXIF dates carry no timezone (use
`ExifInterface`, never hand-roll the parse), and the upload body must stream from the
staged file rather than buffer into a `ByteArray`.
