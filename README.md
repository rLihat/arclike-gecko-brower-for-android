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
  full-height pages. Dark mode is a persisted chrome theme.
- Tabs are a horizontal recents carousel with captured page thumbnails; each `GeckoSession`
  keeps its own history and both tabs and settings survive a cold start.
- Downloads run in a foreground service with pause/resume, are persisted across restarts and
  are published to the system Downloads collection.
- Site prompts and permission requests render as pill-sized popups, with per-site
  remember-me answers editable in Site settings.
- Search sheet shows the chosen engine's live suggestions plus history matches with
  inline completion; the browser accepts `VIEW`, `WEB_SEARCH` and `SEND` intents.
- Local history keeps one row per URL, grouped by day in the History page with search,
  swipe-to-delete and clear-by-range.
- Signed XPI installation and a native add-on manager through `WebExtensionController`,
  including extension popups in a sheet.

See [`gecko-browser/README.md`](gecko-browser/README.md) for build instructions and the
extension-signing caveats.
