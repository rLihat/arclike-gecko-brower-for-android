# horiZen — Release Notes

## 🎉 Major feature

**OTA updates, in-app** — horiZen can now check GitHub for a newer build, download it over the air, and hand it to the system installer. Installing it is as simple as tapping **Download** on the banner that appears when a new build is available.

## ✨ QoL

- 🌐 **Link app-opening prompts** for installed apps (e.g. YouTube opens in the YouTube app when possible)
- 🎨 **Accent colours** — pick a new look under **Settings → Look and feel** (Firefox purple default, Ocean blue, Sunset orange, Forest green, Raspberry berry)
- 🌗 **Appearance tri-state** — System / Light / Dark
- 🔌 **Extension mini menu** — tap the puzzle icon in the menu to open a scrollable list of enabled add-ons; tap any add-on to open its menu
- 🏪 **Visit Store** and **Install from local** buttons on the Extensions screen
- 🚦 **Check for updates on launch** + **Check for updates** in Settings → Updates
- 🧩 Built-in search engines can now be **reordered, disabled or enabled** from Settings
- 🖊️ **Extensions list** — icons render where Gecko decodes them; permissions detail is one tap away
- 💬 **Toggleable translation** of pages already works via the menu → **Translate**

## 🐛 Bug fixes

- 🔗 **App links** detection now matches both `BROWSABLE` and `DEFAULT` categories on installed apps
- 🖼️ **Icons never appeared** for sites, engines and add-ons — full-circle paths redrawn with `addOval`; favicons prewarmed at startup
- 🔤 Full-circle drawable icons (search, history, copy-link, globe, person, image-sun) now render correctly
- 🐛 `QueryErrorMessage` resolved by enabling `BuildConfig`
- ⚙️ `versionCode` was fixed; CI builds now bump `versionCode` with each run — phones update normally
- 🧹 Removed the **orange dot** badge from the Extensions menu action
- 📁 **`.xpi` files download through the add-on installer** instead of Downloads
