# Vibe Home projector test

2026-11-05, Asia/Jerusalem. **Installed and left open: version 0.1.3 (code 4), `com.vibehome.launcher`.** User explicitly approved installation/opening as an ordinary app while keeping the stock launcher as HOME.

## Verified on the actual HY320

- Network ADB identity matches HY320 / `UI_Veng.projector.20241225.164550`.
- APK installed and activity launched successfully. Final cold-start `am start -W` time was 977 ms; this single observation is not a benchmark.
- Screenshot shows the native UI fitting the 1920×1080 display, with six installed favorite apps and four projector-settings cards.
- D-pad Right/Left/Down navigation reaches the Projection card; OK opens the actual vendor ProjectionActivity3.
- The vendor page displays **Screen Zoom, Projection Mode and Keystone Correction**.
- Back from that page returns directly to Vibe Home after correcting the task-stack handling.
- Menu opens the app drawer. Selecting Calculator opens `com.android.calculator2/.Calculator`.
- HOME opens `com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity`. Final package-manager resolution independently confirms that same stock HOME.
- Final build has **no HOME intent filter**, no requested permissions, services, receivers or providers.
- Compared 21 projection/zoom/keystone/panel properties before and after navigation: **no differences**. No control value was selected or adjusted.
- Disabled-package list still contains only `com.android.mtp` and `com.android.nfc`, matching the diagnostic baseline. No system package was disabled or uninstalled.
- No Vibe Home fatal exception appears in the retained final log.

## Corrections made during the approved trial

The initial 0.1.0 build registered as a HOME candidate. Although it did not set itself as default, this firmware responded by resolving HOME to Android's chooser. Removing HOME registration in the normal-app test build restored stock HOME resolution without a default-setting command. A separate, explicitly approved default-launcher trial would require a build with HOME registration restored.

The initial vendor shortcut used NEW_TASK, which reused an older vendor-settings task; Back therefore returned to the older vendor screen. Version 0.1.1 corrected vendor navigation to stay in Vibe Home's task. Ordinary app launches remain separate tasks. Version 0.1.3 also releases the app-drawer dialog reference after dismissal so its icon views are not retained by that field.

The approved test involved installing the initial APK and corrective updates 0.1.1–0.1.3. Only our package was installed/updated. No root, reboot, flash, system-package operation, projection-value change or global settings command was performed.

## Memory and performance limits

The initial 0.1.0 screen measured **34,231 KiB PSS (33.4 MiB)**. The final 0.1.3 build measured **59,172 KiB PSS (57.8 MiB)** after projector-menu, app-drawer and app-launch tests. Runtime footprint grows beyond APK size, and shared mappings/graphics attribution can shift. Do not present the smallest initial value as the sustained footprint or a guaranteed saving.

Final cumulative frame counters were 100 rendered / 22 janky. These include cold startup and UI transitions and are too small and uncontrolled to establish an improvement over the stock launcher's earlier 31.69% cumulative result. The stock launcher remains installed/enabled, so lower per-process PSS is not the same as lower total device RAM use. A controlled repeated navigation workload remains the next performance validation.

Pin/unpin persistence, sustained app-switch performance, Wi-Fi/Bluetooth shortcut flows, physical projection adjustments and firmware keep-alive behavior after a future HOME change remain untested.

## Stock-launcher exception observed

During package installation/update, logs recorded fatal exceptions in **the stock launcher**, `com.htc.htclauncherhighenglishd08`: `NoSuchMethodError` for Kotlin `getAllViews` inside its bundled code. This was not a Vibe Home stack trace. Its timing is consistent with the stock launcher's response to package-list changes, but the exact cause was not proven. The stock launcher subsequently reopened successfully using HOME. No vendor code or APK was patched to address it.

## Evidence

Raw commands/results, activity stacks, UI hierarchies, screenshots, logs, property captures, installed-APK metadata and derived `result.json` are in:

`test-results/20261105T165109Z/`

The final screenshot is `v4-final-screen.png`; `v4-test-commands.json` records the final runtime checks. `installed-artifact.json` matches the installed 0.1.3 APK. `evidence-hashes.json` hashes the captured files available when verification ran. Build/source hashes and signature checks are also retained in `build/artifact.json` and `../launcher-build.log`.

Vibe Home remains an ordinary app accessible from the stock app list. Selecting a new default HOME, modifying projection values, disabling packages or uninstalling the test app requires separate user authorization.
