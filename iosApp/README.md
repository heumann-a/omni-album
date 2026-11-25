# iosApp

iOS entry point for the Compose Multiplatform rewrite.

`iosApp/iosApp/iosApp.swift` hosts the shared Compose UI via
`MainViewControllerKt.MainViewController()` (defined in
`composeApp/src/iosMain`).

The Xcode project (`iosApp.xcodeproj`) is not checked in yet: on a Mac with
Xcode + Android Studio, open the repo root in Android Studio and use the
Kotlin Multiplatform plugin's iOS run configuration (or copy the `iosApp`
project from the official KMP template), then build the `shared` /
`composeApp` frameworks with:

```bash
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

The app uses bundle id `com.skript.omnialbum` (matching the existing iOS app)
so App Store distribution stays possible. The `omnialbum://` deep-link scheme
must be registered in the Xcode project's Info.plist (`CFBundleURLTypes`).

Widget / Live Activity / Spotlight shims for iOS will be added as a small
Swift WidgetKit/App Intents extension next to this target (see
`docs/CMP_REWRITE_PLAN.md`).
