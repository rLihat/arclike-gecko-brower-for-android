# horiZen — native Android browser

An Arc-inspired Android browser built on **GeckoView** (Firefox's engine). The chrome is
native Kotlin + Jetpack Compose; only the page content is Gecko. The UI follows
[`../mockup/index.html`](../mockup/index.html).

## What's inside

| Piece | Notes |
| --- | --- |
| `MainActivity.kt` | Immersive edge-to-edge host, system back handling, `VIEW` / `WEB_SEARCH` / `SEND` intents, XPI and file pickers, Android runtime-permission launcher. |
| `ArcApplication.kt` | One process-wide `GeckoRuntime` shared by every session. |
| `BrowserViewModel.kt` | One `GeckoSession` per tab, delegates, tab history, prompts, permissions, downloads, extensions, suggestions, find in page, translation, passwords, scroll-driven chrome state, sheets, zoom, desktop site, persistence. |
| `TabManager.kt` | Session ownership: create, restore under the previous id, reopen closed tabs. |
| `AppSettings.kt` | DataStore snapshot (dark mode, suggestions, search engine, per-host zoom/desktop, remembered permissions, tracking level, cookie-banner toggle, tracking exemptions) read before the first session. |
| `BrowserFiles.kt` | Private JSON payloads: tab list, per-tab `SessionState` blobs, download list. |
| `HistoryStore.kt` | Local history: one SQLite `visit` row per URL (`id`, `url`, `title`, `lastVisit`, `visitCount`), an in-memory mirror for instant reads, queued writes, 90-day/5000-row pruning. |
| `Downloads.kt` | Download records, `DownloadCenter`, and the foreground `DownloadService` (pause/resume/retry, MediaStore publishing). |
| `Prompts.kt` | Prompt, permission, file-picker, popup-notice, selection, extension and translate-language state types. |
| `PasswordStore.kt` | Encrypted on-device login vault (Android Keystore AES-256/GCM) with CRUD, never-save and autofill toggles. |
| `HorizenAutofillService.kt` | Android `AutofillService` bridging the vault to GeckoView's login forms. |
| `ErrorPages.kt` | Themed local HTML for network and certificate failures, with retry/back. |
| `ui/Theme.kt` | Light/dark design tokens transcribed from the mockup. |
| `ui/Root.kt` | Persistent `GeckoView` host, home overlay, morphing sheet, nav pill, prompt hosts, blocked-popup pill, find bar, selection bar, translate and passwords pages, loading glow, download chip with swipe-to-cancel, extension popup sheet, back/forward notice, toasts. |
| `ui/Sheets.kt` | DuckDuckGo search sheet with live suggestions, and the menu (address bar with tap-to-edit URL, actions including find and translate, zoom, desktop site, site settings, extensions). |
| `ui/Screens.kt` | Home, site settings (dark mode, tri-state permissions, per-site tracking protection and blocked count), downloads manager, extensions, Mozilla account. |
| `ui/SettingsPages.kt` | Settings hub: account card, five category tiles, a bottom search pill with live filtering, and the Look and feel / Search and preferences / Privacy and security / About pages written against the same shared rows. |
| `ui/Passwords.kt` | Password vault: saved logins, search, add/edit, delete, generator, autofill status. |
| `ui/Tabs.kt` | Horizontal recents carousel with captured page thumbnails and a new-tab card. |
| `ui/Components.kt` | Stroke icons, switches, settings rows, favicon tiles, glow. |
| `ui/Splash.kt` | horiZen mark drawn on a Compose canvas, the animated splash, and `BrowserRoot` (splash clock + hand-over into the browser). |
| `ui/History.kt` | History page: day groups, search, swipe-to-delete rows, and clear by range. |
| `ui/Translate.kt` | Translation page: detected/picked source, target picker, Translate/Restore action, support and error states. |
| `res/values/themes.xml` | `Theme.horiZen`, and `Theme.horiZen.Starting` for the Android 12+ system splash. |
| `res/drawable/ic_*launcher*`, `res/mipmap-anydpi*/ic_launcher.xml` | Vector launch mark and adaptive icon (foreground + monochrome). |

## Branding

The in-development name is **horiZen** (`@string/app_name`); the package, `applicationId`
and Kotlin namespaces stay `com.example.geckobrowser`. The mark is the supplied logo SVG
rebuilt as vector paths and Compose drawing — no bitmaps, no blur filters:

- `drawable/ic_splash_logo.xml` — the mark alone (rings, mirrored reflection, horizon
  glint) on the Android 12+ system splash, scaled to 0.6 so it fits the 192dp masked circle.
- `drawable/ic_launcher_foreground.xml` + `ic_launcher_monochrome.xml` +
  `mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon on the `#160C42` night colour, with a
  themed (monochrome) layer for Android 13+.
- `ui/Splash.kt` — the same bands as gradients on a canvas so they can expand out of the
  horizon. The rings grow over 1.2s on `cubic-bezier(.32,.72,0,1)` while the wordmark fades
  in with the capital **Z** on the logo gradient; the screen cuts short as soon as restored
  tabs are ready (420ms floor) and hands over by shrinking the mark as the browser rises.

Wiring: the activity theme is `Theme.horiZen.Starting` (`postSplashScreenTheme` →
`Theme.horiZen`), `MainActivity` calls `installSplashScreen()` before `super.onCreate` and
keeps the system splash until `BrowserViewModel.ready` (900ms cap), then `BrowserRoot`
runs the Compose animation. Below Android 12 `core-splashscreen` shows the static mark.
With the system animator scale at 0 the mark is drawn at rest and the hand-over is instant.

## Chrome behaviour

- No status bar, no address bar. Content runs edge-to-edge behind the nav pill.
- Pill: tabs (site initial) · search (+) · menu (chevron), 70% width, 58dp tall.
- The page's own scroll feeds the pill: a downward scroll past 120px condenses it into a
  bare domain chip (34dp tall, 9dp from the bottom, no favicon); scrolling up 16px or
  reaching the top restores the three icons. Home, open sheets, immersive mode and
  back/forward page changes don't move it.
- The pill pulses while a page loads.
- A fresh navigation dips the page to 92% and eases it back; reloads, offline retries and
  restored sessions (same URL modulo the fragment) don't dip it.
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

## Find in page

- The menu's **Find** opens a bar in the pill's footprint, lifted above the keyboard, with
  the field already focused and the IME raised.
- Typing searches as you go through `GeckoSession.finder`: every hit is highlighted and the
  rest of the page dims (`FINDER_DISPLAY_HIGHLIGHT_ALL | FINDER_DISPLAY_DIM_PAGE`).
- The counter reads `3/17`, or **No results**. ↑ and ↓ step backwards and forwards
  (`FINDER_FIND_BACKWARDS` / `FINDER_FIND_FORWARD`) and are disabled for a single hit; the
  IME's search key goes to the next hit.
- ✕, system back, a new page load, a tab switch or fullscreen close the bar, clear the
  query and reset the session's display flags so the page returns to normal.
- Each request carries a generation number, so a slow answer for an older query can never
  overwrite the counter for what is currently typed.

## Translate

**Translate** in the menu opens a full-height page that drives GeckoView's own translation
engine. Nothing is sent to a cloud service; the language models run on the device.

- Support is checked once per process: `RuntimeTranslation.isTranslationsEngineSupported()`
  gates the screen, then `listSupportedLanguages()` fills the source and target pickers.
  If the check fails the page says so and offers **Check again**.
- The source language comes from `TranslationState.detectedLanguages.docLangTag`; the picker
  lists `fromLanguages` plus **Detect automatically**. A detected language Gecko can't read
  is called out, and choosing another is one tap away.
- The target defaults to `preferredLanguages()`, then the device locale, then English, and is
  stored per tab so switching tabs keeps the choice.
- **Translate page** calls `session.sessionTranslation.translate(from, to, options)` with
  `downloadModel(true)`, so the first use of a pair downloads its model. While that or the
  translation runs the action reads **Working…**; the first translation can take a minute.
- `TranslationsSessionDelegate.onTranslationStateChange` keeps the screen honest: it mirrors
  the requested pair, `hasVisibleChange` (translated vs original) and engine readiness, and
  maps state errors to plain sentences.
- Once translated, the same button becomes **Show original page** and calls
  `restoreOriginalPage()`. Failures surface through `TranslationsException.code` (engine,
  language pair, model download, restore) rather than a generic toast.
- A new document load clears that tab's translated flag, detected language and error but
  keeps its language pair; closing the tab drops the per-tab state. Back in the language
  list returns to the controls, and back on the screen returns to the menu.
- The runtime's automatic translate offer is not surfaced: translation starts from the menu,
  not from a popup on every foreign page. Firefox's per-site "never translate" list is
  likewise not exposed yet.

## Passwords

**Autofill & passwords** in Settings opens a vault page. Logins are stored on the device,
encrypted with an AES-256 key that lives in the Android Keystore; nothing is synced and
nothing leaves the phone.

- The vault lists saved logins grouped by host, with search, reveal, copy and delete.
  Adding or editing a login is a dialog with a built-in password generator (length and
  symbols).
- **Autofill logins** and **Offer to save logins** are toggles. When horiZen is selected as
  the device's autofill service (Android 8+), saved logins appear in the system autofill
  bar over a page's login form, and a submitted login can be offered for saving. The
  settings page shows whether horiZen is the active autofill service and links to the
  system autofill settings.
- Hosts on the never-save list are skipped. Matching is exact or subdomain.
- Storage is a single encrypted file (`logins.enc`) in the app's private files dir, written
  atomically; a corrupt or unreadable file degrades to an empty vault rather than
  crashing.

## Extensions

The extensions sheet lists installed add-ons with enable/disable, **Update**, **Permissions**
(required + optional permissions and site access, with granted state), **Listing** (opens the
AMO page when the add-on carries one) and **Remove** (with confirmation). **Check for
updates** runs `WebExtensionController.update()` on every installed add-on. Only signed
XPIs install; unsigned files are rejected by GeckoView.

## Settings

Settings opens on a hub: the Mozilla account card, five category tiles
(**Look and feel**, **Search and preferences**, **Extensions**,
**Privacy and security**, **About**) and a floating search pill docked at the bottom.
Typing in the pill filters every setting live; tapping a result jumps to the matching
category page (extensions and site permissions open their own overlays). The system
back gesture returns from a page to the hub, then out of settings.

- **Look and feel** - appearance, accent colour, text size, language.
- **Search and preferences** - default search provider with inline reorder/remove/add,
  suggestions toggle, open links in apps, and **Default browser** (see below).
- **Extensions** - opens the extensions overlay.
- **Privacy and security** - tracking protection, cookie banners, HTTPS-Only, history
  (with clear-by-range), clear browsing data, site permissions.
- **About** - app/engine versions, update channel and check, what's new, author notes.

## Default browser

The **Default browser** row lives in **Settings -> Search and preferences -> Preferences**.
It shows whether horiZen is the device's default browser and offers to request it
(Android 10+ uses `RoleManager`; older releases open the system default-apps settings).
The manifest already advertises the standard
`VIEW`/`WEB_SEARCH`/`SEND` intent filters, so horiZen appears in the chooser without
extra work.

## Updates

The **About** page carries an update channel toggle (**Beta** / **Stable**), a manual
**Check for updates**, **Check for updates on launch**, and a **What's new** page rendering
the bundled release notes. When a newer release exists a banner appears over everything
(anchored above settings too); tapping **Download** streams the APK into `filesDir/updates/`
with progress, hands it to the system package installer through `FileProvider` on
**Download & install**, and stale APKs older than the running build are swept on every cold
start and on every fresh download. Installed builds ignore any banner that isn't newer than
themselves.

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
tap, and morphs its flight from the point it was tapped. On the page it appears only while a
transfer is moving, then hides itself three seconds after the last one finishes; the
expanded menu always shows it as a static download button.

Downloads stream through a foreground `DownloadService`, so they keep going while the app is
backgrounded:

- Bytes are written to app-private `filesDir/downloads/download-<id>.bin` first; completed
  files are published to the public Downloads collection through MediaStore on API 29+, and
  to external files plus a `FileProvider` URI below that. The target folder is
  configurable in the settings row **Download folder** (**Settings > Search and
  preferences > Preferences**): blank means the Download root, and any typed subfolder
  (e.g. `horiZen` or `horiZen/work`, a leading `Download/` is discarded) is created
  inside it.
- Pause/resume uses an HTTP `Range` request; a server that ignores the range restarts the
  transfer cleanly.
- The manager groups active, paused and earlier transfers (finished, failed, canceled), and
  offers pause, resume, retry, cancel, open and remove. Removing a transfer (or clearing the
  list) also deletes the underlying file: MediaStore rows are deleted through the resolver,
  FileProvider URIs are mapped back to the app directories declared in `file_paths.xml`.
- OTA update APKs downloaded by the in-app updater accumulate in `filesDir/updates/`; they
  are swept on every cold start and on every fresh update download, keeping only the file
  for the running build and the pending install.
- The manager groups active, paused and earlier transfers (finished, failed, canceled), and
  offers pause, resume, retry, cancel, open and remove.
- The list is persisted on every meaningful change (start, pause, cancel, finish, failure);
  progress itself is in-memory. A transfer that was mid-flight when the process died comes
  back **paused**, never as a phantom live row.

## History

History is local and lives in one SQLite table, `visit(id, url, title, lastVisit, visitCount)`,
unique on `url`. **History** in the menu (or in **Settings → General**) opens the page; it
groups rows into Today / Yesterday / date, filters as you type, opens a row in the active tab,
deletes on a left swipe, and clears the last hour, today or everything from the header.

- `HistoryDelegate.onVisited` records top-level `http`/`https` visits only and returns `true`
  so Gecko keeps no second copy; redirect hops, `about:` URLs and unrecoverable-error pages
  are skipped. A revisit bumps `visitCount` and `lastVisit`; `onTitleChange` fills in a title
  that arrived after the visit.
- `getVisited` answers from the same store, which is what colours visited links.
- Rows older than 90 days are pruned, then the table is capped at 5000 rows (newest kept).
- Reads come from an in-memory mirror, so the page and link colouring never wait on disk;
  writes queue on one background thread in order.
- Private tabs are a hook, not a feature yet: a tab added to `BrowserViewModel.privateTabs`
  records nothing and learns nothing from history.

The schema is Room-shaped but written against `SQLiteOpenHelper`, keeping the app free of a
codegen dependency; moving to Room later only touches `HistoryStore.kt`.

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
- **Popups.** A page that tries to open a popup puts a pill in the nav pill's footprint:
  "Popup blocked", the host, **Allow once** and ✕. Silence (six seconds) or ✕ denies it,
  as does a sheet opening over it. A popup from a background tab, or while the page is
  fullscreen, is denied without asking.

## Fullscreen, errors and selection

- A page's fullscreen request hides the chrome and the pill, lets the phone rotate and keeps
  the screen on. Back, switching tabs, leaving the app, or the page leaving fullscreen all
  call `GeckoSession.exitFullScreen()`, restore portrait and clear the keep-awake flag.
  A configuration change (rotation) is not treated as leaving the app.
- **Error pages.** `onLoadError` returns a themed local page for network and certificate
  failures on `http(s)` URLs — offline, unknown host, refused, timeout, bad certificate and
  a generic failure. The page keeps the failed address, offers **Try again** and **Go back**
  (via `history.back()`), and has no bypass for certificate errors. Content, URI, proxy and
  safe-browsing failures fall through to Gecko's own page; the generic "Couldn't load the
  page" toast is suppressed when a themed page was shown.
- **Offline retry.** An `ERROR_OFFLINE` failure schedules a real reload of the failed URL
  after five seconds, up to four attempts; typing a new address, reload, back or forward
  resets the budget, and a successful load cancels the pending attempt.
- **Long press.** A link or image raises the context sheet next to the finger with a
  `HapticFeedbackType.LongPress` buzz.
- **Text selection.** A `SelectionActionDelegate` puts a small bar under the selection with
  **Copy**, **Select all** (when Gecko offers them) and ✕. Actions run on the live
  `Selection`, so the system clipboard and the page's own state stay in sync.

## Privacy

- **Tracking protection** is Standard by default and can be switched to Strict in
  **Settings → Privacy & security**. The choice is stored in DataStore as `etp_strict` and
  applied to `ContentBlocking.Settings.setEnhancedTrackingProtectionLevel` at bootstrap.
- Cookies are set to `ACCEPT_NON_TRACKERS`, so cross-site trackers don't get a cookie jar.
- **Cookie banners** can be auto-answered (`COOKIE_BANNER_MODE_REJECT_OR_ACCEPT`); the
  toggle is off by default, which leaves `COOKIE_BANNER_MODE_DISABLED`.
- **Blocked count.** Each session has a `ContentBlocking.Delegate`; every `onContentBlocked`
  event with `isBlocking` increments that tab's count, which resets on a new page and is
  shown as **Trackers blocked** in **Site settings**.
- **Per-site exceptions.** GeckoView 138's `ContentBlockingController` exposes only
  `getLog`, with no `addException`/`removeException`, so an exception is applied per session
  through `GeckoSessionSettings.setUseTrackingProtection` for the host being loaded. The
  exemption is stored as `tracking|<host>` in DataStore, re-applied on every location
  change, and turning it off reloads the page.

## Persistence and intents

- Tabs and their `SessionState` blobs are written on every navigation change; on a cold
  start the previous tab list, active tab, titles and back/forward stacks are restored.
- Settings live in DataStore: dark mode, search suggestions, the search engine, per-host
  zoom, per-host desktop mode, remembered permissions, the tracking-protection level, the
  cookie-banner toggle and per-site tracking exemptions all survive a restart.
- `VIEW` intents open the URL in a new tab, `WEB_SEARCH` runs the query through the current
  search engine, and `SEND` text is treated the same way. A cold start handles the intent
  once; a configuration change doesn't duplicate the tab.
- The search sheet lists history matches and the chosen engine's suggestions together
  (debounced; network suggestions are gated by the Settings toggle).

## Search

- **Engines.** DuckDuckGo, Google, Bing, Brave, Ecosia and Startpage, plus any custom
  `%s` template. The choice is stored in DataStore (`search_engine`) and mirrored to the
  old prefs key, and the Settings row expands into an inline picker; the search field's
  engine badge opens the same list.
- **Suggestions.** Each engine carries an optional `suggestTemplate`. The parser accepts
  OpenSearch `["query", ["a", "b"], …]` (Google, Bing), DuckDuckGo's `[{"phrase": …}]`,
  and plain or `suggestions`/`phrases`-wrapped arrays; an engine that answers with
  something else (or 204/429) offers no suggestions and the history matches still list.
  Ecosia and Startpage have no usable endpoint, so their template is null.
- **History autocomplete.** `HistoryStore.suggest` ranks rows by prefix (host, then URL,
  then title) and then by frecency — visit count damped by `ln(1 + n)` and weighted by age
  (100 / 70 / 40 / 20 / 10 / 4 for <1, <3, <7, <14, <30 days and older). Matches appear
  above network suggestions, with the page title and host.
- **Inline completion.** When the caret is at the end and the text does not look like a
  URL, a history host/URL that starts with what was typed is appended and shown selected;
  the first backspace drops the suffix instead of deleting a typed character, and typing
  over the selection replaces it.
- **URL detection.** A single token is a URL when it has a known scheme, `://`, is
  `localhost`, is an IPv4 address or a bracketed IPv6 literal, or ends in a dotted host
  whose last label is 2–63 letters (`example.com`, `example.co.uk`). Everything else
  — `2.5`, `e.g.`, or anything with a space — goes to the engine as a query.

## Known limits

- Home, settings and the switcher are native reimplementations; the mockup's fake site
  clones are replaced by real web content.
- Tab previews are real captures via `GeckoView.capturePixels()` taken when the carousel
  opens or the active tab changes; a page that is still painting can show a placeholder.
- Passwords live in an on-device encrypted vault; in-page filling goes through the Android
  autofill framework, so horiZen must be selected as the device's autofill service. GeckoView
  138 exposes no direct login-capture callback beyond the autofill session, so saving happens
  either through the system save prompt or by adding a login manually.
- Zoom combines a visual scale of the web surface with Gecko's runtime font-size factor, so
  text grows with the page. GeckoView 138 exposes no page-zoom API, so the layout itself is
  not re-zoomed the way Firefox's pinch zoom would.
- GeckoView 138's `ContentBlockingController` has no per-site exception API, so a tracking
  exemption is enforced with `GeckoSessionSettings.setUseTrackingProtection` per session
  rather than stored in Gecko's own exception list; the stored host set is the source of
  truth and is re-applied on every location change.
- Certificate failures deliberately have no "continue anyway"; the themed error page offers
  only **Go back**, and Retry is omitted where it could only fail again.
- The selection bar is the app's own: `SelectionActionDelegate` is wired for Copy /
  Select all, but the full set of editing actions (cut, paste, collapse) has no UI yet.
- Find in page uses Gecko's case-insensitive default: `FINDER_FIND_MATCH_CASE` and
  `FINDER_FIND_WHOLE_WORD` have no toggles in the bar yet.
- Translation depends on GeckoView reporting a translation engine and on-device models; when
  `isTranslationsEngineSupported()` returns false the page explains that instead of falling
  back to a remote service. The automatic translate offer and the per-site never-translate
  list are not wired up.
