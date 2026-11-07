# Vibe Home

A small, offline Android launcher for the diagnosed HY320 / Allwinner H713. **Version 0.14.0** places the pin icon above the upper-left corner of each favorite's artwork. The header has **Inputs**, **Edit** and **Settings**, with default launcher selection under **Settings → About**.

The [matched default-launcher comparison](DEFAULT_LAUNCHER_COMPARISON_REPORT.md) tests Vibe against HTCLauncherEN on the actual projector. It distinguishes the active launcher's footprint and idle CPU from whole-system available RAM, including the old launcher's retained foreground service.

## Design

- One complete sharp row and half of the next row as a blurred preview, with four wide tiles per row. Moving down promotes the next row into the full sharp position, including the final row. Tiles have no borders, colored rims or dimming. Installed Android TV banners are used where available, with native styled fallbacks otherwise. Pinned apps come first. Projector and system settings entries are excluded from Home.
- Clock/date at the top and the focused app's name above the tiles. Edit mode shows remote instructions above the focused app name. The Settings dropdown contains device settings shortcuts and About.
- A static radial/vertical gradient follows the focused app's brand color. Known apps have deliberate colors; other apps use a small, cached icon-color sample. No wallpaper bitmap or animation loop. Preview blur is cached separately.
- Row changes glide one row over 220 ms while the clock and navigation stay in place. A 140 ms lift marks the focused tile: its artwork is about 22% larger than its neighbors and rises 8 dp, with no app-name badge; focused buttons/icons use a white surface. Navigation interrupts pending motion immediately. Existing Android animation settings are respected.
- Home lists installed apps in rows. A small pin above the upper-left corner marks each favorite. The pin follows the tile's focus lift. MENU or hold OK opens app options, including favorites.

The 0.14.0 device check confirmed that focused and resting pins sit fully outside the artwork and that all five rows retain the half-row preview. See the [device results](test-results/20261107-outside-pins/device-verification.json) and [projector screenshot](test-results/20261107-outside-pins/rows-final/row-0.png).

Choose **Edit** beside Inputs, focus an unpinned tile and press **OK** to pick it up. The arrows move it within the unpinned apps. Press **OK** again to save the drop. **Back** cancels the current move, and **Done** or **Back** with no tile held exits Edit mode. Unpin a favorite before adding a fifth. When upgrading an older layout, the first four available favorites keep their pins and come first, and the other apps keep their relative saved order.

The 0.13.0 projector checks covered the first-row pin lock, attempts to enter a pinned slot or add a fifth favorite, unpinned movement, and all five Home rows. See the [device results](test-results/20261107-locked-pins/device-verification.json), [row checks](test-results/20261107-locked-pins/rows/result.json) and [Edit screenshot](test-results/20261107-locked-pins/pinned-locked.png).

The 0.12.0 trial covered moving tiles across rows, cancelling a move, and saving an order across reopening Home. See the [historical edit results](test-results/20261107-edit-order/device-verification.json).

The 0.11.0 projector check covered all five Home rows and navigation between Inputs and Settings. It found four pinned and thirteen unpinned tiles, with pin state preserved through recycling. See the [device results](test-results/20261107-favorite-pins/device-verification.json) and [Home screenshot](test-results/20261107-favorite-pins/home-final.png).

## Settings dropdown

The top **Settings** pill opens a rounded dropdown with white focus and monochrome icons. OK, click or Down opens it. Back, Up from the first row or an outside click dismisses it. **Wi-Fi** opens Android TV network settings. **Bluetooth** opens Android TV Settings. Select Bluetooth there to open the system side panel on this firmware. **Projector settings** opens the original vendor settings home. **About** shows Vibe's version and default launcher controls.

The separate Vibe settings and app drawer pages were removed in 0.10.0. The [hybrid settings trial](HYBRID_SETTINGS_TEST_REPORT.md) describes the earlier page. The [native parity report](SETTINGS_PARITY_REPORT.md) and [migration report](NATIVE_SETTINGS_MIGRATION_REPORT.md) record the older native settings trials.

The HY320 test covered all five Home rows, the Settings shortcuts, and opening and cancelling Android's launcher chooser from About. The installed APK matches the verified build. See the [device results](test-results/20261107-about/device-verification.json) and [About screenshot](test-results/20261107-about/about-remote.png).

## Inputs

The top-right **Inputs** button replaces Apps. OK/click or Down opens a compact, rounded dropdown over the current screen, with a white focused row, source icons and connection status. Back, Up from the first row, or an outside click dismisses it; Left/Right returns focus to the neighboring navigation button. The background and app artwork remain undimmed.

Sources follow the projector's own `/product/etc/config.ini` `sourceList`/`sourceListTitle`, rather than the unused ports also advertised by its generic TV HAL. This unit exposes **HDMI**, mapped by its firmware to **HDMI2**. Selecting it opens the installed vendor HDMI viewer with the same `input_source` extra used by stock. Connected/Standby/Not connected comes from Android's TV input state; it is not a guarantee of a visible HDMI picture.

**USB** opens the installed vendor media browser. Mounted removable storage descriptions appear when available; otherwise the menu says **No drive connected**. USB storage is browsed as files, not treated as an HDMI video signal. The browser owns storage access and volume selection; Vibe requests no storage permission. Configuration/status work uses the existing worker. TV-input callbacks and a media-mount receiver run only while the dropdown is open, with stale-result guards after dismissal; there is no polling. See the [Inputs validation report](INPUTS_TEST_REPORT.md) for tested behavior and hardware limits.

## Launcher buttons

**Settings → About** shows the current default and two actions.

1. **Use Vibe Home** enables our app's HOME entry and opens Android's confirmation dialog. Select Vibe Home and **SET AS DEFAULT** to make it your home screen.
2. **Use original launcher** opens the original immediately when it is already default. If Vibe is default, it opens Android's Home settings so you can choose **HTCLauncherEN**, then returns to the original after the choice is verified (Back returns from the Android settings page if it stays open).

Installing or opening Vibe does not enable the HOME entry or change the default. Cancelling a first request disables our optional HOME alias again. The in-progress choice is recorded in private app preferences so returning after process recreation can clean up or finish it. No shell service, root, signature permission, system-package modification or preferred-activity override is used.

This uses the public [Android home-role API](https://developer.android.com/reference/android/app/role/RoleManager#createRequestRoleIntent(java.lang.String)) and [Home settings action](https://developer.android.com/reference/android/provider/Settings#ACTION_HOME_SETTINGS). Other Android confirmations are controlled by the OS. Four actual selections through Android's Home settings chooser were separately approved and verified in the [matched comparison](DEFAULT_LAUNCHER_COMPARISON_REPORT.md), ending with Vibe restored. That verifies the OS chooser, not the full positive lifecycle of Vibe's built-in selection buttons. Their dialog/cancellation recovery was previously verified; reboot persistence remains untested.

## Performance choices

Native Java/Android Views only, with no runtime dependencies, Compose, WebView, network permission, background services, boot receivers, continuous polling or downloaded artwork. Native clocks update at minute granularity. Focus changes shader colors immediately. Finite property animations run only during interaction, with no idle animation or per-frame image decoding/blur. A down-row transition briefly retains up to four departing cards sharing existing artwork bitmaps; it does not create a screenshot or a second full grid.

Home recycles card views and preserve Android's recycler metadata. Home keeps a small active-row/preview window instead of allowing native pixel scrolling to crop the active row. Banner rasters are **320 × 180**, with a **1 MiB** LRU cache; visible cards also retain their images. Icon rasters remain **72 × 72**, with a separate **384 KiB** cache. Blurred previews use **120 × 68** thumbnails and a **256 KiB** cache. One worker shares pending image requests. Weak, tagged targets prevent stale results populating reused cards, and an already populated visible target survives a cache eviction without being reloaded. Missing banners are remembered so they are not repeatedly requested. Blur runs on the image worker. Native banners are blurred directly from their loaded bitmap, while fallback tile snapshots are captured after layout. No live/full-screen blur is used.

Large artwork has a memory cost compared with the earlier icon-only design. Cache bounds and APK size do not measure total process RAM. The current [raised-focus report](RAISED_FOCUS_TEST_REPORT.md) records badge removal and the stronger tile lift; the [0.5.0 motion report](MOTION_TEST_REPORT.md) records row/tab transitions and their performance limits; the [0.4.2 borderless-preview report](BORDERLESS_PREVIEW_TEST_REPORT.md) records the earlier layout/blur work; the [0.4.1 row-visibility report](ROW_VISIBILITY_TEST_REPORT.md) is historical; the [0.4.0 cinematic-home report](CINEMATIC_TEST_REPORT.md) records that design’s measured footprint and limits; the [0.3.0 report](REDESIGN_TEST_REPORT.md) remains historical. Stock launcher/services remain present.

For the current Vibe-versus-stock evidence, use the [matched comparison](DEFAULT_LAUNCHER_COMPARISON_REPORT.md). Its alternating default-HOME conditions use the same idle, focus-navigation and vendor Wi-Fi workload without stopping either launcher. `measure_home_comparison.py` measures one already-selected condition and refuses a mismatched default; `select_home_for_comparison.py` records one explicitly approved chooser selection. Future default changes still require their own authorization.

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
# Check the dropdown and opening/returning from vendor HDMI and USB browsers.
python3 launcher/check_inputs.py --serial 192.168.10.15:5555
# Check every home row for clipping in both directions.
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
# Optional: open and cancel Android default launcher selection.
python3 launcher/check_ui.py --serial 192.168.10.15:5555 --check-home-picker
```

The UI check exercises Home app options, the Android and vendor settings shortcuts, and About. The optional picker check opens and cancels Android's default launcher UI and compares the starting and ending HOME resolver. Evidence goes to the ignored `test-results/` directory.

The manifest targets API 30, supports API 26+, includes no native ABI libraries and retains the existing permissions for compatible updates. An exported HOME alias is disabled by default. This avoids the automatic chooser caused by registering a second enabled HOME candidate on this firmware. The Use Vibe Home action can enable the alias and request the OS selection flow.

Its draft provider is read-only and UID-guarded. The one-shot hardware probe/test remains separately gated, never automatically invoked by home navigation. No projection write has been approved or performed.

Historical reports: [first prototype](TEST_REPORT.md), [native projector editor](NATIVE_SETTINGS_TEST_REPORT.md). Android references: [TV navigation](https://developer.android.com/training/tv/get-started/navigation), [GridView](https://developer.android.com/reference/android/widget/GridView).

## Credits

The Wi-Fi, Settings and HDMI vector icons come from CleanYourTV at commit `fc148d5a9707b30e8f49f489625947421740dc55`. The MIT notice is included in the APK and in [CLEANYOURTV-LICENSE.txt](CLEANYOURTV-LICENSE.txt). See the [root README](../README.md#credits-and-inspiration) for both reference projects.
