# OmniAlbum — Compose Multiplatform rewrite plan

Decisions confirmed by the product owner:

- **Option C**: full rewrite into a single codebase — **Compose Multiplatform
  (Kotlin)**, not Flutter. Rationale: full feature parity (Glance home-screen
  widget, WorkManager background upload, App Links, App Search) is first-class
  in CMP; the provider layer ports from Swift to Kotlin almost 1:1; both
  Google Play and the Apple App Store remain fully supported distribution
  channels. Flutter cannot render native home-screen widgets.
- **Sync deferred entirely**: no CloudKit replacement. Albums live in a local
  SQLDelight database; CSV export/import (existing feature) covers moving data
  between devices.
- **Full feature parity** with the iOS app: 5 providers (Immich, Nextcloud,
  PhotoPrism, Lychee, Ente), album grid, detail grid, lightbox with zoom,
  EXIF sheet, add-album (single + bulk, auto-detect, password), Immich upload
  with progress, offline banner, deep links (`omnialbum://`), home-screen
  widget, search, CSV export/import.
- **Distribution**: sideload first (debug/universal APK on Android, dev-signed
  build via Xcode on iOS); bundle id `com.skript.omnialbum` and signing
  placeholders keep Play Store + App Store distribution possible later.
- **minSdk 28 (Android 9)**, targetSdk/compileSdk 36, iOS deployment target to
  match the existing app.

## Project layout

```
settings.gradle.kts / build.gradle.kts / gradle/     # Gradle + version catalog
shared/            # KMP library: models, providers, crypto, DB, networking
composeApp/        # CMP app: Compose UI (commonMain), Android + iOS targets
iosApp/            # iOS Xcode entry (Swift host + later WidgetKit/App Intents)
docs/              # this plan
OmniAlbum/ OmniAlbum.xcodeproj/ UploadWidgetExtension/   # legacy SwiftUI app, kept until cutover
```

Stack: Ktor 3 client (OkHttp on Android, Darwin on iOS), kotlinx-serialization,
Coil 3 (image cache, replaces Kingfisher), SQLDelight (local DB, replaces
CloudKit for storage), ionspin kotlin-multiplatform-libsodium (Ente
XSalsa20-Poly1305 secretbox + XChaCha20-Poly1305 secretstream — replaces
swift-sodium, independently audited binding), WorkManager (upload queue,
replaces BGTaskScheduler), Glance (widget, replaces WidgetKit on Android),
App Search (replaces Spotlight).

## API versioning

Every provider declares an `ApiVersionCatalog` (see
`shared/.../net/ApiVersion.kt`): a list of supported API versions with exactly
one marked current, plus the URL path prefix each version uses. Providers expose
`supportedApiVersions` / `currentApiVersion` / `selectedApiVersion` on the
`ShareLinkProvider` interface and implement `detectApiVersion()`, which probes
supported versions current-first and falls back to legacy ones on 404 (e.g.
Lychee supports legacy `v1` (`/api`) and current `v2` (`/api/v2`)). Adding a new
API version = adding one enum entry; old versions keep working until removed
from the catalog. CI (`.github/workflows/build.yml`) runs Android compile +
unit tests on ubuntu and iOS framework link + tests on macOS for every PR.

## Phases

1. **Scaffold** (done in this PR): Gradle/KMP modules, version catalog,
   `shared` + `composeApp` + `iosApp` shells, ported core models
   (`Asset`, `AlbumDetail`, `UploadTask`, `PhotoProvider`), `ShareLinkProvider`
   contract + `ProviderRegistry`, Android manifest with `omnialbum://` scheme
   and network security config mirroring `NSAllowsArbitraryLoads`.
2. **Providers**: port all 5 providers (link parsing/detection order
   Ente → Nextcloud → Lychee → Immich → PhotoPrism, API calls, URL builders,
   upload) + Ente crypto on libsodium bindings, verified against the same
   payloads the Swift app decrypts. Unit tests per provider.
3. **UI port**: Compose screens — album grid, album detail grid, lightbox
   (pager + pinch zoom + EXIF sheet), add-album sheet (single/bulk), settings,
   photo picker, upload progress views, offline banner.
4. **Persistence**: SQLDelight schema (albums, upload queue), repositories,
   CSV export/import in the existing format.
5. **Upload**: WorkManager queue with foreground progress notification on
   Android; BGTaskScheduler + Live Activity shim on iOS.
6. **Platform integrations**: deep-link router, App Search (Android) /
   App Intents (iOS), Glance recent-albums widget (Android) / WidgetKit
   extension (iOS), connectivity monitor.
7. **Assets & distribution**: adaptive icon, provider logos, splash; Play
   Console / App Store Connect readiness (signing, privacy manifest, photo
   permissions), sideload build docs.
8. **Tests, CI & docs**: provider/crypto/CSV unit tests (incl. API-version
   catalog tests), Compose UI tests, GitHub Actions workflow
   (`.github/workflows/build.yml`, added), README/CLAUDE.md update, cutover
   checklist for removing the legacy SwiftUI target.

## Verification note

The agent sandbox has no JDK/Android SDK/Xcode, so Gradle builds cannot run
here; the first `./gradlew :composeApp:assembleDebug` must run on a dev
machine. Dependency versions in `gradle/libs.versions.toml` are pinned to
known-good releases and can be bumped freely.
