# horiZen

An Android browser built on GeckoView (Firefox's engine) with a custom, edge-to-edge
UI: no status bar, no address bar, and a single morphing navigation pill.

horiZen is the in-development name (the logo is a ring stack rising over a horizon);
the package and `applicationId` stay `com.example.geckobrowser`, and the chrome still
follows the Arc-inspired mockup.

Extensions install as AMO-compatible signed XPIs — the app uses Mozilla's add-on
system rather than shipping its own store.

## Contents

| Path | What it is |
| --- | --- |
| `mockup/index.html` | Single-file, interactive UI mockup — the design reference. Open it in any browser. |
| `gecko-browser/` | The Android app: native Jetpack Compose chrome over a real GeckoView engine. |

## Mockup

`mockup/index.html` is self-contained (no build step, no dependencies). It covers the
nav pill (search, tabs, menu), the sheets that grow out of it, settings, site settings,
the Mozilla account page, dark mode, and long-form site clones (YouTube, GitHub, Gmail,
DuckDuckGo results, Add-ons) with a loading glow, a condensing domain pill and a slim
scroll indicator.

The mockup always animates, even on a machine that asks for reduced motion. Its Debug
panel has a **Reduced motion** switch that demonstrates the collapsed-transition
behaviour, next to a trigger for every gesture.

## Android app

Native Kotlin reimplementation of the mockup chrome with real GeckoView content:

- No status bar / no address bar; web content runs behind the floating pill.
- Pill condenses to a domain chip on real page scroll down and restores its icons on scroll up.
- Search + menu sheets morph out of the pill; settings, site settings and account are
  full-height pages. Settings opens on a hub (account card, five category tiles) with a
  floating search pill that filters every setting live. Dark mode is a persisted chrome
  theme.
- Tabs are a horizontal recents carousel with captured page thumbnails; each `GeckoSession`
  keeps its own history and both tabs and settings survive a cold start.
- Downloads run in a foreground service with pause/resume, are persisted across restarts and
  are published to the system Downloads collection. Removing a row deletes the underlying
  file; the folder lives under Download and is configurable in Settings (**Download folder**),
  and the chip auto-hides three seconds after the last transfer finishes.
- Site prompts and permission requests render as pill-sized popups, with per-site
  remember-me answers editable in Site settings.
- Blocked popups get a pill with **Allow once**; long-press brings a context sheet and text
  selections get Copy / Select all, both with haptic feedback.
- Tracking protection (Standard/Strict), so-called "block cross-site trackers" cookies,
  optional cookie-banner answering, a per-page blocked count and per-site exemptions.
- Failed loads render a themed local error page with retry/back; an offline page retries the
  URL by itself. Fullscreen video hides the chrome, rotates and keeps the screen on.
- Find in page opens a bar over the pill, highlights every hit, dims the rest of the page
  and steps through matches with a live `3/17` counter.
- Page translation goes through Gecko's own engine: source language detected (or picked),
  any supported target, models downloaded on first use, and one tap back to the original.
- Passwords live in an encrypted on-device vault (Android Keystore) with a generator and
  autofill through the system autofill framework.
- Search sheet shows the chosen engine's live suggestions plus history matches with
  inline completion; the browser accepts `VIEW`, `WEB_SEARCH` and `SEND` intents.
- Local history keeps one row per URL, grouped by day in the History page with search,
  swipe-to-delete and clear-by-range.
- Signed XPI installation and a native add-on manager through `WebExtensionController`,
  including extension popups in a sheet.
- An optional update channel (About page) checks GitHub Releases, shows a banner when a newer
  beta or stable build exists, downloads the APK and hands it to the system installer; stale
  update APKs are swept automatically.

See [`gecko-browser/README.md`](gecko-browser/README.md) for build instructions and the
extension-signing caveats.
