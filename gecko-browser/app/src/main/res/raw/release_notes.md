## Beta - 19-11-2026

_44 commits since `beta-build-41` | auto-generated 19-11-2026_

### Features
- Add pinned tabs: search-sheet row with pin/unpin from menu, paging, accent highlight, X to unpin, persisted (22e4aa6)
- Add gated release pipeline: precompile -> build -> beta (auto) / stable (manual promotion) (317fff6)
- Downloads/storage: purge OTA APKs, configurable Download folder, delete removes bytes, chip autohide, new-page-only load zoom (465ff20)
- Revamp settings: segmented hub with category pages, author notes, update channel, reddit provider, history swipe/intervals (d762914)
- Add What's new screen with bundled release notes, release publishes notes file as asset (09a71ef)

### Fixes
- Fix CI #20 compile: import scrollBy extension (ScrollableState, foundation.gestures) (eda9f54)
- Revert OTA updates to the in-app flow (df194cb)
- Fix CI #16 compile: PendingDownload has no id (05d74ca)
- Fix CI #15 compile: import mutableIntStateOf in Root.kt (d1ddb9b)
- Fix CI #14 compile: haptics used without local declaration (0802ae5)
- Fix pinned-row compile failure: add explicit pointerInput import (1547637)
- Fix OTA up-to-date comparison: compare release run number against installed versionName (5284fe2)
- Fix beta-release RC download: reference needs.build.outputs.run, not steps.tag (only exists in the build job) (d475ba9)
- Fix build numbering to use commit count instead of workflow run number (dfc0bbc)
- Fix unit test expectation: 'Beta v17-11-2026' ends in -2026 which matches the run-number regex (85a4df0)
- Settings: restore bottom search pill with live results, move Default browser under Search and preferences > Preferences, repair corrupted glyphs (911fa93)
- Fix unresolved references: scope for VM BackHandler, scope coroutine, drop Modifier.weight outside ColumnScope (eea7fd5)
- Fix release paths for artifact upload and body (a7cbd13)
- Fix release job artifact paths (968ee55)
- Fix release asset paths for gecko-browser working directory (7db59c6)
- Add missing height and ColumnScope imports (45abd39)

### Other changes
- Pin reorder one swap per frame + edge auto-scroll; settings layout; popup gestures; toast placement (0a11ec1)
- Pins: port the mockup interaction - hold shows the x, release unpins, push slide animations (3852382)
- Mockup: pins push with FLIP animations (3c53ace)
- Mockup: the x replaces the favicon, not a corner badge (416e699)
- Mockup: long-press a pin shows the red x, release to unpin (7374edb)
- Mockup: long-press a pin to unpin it immediately (156de50)
- Mockup: Essentials pin row - drag-to-reorder, favicon-dominant active outline (2a2fed7)
- Haptic feedback toggle + pin drag-to-reorder + forward/back popup accuracy (b0dd6fd)
- Reorder pins by dragging; background OTA download with silent notification and stale-APK cleanup (417234a)
- Zen Essentials: rectangular thirds tiles in search sheet, favicon-dominant-color accent border, menu pin always visible (809ec5d)
- Mockup: Zen-style essentials row in the search sheet (92f9a8d)
- Opt in to ExperimentalFoundationApi for PinnedRow's combinedClickable (91ef5a2)
- Number builds from a CI-written version.properties, not GitHub's built-in run number (e8639dd)
- OTA: verify downloaded APK integrity before install; beta releases claim Latest badge again (63d87d8)
- Make beta releases regular releases (list order restored) and make OTA channel selection order-independent (606319b)
- Update check on launch runs silently; toasts only from manual Check for updates (6246019)
- Rename setters to avoid Compose property setter clashes (133d329)
- ci: retrigger build (8c755c3)
- AppUpdater: pick releases by channel (beta/stable) (9463842)
- ci: retrigger build (9065233)
- ci: retrigger build (e7a74c8)
- History icon uses text color, trigger CI path (80bb338)
- Trigger CI build (8404718)


**Full changelog:** https://github.com/rLihat/arclike-gecko-brower-for-android/compare/beta-build-41...HEAD

