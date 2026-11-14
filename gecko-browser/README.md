# Arc Gecko — native Android browser

An Arc-inspired Android browser built on **GeckoView** (Firefox's engine). The chrome is
native Kotlin + Jetpack Compose; only the page content is Gecko. The UI follows
[`../mockup/index.html`](../mockup/index.html).

## What's inside

| Piece | Notes |
| --- | --- |
| `MainActivity.kt` | Immersive edge-to-edge host, system back handling, `VIEW` / `WEB_SEARCH` / `SEND` intents, XPI and file pickers, Android runtime-permission launcher. |
| `ArcApplication.kt` | One process-wide `GeckoRuntime` shared by every session. |
| `BrowserViewModel.kt` | One `GeckoSession` per tab, delegates, tab history, prompts, permissions, downloads, extensions, suggestions, scroll-driven chrome state, sheets, zoom, desktop site, persistence. |
| `TabManager.kt` | Session ownership: create, restore under the previous id, reopen closed tabs. |
| `AppSettings.kt` | DataStore snapshot (dark mode, suggestions, per-host zoom/desktop, remembered permissions) read before the first session. |
| `BrowserFiles.kt` | Private JSON payloads: tab list, per-tab `SessionState` blobs, download list. |
| `Downloads.kt` | Download records, `DownloadCenter`, and the foreground `DownloadService` (pause/resume/retry, MediaStore publishing). |
| `Prompts.kt` | Prompt, permission, file-picker and extension state types. |
| `ui/Theme.kt` | Light/dark design tokens transcribed from the mockup. |
| `ui/Root.kt` | Persistent `GeckoView` host, home overlay, morphing sheet, nav pill, prompt hosts, loading glow, download chip with swipe-to-cancel, extension popup sheet, back/forward notice, toasts. |
| `ui/Sheets.kt` | DuckDuckGo search sheet with live suggestions, and the menu (address bar with tap-to-edit URL, actions, zoom, desktop site, site settings, extensions). |
| `ui/Screens.kt` | Home, Firefox-style settings, site settings (with dark mode and tri-state permissions), downloads manager, extensions, Mozilla account. |
| `ui/Tabs.kt` | Horizontal recents carousel with captured page thumbnails and a new-tab card. |
| `ui/Components.kt` | Stroke icons, switches, settings rows, favicon tiles, glow. |

## Chrome behaviour

- No status bar, no address bar. Content runs edge-to-edge behind the nav pill.
- Pill: tabs (site initial) · search (+) · menu (chevron), 70% width, 58dp tall.
- The page's own scroll feeds the pill: a downward scroll past 120px condenses it into a
  bare domain chip (34dp tall, 9dp from the bottom, no favicon); scrolling up 16px or
  reaching the top restores the three icons. Home, open sheets, immersive mode and
  back/forward page changes don't move it.
- The pill pulses while a page loads.
- Sheets grow out of the pill: search and menu share the same 47% height; the pill floats
  above them. Settings, site settings and the account page grow to full height.
- In the menu the pill stays put as a capsule in the sheet's base — chip fill, hairline
  stroke, a slow colour handover — and the rows leave 92dp clear beneath them. Search and
  the full pages have no capsule; the search pill shrinks into the field's ✕ instead.
- Sheet content rises 26dp into place with a short delay, so it isn't a bare fade.
- Menu shows account · settings · menu while it is open, without resizing the pill.
- Dark mode lives in **Site settings → Appearance**, in **Settings → Appearance**, and the
  same preference drives the chrome theme.

## Gestures

- Pinch **in** on a page to hide the chrome; pinch in again to bring the pill back. Home,
  open sheets and pages that aren't at 100% zoom are ignored, and pinch-out does nothing.
  A trackpad pinch-in (ctrl+wheel down) mirrors it. The same toggle lives in the menu.
- Pill controls classify touches: under 24dp is a tap, a vertical drag needs 26dp at a
  1.25 ratio, and a horizontal drag needs 26dp at 1.25. Back/forward bindings are stricter:
  36dp at a 2.2 ratio, so a long diagonal doesn't navigate.
- Swipe **up** from the plus or the menu chevron to open the menu.
- Swipe **down** from the plus, the menu chevron, the search close (×), a sheet handle, or
  anywhere on the tab carousel to dismiss whatever that pill gesture opened.
- Swipe **left/right** from the plus to walk the open tabs; the incoming page slides in from
  the direction of the swipe.
- Swipe **up** from the tab icon to open the tab carousel, **left** to go back, **right** to go
  forward a page. Back/forward slide the page in from the side of travel.
- Swipe **left/right** from the menu chevron to go back/forward. Back and forward flash the
  same pill-sized notice as the immersive-mode coach mark.
- In the carousel, swipe a card **up** to dismiss the tab (the row closes the gap; the last
  tab leaves a fresh home tab) or **down** to focus it, exactly like tapping it. Tapping the
  empty space around the cards closes the carousel. Closing it shrinks the whole row back
  into the pill.
- Swipe **up** from a live download chip to cancel the newest transfer: a trash confirm
  floats above the chip, arms red, and shows an X near the end of the drag. A gesture-canceled
  transfer is cleared from the list instead of lingering as a canceled row.
- Tapping the URL in the menu address bar hands the whole address to the search field,
  fully selected, ready to edit.
- The download question and short messages (“Canceled”, “Opening”) use a pill-sized popup in
  the pill's footprint; the pill steps aside, a tap outside cancels, and a message over a
  sheet sits 92dp up. Finished downloads don't pop anything — the live chip says it.
- Reduced motion (system animator scale 0) collapses these transitions to their end state.

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

GeckoView supports WebExtensions, but only **signed** ones. **Extensions** in the menu opens
the add-on manager: installed add-ons with enable/disable, and **Add from file**, which hands
off to the system document picker. Pick an `.xpi` downloaded from
[addons.mozilla.org](https://addons.mozilla.org/android/) and it is installed through
`WebExtensionController.install(...)`. Toolbar actions open their popup in a native sheet
over the current page.

Caveats:

- Unsigned or self-built XPIs are rejected by GeckoView.
- Not every AMO add-on supports GeckoView (mobile); compatibility is up to the add-on.

## Downloads

The download chip is its own 46dp button beside the pill (right 6dp, bottom 24dp), never
inside it. It shows a badge only while more than one transfer is active, opens the manager on
tap, and morphs its flight from the point it was tapped. With no transfer running, the
expanded menu still shows it as a static download button beside the pill.

Downloads stream through a foreground `DownloadService`, so they keep going while the app is
backgrounded:

- Bytes are written to app-private `filesDir/downloads/download-<id>.bin` first; completed
  files are published to the public Downloads collection through MediaStore on API 29+, and
  to external files plus a `FileProvider` URI below that.
- Pause/resume uses an HTTP `Range` request; a server that ignores the range restarts the
  transfer cleanly.
- The manager groups active, paused and earlier transfers (finished, failed, canceled), and
  offers pause, resume, retry, cancel, open and remove.
- The list is persisted on every meaningful change (start, pause, cancel, finish, failure);
  progress itself is in-memory. A transfer that was mid-flight when the process died comes
  back **paused**, never as a phantom live row.

## Prompts and permissions

Site prompts (alert/confirm/prompt, HTTP auth, `<select>`, file inputs) and permission
requests (camera, microphone, location, notifications) render as pill-sized popups in the
pill's footprint. Answers go back through the original `GeckoResult`, so a dismissed prompt
never leaves the page waiting:

- Camera/microphone/location/notifications ask Android for the runtime permission first,
  then show the site question.
- Each site question can be remembered. Remembered answers are stored per `host|kind` in
  DataStore and mirrored into `StorageController`, so they apply on the next visit without a
  prompt; Site settings shows the tri-state Ask / Allowed / Blocked control.
- Media autoplay permissions are granted silently — this chrome has no UI for them.
- Unknown permissions are denied, and prompts from a background tab are dismissed rather
  than queued.

## Persistence and intents

- Tabs and their `SessionState` blobs are written on every navigation change; on a cold
  start the previous tab list, active tab, titles and back/forward stacks are restored.
- Settings live in DataStore: dark mode, suggestions, per-host zoom, per-host desktop mode
  and remembered permissions all survive a restart.
- `VIEW` intents open the URL in a new tab, `WEB_SEARCH` runs the query through the current
  search engine, and `SEND` text is treated the same way. A cold start handles the intent
  once; a configuration change doesn't duplicate the tab.
- The search sheet asks DuckDuckGo for suggestions (debounced, gated by the Settings toggle);
  typing a bare host or `www.` address skips suggestions and navigates.

## Known limits

- Home, settings and the switcher are native reimplementations; the mockup's fake site
  clones are replaced by real web content.
- Tab previews are real captures via `GeckoView.capturePixels()` taken when the carousel
  opens or the active tab changes; a page that is still painting can show a placeholder.
- There is no autofill or browsing-history UI yet.
- Zoom combines a visual scale of the web surface with Gecko's runtime font-size factor, so
  text grows with the page. GeckoView 138 exposes no page-zoom API, so the layout itself is
  not re-zoomed the way Firefox's pinch zoom would.
