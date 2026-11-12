# GeckoBrowser (Android, GeckoView)

A minimal starter Android app that uses **Mozilla's GeckoView** (the Gecko engine) with a custom UI and a hook for installing WebExtensions.

## What this gives you
- Custom browser UI (URL bar + GeckoView) instead of Firefox's UI
- Gecko engine rendering via [`org.mozilla.geckoview:geckoview`](https://maven.mozilla.org/?prefix=maven2/org/mozilla/geckoview/)
- Example code path to install an extension via `WebExtensionController.install(...)`

## Prerequisites
- Android Studio / Android SDK (set `ANDROID_HOME` or `ANDROID_SDK_ROOT`)
- JDK 17
- A device/emulator meeting GeckoView's min SDK (this project targets minSdk 23)

## Build & run
1. Open the `gecko-browser/` folder in Android Studio and let it sync Gradle, **or** generate a wrapper and build from CLI:
   ```bash
   gradle wrapper
   ./gradlew assembleDebug
   ```
2. Install/run on an Android device or emulator.

## Extension support (Firefox Add-ons / AMO)
GeckoView supports WebExtensions, but there are important constraints:
- **Compatibility:** Many desktop Firefox extensions rely on APIs not available on GeckoView/Android. Extensions must be compatible with the GeckoView/Android environment.
- **Signing/source:** Gecko-based runtimes generally require extensions to be properly signed. In practice, use extensions that are built/signed for Android/GeckoView-compatible use, or your own extensions during development as appropriate for your GeckoView configuration.
- **Installing from AMO:** This starter calls `runtime.webExtensionController.install(<xpi-url>)`. Replace the placeholder URL in `MainActivity.installExampleExtension` with a direct `.xpi` URL for a compatible, signed extension. Browsing the AMO store UI and one-click install like desktop Firefox is not provided by GeckoView out of the box—you would need to build store browsing/selection UI and resolve compatible download URLs yourself.

For production use, review Mozilla's GeckoView docs on WebExtensions, recommended GeckoView versions, and extension compatibility/signing requirements:
- https://mozilla.github.io/geckoview/
- https://firefox-source-docs.mozilla.org/mobile/android/geckoview/

## Next steps / customization ideas
- Tabs (multiple `GeckoSession`s) and session management
- Back/forward/reload, progress, error handling
- Custom extension manager UI (list/enable/disable/uninstall installed extensions)
- Download handling, permissions prompts, file chooser
- Update GeckoView version in `app/build.gradle` to a current release from Mozilla's Maven repo
