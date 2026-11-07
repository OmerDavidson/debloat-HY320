# Native settings migration — 0.7.0 (partial)

2026-11-06, Asia/Jerusalem. This release adds native Settings categories and removes vendor-settings redirects. **It does not complete functional parity with the legacy settings app.** Several entries are read-only or explicitly unavailable; the user requested every control to be native, so external vendor editors are not used as a substitute.

## Scope and source evidence

The connected target was reidentified as HY320 / sun50iw12p1 / Android 11, firmware `UI_Veng.projector.20241225.164550`, display 1920 × 1080 at density 320. Starting HOME was **Vibe Home (`.HomeEntry`)**, and that selection must be preserved. This differs from historical stock-default trials.

The installed legacy settings menu was inspected without selecting a new value. Its top-level sections are Networking, Bluetooth, Language, Other, Projection, Audio, Date/time and About device. The actual Projection menu exposed Screen zoom, Projection mode, Keystone correction, Brightness and color, and Screen saver. Other exposed Application settings, Factory reset, Developer options and Advanced settings. About exposed device information, local upgrade and online upgrade. The proprietary APK and `/product/etc/config.ini` were used only as local evidence; none of its code, icons or layouts are bundled.

Vibe reorganizes these into Picture, Connections, Sound, Language & input, Date & time, and System. The legacy application list was not imported. Vibe's existing All apps entry, app options, favorites and order remain available. Inputs are unchanged. Both launcher-selection buttons remain.

## What is implemented

- Native, remote-navigable sidebar and scrolling control pane, preserving the selected category when returning from a child screen.
- Native media-volume slider using `AudioManager`, with user-originated changes only and live updates while Settings is visible.
- Native Wi-Fi status, on-demand permission-gated scanning, and an open/WPA2 personal network suggestion form. Adding a suggestion requires an explicit user action and can require Android approval. A successful suggestion is never reported as a successful connection.
- Native Bluetooth enable/disable confirmation, paired-device list, on-demand discovery and pairing request. Turning Bluetooth off warns that remotes/accessories will disconnect.
- Current locale/keyboard and Android's keyboard selection confirmation.
- Native date/time format selectors using the separately consented Android Modify system settings permission. Merely visiting these pages grants nothing and writes no value.
- Native model, OS/API, firmware, kernel, hardware, storage, memory snapshot and debugging/USB status.
- Existing native projection draft editor with its vendor-controls redirect removed. Native settings shows live screen size and panel mode where readable. A separate read-only H713 picture-service snapshot uses getter transaction 98 for brightness/contrast/saturation/hue/sharpness; it contains no setter.

Firmware/configuration/picture snapshots use the existing worker; connection and volume state also refreshes on relevant broadcasts and user actions. There are no services, boot receivers, polling, network downloads, shell setters, generic privileged bridges or permission auto-grants. Settings receivers are unregistered when leaving; Bluetooth discovery initiated here is cancelled. New permissions and their purposes are documented in [README](README.md#native-settings--current-capabilities).

## Functional gaps — not completed

The full control-by-control matrix is in [README](README.md#native-settings--current-capabilities). In particular:

- Zoom/mode/manual corners remain **preview/draft only**. Automatic keystone, calibration and picture adjustment writes have not been validated from the ordinary app UID. Successful getters do not establish write access or persistence.
- Wi-Fi power, full saved-network management and the internet-sharing hotspot are not implemented for this ordinary target-30 app. Network suggestions do not replace those controls.
- Bluetooth reconnect/disconnect/forget are not implemented.
- Global system language, automatic time, manual clock and time-zone changes require protected capabilities that have not been granted. Keyboard administration is incomplete.
- Screen saver changes require synchronized vendor-property and Android-timeout integration and are unavailable.
- Local/online firmware update, factory reset, developer changes and the broad Android advanced-settings suite are not implemented. The native entries explain this and contain no executable reset/update.

Android's [Wi-Fi restrictions](https://developer.android.com/reference/android/net/wifi/WifiManager#setWifiEnabled(boolean)), [suggestion behavior](https://developer.android.com/develop/connectivity/wifi/wifi-suggest), and [protected permissions](https://developer.android.com/reference/android/Manifest.permission) constrain what a normally installed APK can do. Lowering target SDK, rooting, system signing, installing a privileged companion, granting secure-settings access, or modifying system packages was not used to bypass these limits.

## Validation

Evidence directory: [test-results/20261106-settings/](test-results/20261106-settings/). The previous installed 0.6.0 APK was retained before replacement and its SHA-256 matched the local prior build. Each corrective install checks the current installed hash against the previous known build and verifies current source hashes against the candidate artifact. A new unexpected install aborts this procedure.

Candidate APK: **0.7.0 (code 13)**, **78,370 bytes**, SHA-256 `0dbfb4dfc837edfcf1e960b3ae209bc42b87f9f1b0fad40989ad150ff7ef5cb7`. Build signatures verify with APK signature schemes v2/v3. Corrective trial hashes are retained separately in the evidence directory. One test exposed touch-to-D-pad focus loss; the final source explicitly focuses the selected native category/control on click.

The [native Settings check](test-results/20261106-settings/native-verified/result.json) passed on this final candidate. It visited all six categories, scrolled to picture and maintenance controls, opened/returned from the native projection preview, cancelled scan/network/format/reset explanations, exercised touch-to-D-pad category/control focus, and verified Back/Home navigation. The read-only panel getter reported **front/table**, and the ordinary app's picture getter displayed **brightness 50/100**. No Vibe fatal-exception/ANR markers appeared in its bounded log sample.

The starting Vibe HOME, disabled-package list, 16 tracked projection/screen-saver/USB properties and selected audio/network/time/input settings remained unchanged across this trial. Location and Modify system settings permissions were not granted. Actual volume changes, Bluetooth switching/pairing, Wi-Fi discovery/submission/connection, format writes and physical projector adjustments remain **untested**. No mutating control is exercised by those checks: they do not change volume, grant permissions, scan, pair, submit a network, reset, upgrade, change HOME, or apply projection geometry.

The [Home-row regression check](test-results/20261106-settings/home-rows/result.json) passed across **5 distinct rows**, down and up, including the final row, app options and app drawer access. The viewport remains 382 px, active row 236 px and next-row preview 118 px; HOME and tracked projection values stayed unchanged.

[Final installed-artifact verification](test-results/20261106-settings/final-verification.json) confirms APK SHA-256 `0dbfb4dfc837edfcf1e960b3ae209bc42b87f9f1b0fad40989ad150ff7ef5cb7`, version 0.7.0/code 13, and a source/build/installed-hash match. The full before/after selected-setting comparison, HOME, disabled packages and projection/screen-saver/USB properties remained unchanged from the beginning of this task. A separate current-process bounded log capture contains no fatal-exception/ANR marker. No sustained performance claim is made.

Screenshots: [native Settings](test-results/20261106-settings/native-verified/settings.png), [live picture values](test-results/20261106-settings/native-verified/picture-values.png), [Sound](test-results/20261106-settings/native-verified/sound.png), [projection draft](test-results/20261106-settings/native-verified/projection-preview.png). The failed corrective-trial evidence is retained alongside the passing run; the first trial's uppercase Android-dialog label assertion and a later real touch/remote focus bug were resolved before final verification.

## Next hardware step

The existing tiny temporary geometry plan was refreshed read-only against the current baseline at 18:43 Israel time: [prepared plan](test-results/20261106-settings/reviewed-preview-plan.json). The captured vector remains `167,166,167,167,167,167,167,166`; the proposed vector is `168,166,167,167,167,167,167,166`. It changes one live bottom-left horizontal inset by one unit per thousand (about 1.9 raster pixels), lasts 10 seconds and attempts restoration on-device. It writes no persistent geometry/calibration property. It has not been executed. The authorization boundary and remaining ordinary-app permission/persistence questions are in [PROJECTOR_CONTROLS.md](PROJECTOR_CONTROLS.md). Passing this protocol test alone would not establish complete native settings support.
