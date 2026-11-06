# Home redesign — Vibe Home 0.3.0

2026-11-06, Asia/Jerusalem. Updated and tested as an ordinary Android app on the authorized `192.168.10.15:5555` HY320, build `UI_Veng.projector.20241225.164550`, 1920 × 1080 at density 320. Stock HOME remains `com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity`.

## Delivered design

Graphite backgrounds, warm-white type and pale-lime focus. The home screen uses a large focused-app title, a horizontal favorites shelf, simple line artwork and four compact shortcuts. Apps and Settings have full-screen layouts; app options use a matching side sheet. Existing favorites are retained, and options include add/remove and move left/right. MENU is an explicit alternative to holding OK.

CleanYourTV informed the focus-driven shelf, favorites dock and restrained motion. Tellyscope informed the tonal surfaces and clear menu hierarchy. The independently implemented code uses none of their source, fonts or artwork. Both clones remain gitignored and credited in the root and launcher READMEs.

The native projector editor shares the new colors and focus treatment. It still previews and saves drafts only. No hardware preview/setter was invoked.

## Lean implementation

- Native Java and Android Views, with no runtime dependencies, WebView, Compose, network permission, services, receivers, downloaded wallpaper or periodic device monitoring.
- Recycled native GridView cards, rather than materializing the complete app drawer.
- 72 × 72 ARGB icons (20.25 KiB each) with a 384 KiB LRU cache; previous limits were 80 × 80 and 512 KiB. These limits exclude visible views and other process memory.
- One icon worker, shared pending requests, and weak, tagged image targets. Late results cannot populate a card rebound to another app.
- Prebuilt focus drawables, static vector-style artwork, no icon-color sampling, background recoloring, blur, focus-scale animations or wallpaper textures. Dialog/activity transitions are disabled within this app's theme; global Android animation settings were not changed.
- Package discovery runs on resume, off the main thread, and rebuilds only when its launchable-app signature changes. Home navigation does not read projector properties; the explicit projector editor does so on its own worker.

## Validation

The current trial is reproducible with:

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_ui.py --serial 192.168.10.15:5555
```

Use only under the existing installation/UI-testing authorization and while projector interaction is appropriate. The check does not install anything itself or change favorites/device settings. It captures screenshots, UI hierarchies, logs, memory and cumulative frame counters without clearing logs or resetting counters.

The final on-device evidence is under `test-results/20261106-redesign-accepted/`:

- Home, Apps, Settings, native projector preview, app-options sheet, Back navigation, and stock HOME.
- Visible remote focus after pointer navigation; selection and scrolling in the native grid; app options from both favorites and the drawer.
- A visible-card hierarchy assertion covering the measured/recycled first card. Testing found that replacing GridView's layout parameters erased framework recycler flags; rebinding now preserves those parameters.
- The same projection-related properties and disabled-package list before/after navigation. Absent/empty properties are retained in the raw captures; the compared dictionary counts the relevant properties actually present.
- Geometry contract tests pass: identity, unchanged baseline, retained trapezoid, bounds and unsafe enlargement rejection.
- APK signature verification and no requested permissions. No HOME intent filter, services or receivers were added. Hardware helper/state/provider sources remain unchanged by the redesign.

A separate runtime trial in `test-results/20261106-redesign-release/` verified that selecting VLC in the native app grid and pressing OK opens `org.videolan.vlc/.StartActivity`, followed by returning to Vibe. Favorite writes/reordering persistence and physical-remote long-hold behavior were not exercised; MENU was tested. Test builds and intermediate captures are retained separately, with command and file-hash records.

## Performance evidence and limits

The UI check measures eight right/left focus pairs using cumulative gfxinfo counter differences. This short sample includes resource warm-up and unrelated clock/framework work; it is not a controlled speed or responsiveness benchmark. Earlier captures vary across repeated app updates and visits to multiple windows.

The previous 0.2.0 trial captured 15 additional rendered frames and zero additional janky frames. Intermediate 0.3.0 trials captured 16 additional frames and zero to two additional janky frames. Home PSS samples varied approximately 33–55 MiB across these updates; opening menus/editor and returning home can retain additional graphics resources. A smaller icon cache and APK do not establish lower total PSS or a device-wide RAM saving. No measured improvement over 0.2.0 is claimed.

The final artifact, installed APK/source hashes, frame deltas, PSS and unchanged-property result are recorded in the accompanying evidence summary. Longer remote use and comparable app-switch trials remain useful before choosing a default-launcher trial.

## Final build measurements

| Measurement | Result |
| --- | --- |
| Installed version | 0.3.0 (code 6) |
| Signed APK | 45,602 bytes |
| Visible app cards with labels | 18 of 18 |
| Focus-sequence frames / additional janky frames | 17 / 0 |
| Home PSS after focus sequence | 30,617 KiB (29.9 MiB) |
| PSS after full UI trial and returning home | 47,968 KiB (46.8 MiB) |
| Present projection properties compared / changed | 13 / 0 |

Installed APK SHA-256: `2c4dcd7e99450ea104091ec42682e1c562a64043560d8e39d520c8bb16dfd69d`. Its hash matches the local APK; all recorded source/resource hashes match. No Vibe fatal-process/ANR entry was found in the retained log capture. These are bounded observations, not a sustained stability test. The home sample is lower than the previous trial's 32.4 MiB, but the wider run-to-run variation prevents attributing a RAM reduction to the redesign.

[Machine-readable evidence](test-results/20261106-redesign-accepted/evidence-summary.json), [home](test-results/20261106-redesign-accepted/final-home.png), [app drawer](test-results/20261106-redesign-accepted/apps.png), [settings](test-results/20261106-redesign-accepted/settings.png), [app menu](test-results/20261106-redesign-accepted/app-options.png).
