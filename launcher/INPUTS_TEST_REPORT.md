# Inputs dropdown — 0.6.0

2026-11-06, Asia/Jerusalem. Installed **0.6.0 (code 12)** on the authorized HY320, firmware `UI_Veng.projector.20241225.164550`, with its unchanged 1920 × 1080 display and density 320.

The top Apps tab is now **Inputs**. It opens a rounded, dark dropdown over the current page, with source icons, a white focused row, and connection status. There is no background dim. All apps remains available through **Settings → All apps** and MENU on navigation. Home tiles, favorites/order, blur and motion are unchanged.

## Source evidence and behavior

The current `/product/etc/config.ini` exposes `sourceList: HDMI2` and `sourceListTitle: HDMI`. Although the generic HAL advertises three HDMI services and CVBS, those extra ports are not added to the dropdown. Read-only inspection of the installed stock launcher/settings and HDMI app confirmed the vendor activity `com.softwinner.awlivetv/.MainActivity` and its `input_source` string extra. Vibe delegates selection through that activity, using the configured source key. No vendor code or artwork was copied.

HDMI connection text uses Android's TV input state for the configured port. At test time it reported **Standby**; opening the vendor viewer showed **HDMI No signal**. This verifies source routing and returning with Back, not HDMI picture/audio from an attached source.

USB opens `com.softwinner.TvdFileManager/.MainUI`, the same media browser used by stock. The dropdown reads mounted removable-volume descriptions through Android StorageManager, without a storage permission. No removable volume was mounted during testing, and the menu correctly showed **No drive connected**. Opening the browser showed its internal-storage device screen. Physical USB insertion/removal, mount-status refresh, drive selection and file playback remain unverified.

Configuration and status discovery run on the existing image worker. Input-state callbacks and a dynamically registered media receiver exist only while the menu is open; dismissal unregisters them and invalidates pending results. No service, boot receiver, polling, shell bridge, projection setter, mount operation or default-HOME change was introduced.

## Validation

- [Inputs check](test-results/20261106-inputs/inputs-final/result.json): touch opening, initial HDMI focus, remote Up/Down selection, OK and Down opening, Back/Up/outside dismissal, Right returning to Settings navigation, six rapid close/reopen cycles, opening both vendor apps and returning using Back, and access to All apps from Settings.
- [Home-row regression](test-results/20261106-inputs/rows/result.json): all **five Home rows**, 11 captured down/up states, the final row fully at the top, 236 px active cards in a 382 px viewport with a 118 px preview, and retained enlarged/raised focus. App options, the full drawer through Settings, and Settings navigation also passed.
- Actual projector [dropdown screenshot](test-results/20261106-inputs/inputs-final/dropdown.png), [USB focus](test-results/20261106-inputs/inputs-final/usb-focused.png), [HDMI viewer](test-results/20261106-inputs/inputs-final/hdmi-viewer.png), and [USB browser](test-results/20261106-inputs/inputs-final/usb-browser.png) were inspected.
- All **13 tracked projection properties**, default HOME and disabled-package inventory match the before-state. Stock remains default. Neither favorites nor projection controls were edited. No home-role request, default grant or reboot was performed.
- [Final verification](test-results/20261106-inputs/verification.json): a single tap on the previously unfocused USB row opens its browser; About matches 0.6.0; the pulled installed APK matches the retained build and every recorded source/resource hash. The HOME alias remains disabled and vendor configuration is byte-identical to the before-state. [Final device screenshot](test-results/20261106-inputs/final-inputs.png).
- Build/signature verification, Python syntax checks and `git diff --check` passed. Bounded input-check logs contain no Vibe fatal exception or ANR entry. This is a functional UI check, not a performance benchmark.

Initial corrective trials found missing touch-mode row focus and a popup at the screen edge. A later trial found that making the tab focusable in touch mode consumed its first tap. These were corrected before the successful Inputs run. Trial APKs and metadata are retained separately in the ignored evidence directory; they are not evidence for the final APK.

```sh
python3 launcher/build.py
adb -s 192.168.10.15:5555 install --no-incremental -r launcher/build/vibe-home.apk
python3 launcher/check_inputs.py --serial 192.168.10.15:5555
python3 launcher/check_home_rows.py --serial 192.168.10.15:5555
```

Final APK: **61,986 bytes**. SHA-256: `7ebefa3e510bfe31a06f9f419f648df335a60e2e43ea69bced49154f263cbc36`. [Build metadata](test-results/20261106-inputs/artifact.json), previous installed APK, trial hashes, proprietary inspection files, logs, XML and screenshots remain ignored under `launcher/test-results/20261106-inputs/`.
