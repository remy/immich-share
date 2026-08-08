# Immich Share

An Android share target for [Immich](https://immich.app). Share photos from any app —
Gallery, Google Photos, Signal, a browser — straight into a self-hosted Immich server,
with all original metadata intact.

This repo holds the Kotlin source. Following the `priority-calls` precedent, only the
built APK and a landing page are published to `remy/tools`.

**Status: implemented, not yet verified against a live server.** The settings screen,
share sheet and upload worker are all in place and the build is green, but nothing here
has been tested against a real Immich instance or on a physical device.

## Immich API key permissions

Immich API keys are scoped. Create one under **Account Settings → API Keys**; these are
the permissions this app needs, one row per endpoint it calls.

| What the app does | Endpoint | Permission |
| --- | --- | --- |
| Test connection | `GET /api/users/me` | `user.read` |
| Upload a photo | `POST /api/assets` | `asset.upload` |
| List albums for the picker | `GET /api/albums` | `album.read` |
| Create a typed-in album | `POST /api/albums` | `album.create` |
| Add photos to an album | `PUT /api/albums/{id}/assets` | `albumAsset.create` |
| List tags for the picker | `GET /api/tags` | `tag.read` |
| Create or upsert tags by name | `PUT /api/tags` | `tag.create` |
| Attach tags to photos | `PUT /api/tags/assets` | `tag.asset` |

Note that `albumAsset.create` is a separate scope from `album.create` — adding to an
existing album and creating a new one are granted independently, and it is easy to tick
one and not the other.

**Minimum useful key:** `asset.upload` and `user.read`. Photos upload fine; the album and
tag pickers come back empty and any selection fails after upload. Since album and tag
failures are reported as "uploaded, but…", the photos still land safely — you just get an
error notification each time. `user.read` is only used by **Save and test connection**,
but without it that button reports the key as rejected even though uploads would work,
which is confusing enough to be worth including.

For albums but no tags, add `album.read`, `album.create` and `albumAsset.create`.

These were read off the `Permission` enum in `server/src/enum.ts` and the
`@Authenticated({ permission: … })` decorator on each route in `immich-app/immich` on
`main`, not from documentation. Granular API key permissions are a relatively recent
Immich feature; on an older server there is no permission picker and keys carry full
account access, making this moot.

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
├── MainViewModel.kt        # settings state, connection test
├── data/                   # DataStore settings, Retrofit API, DTOs, repository
├── share/                  # ShareActivity, byte staging, EXIF, batch manifest
├── ui/                     # Material 3 theme, settings screen, confirm sheet
└── upload/                 # UploadWorker, notifications
```

## The parts that are easy to get wrong

The spec documents four traps that silently destroy metadata or break uploads. All four
are handled; these are the two worth knowing before changing anything:

- **URI grants die with the Activity.** An `ACTION_SEND` `content://` grant is scoped to
  the receiving activity and cannot be made persistable, so bytes must be staged to
  app-private storage in `ShareActivity.onCreate`, before the confirm sheet renders.
  Passing the URI to `WorkManager` and reading it later throws `SecurityException` —
  intermittently, which is what makes it dangerous.
- **Android redacts GPS by default.** `MediaProvider` strips GPS tags unless the app holds
  `ACCESS_MEDIA_LOCATION` *and* asks for the original via `MediaStore.setRequireOriginal`,
  which only exists from API 29 and only accepts MediaStore URIs. A redaction failure must
  degrade visibly, never block the upload.

The other two: EXIF dates carry no timezone, so `ExifInterface` resolves them rather than
any hand-rolled parse (`share/Staging.kt`), and the upload body streams from the staged
file rather than buffering into a `ByteArray` (`upload/UploadWorker.kt`).
