# Cinematic home and launcher selection — 0.4.0

2026-11-06, Asia/Jerusalem. Installed code 7 on the authorized HY320, firmware `UI_Veng.projector.20241225.164550`, Android 11 / 1920 × 1080 at density 320.

## Result

Home now follows the supplied visual reference: clock/date in the top left, navigation in the top right, focused app name above four large, wide tiles, and a brand-colored glow. The next row remains partially visible and scrolls with the remote. Tiles use installed app TV banners or native text/icon fallbacks; selected tiles have a bright border while other tiles are dimmed.

All projector/connection controls are in Settings. Home contains no control shortcuts or navigation hints. Existing favorites/order remain untouched and appear before the remaining apps; system/projector-settings app entries are excluded from Home. The Apps tab retains the complete launchable-app list.

Settings includes **Use Vibe Home** and **Use original launcher**, plus the current default. No global default changed during testing.

## Launcher selection

An exported HOME alias is disabled in the manifest. Installing/opening Vibe leaves it inactive, preserving stock HOME. Pressing Use Vibe enables only that alias and opens [Android's home-role confirmation](https://developer.android.com/reference/android/app/role/RoleManager#createRequestRoleIntent(java.lang.String)). The tested dialog lists **HTCLauncherEN** (current default) and **Vibe Home**, with CANCEL and SET AS DEFAULT.

Cancellation disables the newly enabled alias again. Package-manager resolution and candidate queries then confirm stock HOME without a Vibe candidate. A pending choice is recorded in private preferences before alias activation so normal process recreation can finish/clean up it on resume. No reboot/force-stop recovery test was performed.

Use original opens stock immediately when it is already default; this path was tested. If Vibe is selected, the button opens the [Android Home settings picker](https://developer.android.com/reference/android/provider/Settings#ACTION_HOME_SETTINGS); choose HTCLauncherEN and return from the picker if it stays open. The app verifies the choice before disabling its own alias and opening stock. This latter path and a positive Vibe default grant were not executed, preserving the user's current default. No system package is disabled, and no shell/root service or privileged permission is used.

## Checks passed

- Signed APK build, no requested permissions, matching installed APK/source hashes.
- Home/Apps remote selection, scrolling and app options; all 12 visible drawer cards are present in the captured hierarchy.
- Home contains no Projector, Wi-Fi, Bluetooth, favorites/help section, or instruction footer. Settings contains the projector/connection controls and both launcher actions; layout visually checked at 1080p.
- Netflix focus sets a red background; VLC focus sets orange. The same background pixel at (700, 300) changes from RGB **158, 2, 32** to **179, 99, 32**. These are screenshots, not generated mockups.
- Pressing OK on the VLC tile opens `org.videolan.vlc/.StartActivity`; return to Vibe succeeds.
- Android home-role prompt opens; Back cancels it; stock HOME resolution/candidates are restored. Original-launcher button opens stock. Final screen is Vibe Home with Netflix focused, while HOME still opens stock.
- Native projector preview opens and Back returns. **13 present projection-related properties** remain unchanged through the complete trial, as does the disabled system-package list. No hardware setter, projection adjustment, root, reboot, flash or system-package modification occurred.
- No Vibe fatal-process/ANR entry found in the retained log capture. This is a bounded UI trial, not a prolonged stability test.

## Cost and performance boundary

Native Java/Android Views only, no runtime libraries, WebView, network permission, background service or animation loop. Both grids recycle cards. Icons remain 72 × 72 / 384 KiB cache; banner rasters are 320 × 180 / 1 MiB cache. Visible cards also retain bitmaps. Pending requests share one worker and weak/tagged targets; an already populated visible target avoids reloading after cache eviction. Banners come from installed APK resources, without network downloads or bundled artwork. The full-screen glow is two cached GPU gradients, reconfigured only for size/theme changes.

| Measurement | Final trial |
| --- | --- |
| Signed APK | 49,698 bytes |
| Home PSS after navigation | 43,224 KiB (42.2 MiB) |
| PSS after full UI / native preview / home-picker trial | 66,873 KiB (65.3 MiB) |
| Eight right/left pairs: additional frames / janky frames | 34 / 1 |

Large artwork and additional windows consume more memory than the previous icon-only snapshot (29.9 MiB home). The measurements include resource warm-up, framework caches and window lifecycle effects; they do not establish steady-state use, a regression percentage or lower total device RAM consumption. Stock launcher/services remain active. No APK-size-to-RAM equivalence or performance improvement is claimed. Reboot/default-launcher persistence and firmware keep-alive behavior remain untested.

## Reproduction and evidence

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_ui.py --serial 192.168.10.15:5555 --check-home-picker
```

Use only during the authorized app/UI trial. The optional home-picker check temporarily enables our HOME alias, opens/cancels the Android request, verifies cleanup, and tests the already-default original launcher. It never selects a different default. It does not modify favorites, projection values or system packages, clear logs or reset frame counters. The main script records ADB commands and hashes. The extra color/app-launch trial records separate commands and screenshots.

Installed APK SHA-256: `af0cbc4499fef687a72b85f50113735302340687fcc5157faddffcb2546c46e8`.

[Evidence summary](test-results/20261106-cinematic-final/evidence-summary.json) · [Home](test-results/20261106-cinematic-final/final-netflix-home.png) · [Orange VLC theme](test-results/20261106-cinematic-final/vlc-focus.png) · [Settings](test-results/20261106-cinematic-final/settings.png) · [Android confirmation](test-results/20261106-cinematic-final/android-home-request.png).

Credits and ignored reference clones remain documented in the root and launcher READMEs. No reference-repository code/assets were incorporated. The prior installed 0.3.0 APK is retained locally at `test-results/20261106-cinematic/previous-v0.3.0.apk`; no rollback was performed.
