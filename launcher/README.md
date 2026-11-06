# Vibe Home

A small, offline Android launcher for the diagnosed HY320 / Allwinner H713. **Version 0.5.1** uses large, wide app tiles and a focus-colored background inspired by the supplied reference image. The original launcher remains default after installation; optional default-launcher buttons live in Settings.

## Design

- One complete sharp row and half of the next row as a blurred preview, with four wide tiles per row. Moving down promotes the next row into the full sharp position, including the final row. Tiles have no borders, colored rims or dimming. Installed Android TV banners are used where available, with native styled fallbacks otherwise. Existing favorite order comes first; the remaining apps follow. Projector/system settings entries are excluded from Home.
- Clock/date at the top and the focused app's name above the tiles. Home has no instructions, hints or settings shortcuts; the Settings tab contains picture and connection controls.
- A static radial/vertical gradient follows the focused app's brand color. Known apps have deliberate colors; other apps use a small, cached icon-color sample. No wallpaper bitmap or animation loop. Preview blur is cached separately.
- Row changes glide one row over 220 ms; tab content glides horizontally over 220 ms while the clock/navigation stay in the same positions. A 140 ms lift marks the focused tile: its artwork is about 22% larger than its neighbors and rises 8 dp, with no app-name badge; focused buttons/icons use a white surface. Navigation interrupts pending motion immediately. Existing Android animation settings are respected.
- Native app options still support favorites and ordering through MENU/hold OK. The full app drawer uses the same wide tiles.
- The native projector preview remains available in Settings. It previews/saves drafts only and does not apply hardware changes. See [the investigation](PROJECTOR_CONTROLS.md).

## Launcher buttons

Settings shows the current default and two actions:

1. **Use Vibe Home** enables our app's HOME entry and opens Android's confirmation dialog. Select Vibe Home and **SET AS DEFAULT** to make it your home screen.
2. **Use original launcher** opens the original immediately when it is already default. If Vibe is default, it opens Android's Home settings so you can choose **HTCLauncherEN**, then returns to the original after the choice is verified (Back returns from the Android settings page if it stays open).

Installing or opening Vibe does not enable the HOME entry or change the default. Cancelling a first request disables our optional HOME alias again. The in-progress choice is recorded in private app preferences so returning after process recreation can clean up or finish it. No shell service, root, signature permission, system-package modification or preferred-activity override is used.

This uses the public [Android home-role API](https://developer.android.com/reference/android/app/role/RoleManager#createRequestRoleIntent(java.lang.String)) and [Home settings action](https://developer.android.com/reference/android/provider/Settings#ACTION_HOME_SETTINGS). Other Android confirmations are controlled by the OS. Default grants and switching away from an active Vibe default have not been exercised in the automated trial; the dialog and cancellation recovery were verified while keeping stock HOME selected.

## Performance choices

Native Java/Android Views only, with no runtime dependencies, Compose, WebView, network permission, services, receivers, continuous polling or downloaded artwork. Native clocks update at minute granularity. Focus changes shader colors immediately. Finite property animations run only during interaction, with no idle animation or per-frame image decoding/blur. A down-row transition briefly retains up to four departing cards sharing existing artwork bitmaps; it does not create a screenshot or a second full grid.

Both grids recycle card views and preserve Android's recycler metadata. Home keeps a small active-row/preview window instead of allowing native pixel scrolling to crop the active row. Banner rasters are **320 × 180**, with a **1 MiB** LRU cache; visible cards also retain their images. Icon rasters remain **72 × 72**, with a separate **384 KiB** cache. Blurred previews use **120 × 68** thumbnails and a **256 KiB** cache. One worker shares pending image requests. Weak, tagged targets prevent stale results populating reused cards, and an already populated visible target survives a cache eviction without being reloaded. Missing banners are remembered so they are not repeatedly requested. Blur runs on the image worker. Native banners are blurred directly from their loaded bitmap, while fallback tile snapshots are captured after layout. No live/full-screen blur is used.

Large artwork has a memory cost compared with the earlier icon-only design. Cache bounds and APK size do not measure total process RAM. The current [raised-focus report](RAISED_FOCUS_TEST_REPORT.md) records badge removal and the stronger tile lift; the [0.5.0 motion report](MOTION_TEST_REPORT.md) records row/tab transitions and their performance limits; the [0.4.2 borderless-preview report](BORDERLESS_PREVIEW_TEST_REPORT.md) records the earlier layout/blur work; the [0.4.1 row-visibility report](ROW_VISIBILITY_TEST_REPORT.md) is historical; the [0.4.0 cinematic-home report](CINEMATIC_TEST_REPORT.md) records that design’s measured footprint and limits; the [0.3.0 report](REDESIGN_TEST_REPORT.md) remains historical. Stock launcher/services remain present.

## Build and test

Requires the installed JDK 17, Android platform 34 and build-tools 34.0.0:

```sh
python3 launcher/build.py
```

The build never invokes ADB or downloads dependencies. `launcher/build/vibe-home.apk` is signed with the existing local prototype key. `build/artifact.json` records the APK and source hashes. Keep `local-debug.keystore` for compatible updates; its conventional development password is unsuitable for public releases. SDK paths can be overridden with `ANDROID_SDK_ROOT`, `VIBE_BUILD_TOOLS`, and `VIBE_PLATFORM`.

The user has authorized installing and opening this app for testing:

```sh
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
adb -s 192.168.10.15:5555 shell am start -W -n com.vibehome.launcher/.MainActivity
python3 launcher/check_ui.py --serial 192.168.10.15:5555
# Check every home row for clipping in both directions.
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
# Optional: open/cancel the Android home-role dialog and verify rollback.
python3 launcher/check_ui.py --serial 192.168.10.15:5555 --check-home-picker
```

The UI check navigates pages and menus, opens the read-only projector preview, checks stock HOME, and returns to Vibe. It does not select a different default, change favorites or projection values, install anything, reset counters or clear logs. The optional home-picker check temporarily enables only our HOME alias, cancels the Android request, verifies alias cleanup, and checks returning to the already-default original launcher. Evidence goes to the ignored `test-results/` directory. Run it only on the authorized projector when UI interaction is appropriate.

The manifest targets API 30, supports API 26+, includes no native ABI libraries and requests no permissions. An exported HOME alias is disabled by default. This avoids the automatic chooser caused by registering a second enabled HOME candidate on this firmware. Only pressing the Settings action enables the alias and requests the OS selection flow.

Its draft provider is read-only and UID-guarded. The one-shot hardware probe/test remains separately gated, never automatically invoked by home navigation. No projection write has been approved or performed.

Historical reports: [first prototype](TEST_REPORT.md), [native projector editor](NATIVE_SETTINGS_TEST_REPORT.md). Android references: [TV navigation](https://developer.android.com/training/tv/get-started/navigation), [GridView](https://developer.android.com/reference/android/widget/GridView).

## Credits

Inspired by [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (focus-driven shelf, favorites dock, restrained motion) and [Tellyscope](https://github.com/man15h/tellyscope) (tonal surfaces, clear menus and review-first changes). See the [root README](../README.md#credits-and-inspiration) for inspected commits and licenses. Both reference clones are gitignored. No code, fonts or assets from either repository are incorporated. Their debloat/install scripts were not executed.
