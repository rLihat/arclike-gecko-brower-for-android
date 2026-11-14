# Arc Gecko — native Android browser

An Arc-inspired Android browser built on **GeckoView** (Firefox's engine). The chrome is
native Kotlin + Jetpack Compose; only the page content is Gecko. The UI follows
[`../mockup/index.html`](../mockup/index.html).

## What's inside

| Piece | Notes |
| --- | --- |
| `MainActivity.kt` | Immersive edge-to-edge host, system back handling, XPI document picker. |
| `BrowserViewModel.kt` | One `GeckoSession` per tab, tab history, scroll-driven chrome state, sheets, dark mode, zoom, desktop site, add-on install. |
| `ui/Theme.kt` | Light/dark design tokens transcribed from the mockup. |
| `ui/Root.kt` | Persistent `GeckoView` host, home overlay, morphing sheet, nav pill, loading glow, download chip with swipe-to-cancel, back/forward notice, toasts. |
| `ui/Sheets.kt` | DuckDuckGo search sheet and the menu (address bar with tap-to-edit URL, actions, zoom, desktop site, site settings). |
| `ui/Screens.kt` | Home, Firefox-style settings, site settings (with dark mode), Mozilla account. |
| `ui/Tabs.kt` | Horizontal recents carousel with per-tab miniatures and a new-tab card. |
| `ui/Components.kt` | Stroke icons, switches, settings rows, favicon tiles, glow. |

## Chrome behaviour

- No status bar, no address bar. Content runs edge-to-edge behind the nav pill.
- Pill: tabs (site initial) · search (+) · menu (chevron), 70% width, 58dp tall.
- Scrolling **down** condenses the pill into a bare domain chip (34dp tall, 9dp from the
  bottom, no favicon). Scrolling **up** restores the three icons immediately.
- The pill pulses while a page loads.
- Sheets grow out of the pill: search and menu share the same 47% height; the pill floats
  above them. Settings, site settings and the account page grow to full height.
- Menu shows account · settings · menu while it is open, without resizing the pill.
- Dark mode lives in **Site settings → Appearance**, in **Settings → Appearance**, and the
  same preference drives the chrome theme.

## Gestures

- Swipe **up** from the plus or the menu chevron to open the menu.
- Swipe **down** from the plus, the menu chevron, the search close (×), a sheet handle, or
  anywhere on the tab carousel to dismiss whatever that pill gesture opened.
- Swipe **left/right** from the plus to walk the open tabs; the incoming page slides in from
  the direction of the swipe.
- Swipe **up** from the tab icon to open the tab carousel, **left** to go back, **right** to go
  forward a page.
- Swipe **left/right** from the menu chevron to go back/forward. Back and forward flash the
  same pill-sized notice as the immersive-mode coach mark.
- Swipe **up** from a live download chip to cancel the newest transfer: a trash confirm
  floats above the chip, arms red, and shows an X near the end of the drag. A gesture-canceled
  transfer is cleared from the list instead of lingering as a canceled row.
- Tapping the URL in the menu address bar hands the whole address to the search field,
  fully selected, ready to edit.

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35.0.0).

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`local.properties` (generated locally) points Gradle at your SDK; it is git-ignored.

**GeckoView version:** the project pins GeckoView **138.0.20250517143237**, the newest
release whose AndroidX dependencies still build with AGP 8.7 / compileSdk 35. GeckoView
159–160 pull AndroidX `core` 1.19 / `lifecycle` 2.11, which require AGP 9.1 and
compileSdk 37; bumping all four together is the upgrade path if you want those builds.

## Add-ons (signed XPI)

GeckoView supports WebExtensions, but only **signed** ones. The **Extensions** action in
the menu opens the system document picker; pick an `.xpi` downloaded from
[addons.mozilla.org](https://addons.mozilla.org/android/) and it is installed through
`GeckoRuntime.webExtensionController.install(uri)`.

Caveats:

- Unsigned or self-built XPIs are rejected by GeckoView.
- Not every AMO add-on supports GeckoView (mobile); compatibility is up to the add-on.
- The extension's UI (popups, options pages) is not rendered by this custom chrome, so
  toolbar-button extensions in particular will look inert.

## Known limits

- Home, settings and the switcher are native reimplementations; the mockup's fake site
  clones are replaced by real web content.
- Tab previews are stylised miniatures (title/url/colour), not live screenshots.
- There is no autofill or browsing-history UI yet.
- Zoom scales the web surface visually; GeckoView 138 exposes no page-zoom or script API,
  so pages do not reflow when zoomed.
