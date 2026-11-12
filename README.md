# Arc-inspired GeckoView Browser

An Android browser built on GeckoView (Firefox's engine) with a custom, edge-to-edge
UI: no status bar, no address bar, and a single morphing navigation pill.

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

## Android app

Native Kotlin reimplementation of the mockup chrome with real GeckoView content:

- No status bar / no address bar; web content runs behind the floating pill.
- Pill condenses to a domain chip on scroll down and restores its icons on scroll up.
- Search + menu sheets morph out of the pill; settings, site settings and account are
  full-height pages. Dark mode is a persisted chrome theme.
- Tabs are a horizontal recents carousel; each `GeckoSession` keeps its own history.
- Signed XPI installation through `WebExtensionController`.

See [`gecko-browser/README.md`](gecko-browser/README.md) for build instructions and the
extension-signing caveats.
