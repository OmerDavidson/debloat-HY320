# Settings parity: what works, what is blocked, and viable solutions

2026-11-06. Target: the verified HY320 / H713, Android 11, firmware `UI_Veng.projector.20241225.164550`. This report concerns **native Vibe UI**, excluding the legacy app list. It does not count vendor-screen shortcuts as native parity.

**Full parity has not been reached. A normally installed Vibe APK cannot currently replace every privileged control.** There are also unimplemented integrations and unvalidated projector writes; these are different from proven permission denials.

## Direct evidence on this unit

- Installed vendor settings: UID **1000**, shared user `android.uid.system`, with granted `NETWORK_SETTINGS`, `RECOVERY`, `MASTER_CLEAR`, `SET_TIME`, `SET_TIME_ZONE`, `WRITE_SECURE_SETTINGS`, `CHANGE_CONFIGURATION` and other system capabilities.
- Vibe: ordinary UID **10074**, signed differently, target API 30. Normal audio/Wi-Fi/Bluetooth permissions are granted. Location was already granted at the start of this task and was preserved.
- The installed ADB shell package has granted `NETWORK_SETTINGS`, `TETHER_PRIVILEGED`, `SET_TIME`, `SET_TIME_ZONE`, `WRITE_SECURE_SETTINGS` and `CHANGE_CONFIGURATION`. Its recorded granted-permission set does **not** contain `BLUETOOTH_PRIVILEGED`, `MASTER_CLEAR` or `RECOVERY`. Shell access is therefore a possible partial backend, not an all-powerful substitute for the vendor process.
- Vibe's native panel and picture getters work. There is still no validated ordinary-app geometry, panel-mode, picture-setting or persistence write path. This task did not run a setter merely to discover whether it fails.
- The only inspected exported vendor provider, `BusinessProvider`, is an authentication/items database, not a supported projector-settings backend. No provider mutations or implicit privileged broadcasts were used.

Full local evidence, including permission dumps and the app-process access check, is ignored under [test-results/20261106-parity/](test-results/20261106-parity/). Vendor APKs are not bundled or published.

## Per-control implementation and blockers

| Control | Current implementation / why parity is incomplete | Possible solution |
|---|---|---|
| Volume | Native media-volume control. Already implemented. | Test actual adjustment separately; no privileged backend is needed. |
| Wi-Fi discovery / personal networks | Native scanning, explicit open/WPA2/WPA3/Enhanced Open selection, hardware-capability checks and suggestions. A suggestion is not an immediate guaranteed connection. | Ordinary app APIs support this subset. WPA3/OWE depend on this radio/driver, not just Android version. Enterprise/WEP/certificate provisioning is not implemented; it needs separate provisioning UI and validation rather than a generic password field. |
| Vibe Wi-Fi suggestions | Native list/removal of suggestions owned by Vibe, with confirmation before removal. | Implemented using public API 30. Existing networks saved by vendor/Android are outside this API's scope. |
| Wi-Fi switch, saved vendor networks, forced connect/disconnect | Target-30 ordinary-app Wi-Fi restrictions; `CHANGE_WIFI_STATE` alone does not restore legacy network administration. | Authorized shell-backed network operations or OEM/system integration, tested on this firmware. A device-owner route has deployment/provisioning constraints and is not assumed available on this already configured projector. Lowering target SDK is not the chosen fix. |
| Internet-sharing hotspot | Ordinary local-only hotspot API does not match the vendor's internet-sharing hotspot. Tethering needs privileged access. | Shell has `TETHER_PRIVILEGED` on this unit; investigate a narrowly scoped authorized backend and actual tethering support. OEM integration is another route. Neither was installed or tested. |
| Bluetooth power / discovery / pairing | Native controls already implemented with ordinary Android 11 permissions. Actual pairing/switching is not exercised by non-mutating tests. | User-driven functional tests with an accessory. |
| Bluetooth reconnect / disconnect / forget | Legacy uses hidden/system profile and adapter APIs; ordinary public APIs do not provide the same full administration. Android 11's system `removeBond` contract specifies `BLUETOOTH_PRIVILEGED`. Normal `BLUETOOTH_ADMIN` is not evidence of full access. | OEM privileged integration; profile-specific firmware API investigation may provide a subset. The shell's current grants do not include Bluetooth privileged access, so a shell companion is not promised to solve all of this. |
| System language | Native locale chooser and adapter are implemented behind `CHANGE_CONFIGURATION`. No grant is made automatically. Adapter lookup and permission status are reported from the actual app process. | A separately approved development grant may enable this adapter; hidden-API checks and an actual language-change/restore trial must still pass. Otherwise use a scoped shell/OEM backend. |
| Keyboard selection | Native selection among enabled keyboards is implemented behind `WRITE_SECURE_SETTINGS`; no keyboard is silently enabled. | A separately approved development grant, followed by a selection/restore test. Enabling/configuring additional keyboards remains separate integration work; arbitrary keyboard-specific preferences cannot be generically reproduced. |
| Automatic date/time | Native selector is implemented behind `WRITE_SECURE_SETTINGS`, with denied access handled before any write. | A separately approved development grant and a functional test. |
| Date and time formats | Native selectors use the ordinary `WRITE_SETTINGS` special-access consent. | User grants Modify system settings in Android's permission UI, then functional testing. This consent does not grant clock, hotspot, recovery or projector-property access. |
| Manual clock / time zone | Requires `SET_TIME` / `SET_TIME_ZONE`; these are not normal runtime permissions. | This unit's shell has both permissions, so a scoped authorized backend is a plausible route. Development grants for secure settings are not equivalent to these permissions. |
| Zoom / four-corner correction | Native draft editor only. Known firmware protocol is SurfaceFlinger transaction 1050; persistent geometry and overscan must remain synchronized without losing the existing trapezoid. | Approve and physically verify the prepared transient test, then test ordinary-app access or a scoped backend, restoration, persistence and calibration interaction. A successful read or shell transaction alone is insufficient. |
| Projection orientation | Getter works; nonzero physical orientation mapping and write/persistence behavior are unvalidated. | Separate physical mode-mapping and restore trials after approval, then native Apply/Undo. No reboot was performed. |
| Brightness / contrast / saturation / hue / sharpness / preset | Five ordinary-app numeric getters work. Vendor uses H713 TV-server getters/setters and preset methods. Setter behavior, read-back and physical result have not been validated. | Controlled one-step adjustment/restore tests for the exact service and ordinary app UID. It may be possible without root; it has not been proven either way. Do not classify this as a proven permission denial. |
| Auto keystone / initial-angle calibration | Vendor properties and sensor/calibration routines interact. These are not equivalent to changing the manual corner draft. | Reconstruct the active H713 path, validate ownership/persistence, and obtain authorization for calibration-affecting tests. Requires exact-device evidence; do not copy unrelated I2C/sysfs paths. |
| Screen saver | Current status/delay are native. Vendor writes both system timeout and protected `persist.sys.screendream.*` properties. Changing only Android timeout would create mismatched behavior. | Validate a synchronized firmware-aware backend. `WRITE_SETTINGS` alone does not grant property writes. |
| Developer options / USB mode | Status is native. The original developer page controls debugging and the vendor USB-mode property. | Some global toggles may use a development grant, but USB mode needs firmware property access and careful connection/recovery planning. No global toggle/property was changed. |
| Factory reset | No native executable reset. Requires protected reset/recovery capabilities and can erase app data and affect projection state. | OEM/recovery integration with an explicit destructive confirmation. The shell's current grants are not enough to claim support. A reset is not a suitable routine parity test. |
| Local / online firmware update | No native updater. Legacy delegates to installed updater packages/broadcasts; those are not a documented safe backend. Native implementation needs signed-package compatibility checks, update protocol, permissions and recovery behavior. | OEM-documented updater integration and an exact compatible firmware/recovery path. An ordinary APK or a secure-settings grant does not supply these. |
| Device information | Native model/OS/firmware/kernel/hardware/storage/memory snapshot. | Implemented; snapshots do not imply a performance measurement. |
| Advanced Android settings | Legacy opens the entire Android TV Settings app. This is broader than the eight primary vendor sections and is not fully reimplemented. Some gaps are implementation scope, not just permissions. | Per-category native work plus the applicable user/system/backend access. See inventory below; a blanket “root required” claim would be inaccurate. |

## What “Advanced settings” includes here

The installed `com.android.tv.settings` APK was pulled read-only and its `main_prefs.xml` / `device.xml` inspected locally. Resources include networking, Bluetooth/remotes, account sign-in, apps (excluded by the user's scope), device information, date/time, language, keyboard, display/sound, inputs, storage, launcher-specific settings, Google/Assistant settings, Cast settings, screen saver/energy saver, developer options, location, usage/diagnostics, accessibility and reboot.

This is a resource inventory, not proof that every entry is visible or usable in this firmware. Some entries are marked hidden or depend on another installed provider. Vibe already covers parts of networking, sound, inputs, storage status, device information and default-home selection. Accessibility administration, accounts/Google/Cast provider-specific preferences, storage maintenance, energy behavior and reboot are not fully implemented. A native frontend cannot universally edit another app's private account/service preferences. Manufacturer/provider contracts or explicit authorization/system access are needed for those parts. Reading status and showing app-owned preferences often requires no privilege; those should be implemented individually rather than classified wholesale as blocked.

## Actual app-process result in 0.7.1

The native access diagnostic confirms a different Vibe/vendor signing certificate, UID 10074 versus 1000, and denied access for all nine inspected protected permissions. `WRITE_SECURE_SETTINGS` and `CHANGE_CONFIGURATION` include the development flag on this firmware. The locale adapter lookup succeeds, but it has not executed an update. Modify system settings consent is not allowed. The radio reports **WPA3 personal and Enhanced Open support**; actual association is untested.

## Practical solution choices

1. **Keep the ordinary APK and add two narrowly approved development grants.** This needs no new always-running service. It can enable the implemented native automatic-time, enabled-keyboard and locale paths, subject to actual firmware adapter/write verification. It does not unlock hotspot, recovery, Bluetooth privileged management or protected vendor properties. [Optional grant/revoke commands](OPTIONAL_SETTINGS_ACCESS.md) are documented for review; no grant was executed.
2. **A scoped shell-backed companion/session.** Keep all UI in Vibe and expose a small allowlist of typed operations over authenticated IPC, not arbitrary shell strings, an exported command activity, or an open network port. This unit's shell permission snapshot makes Wi-Fi, tethering, clock/time-zone and some configuration operations plausible. Each API still needs validation. ADB startup, restart/reboot behavior, cleanup and memory cost must be measured; no automatic boot persistence is assumed. This is a new architecture and needs explicit authorization before installation/activation.
3. **OEM/platform-signed, appropriately allowlisted integration.** The strongest route toward complete native parity. The manufacturer could supply a small settings service or sign an approved companion with the right permissions and vendor-policy access. Signature-only permissions require the appropriate signing identity; simply copying an APK into a privileged directory or adding a manifest permission is not enough. All calibration/update behavior still needs device validation.
4. **Root/custom firmware.** Potentially offers wider control, but no compatible rooting/flashing procedure has been verified for this exact board/flash/panel. It would change the device security/recovery assumptions and is not an automatic or currently validated solution. It was not attempted.
5. **Reuse vendor control screens.** This reaches many controls with low integration risk and no old Home launcher, but it fails the user's requirement that every control be native. It is not used as a claimed solution in this build.

## Primary references

- [Android Wi-Fi administration restrictions and APIs](https://developer.android.com/reference/android/net/wifi/WifiManager).
- [Wi-Fi suggestions and user approval](https://developer.android.com/develop/connectivity/wifi/wifi-suggest).
- [Android 11 permission definitions](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/res/AndroidManifest.xml).
- [Android 11 Bluetooth device administration contract](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/java/android/bluetooth/BluetoothDevice.java).
- [Privileged permission allowlisting](https://source.android.com/docs/core/permissions/perms-allowlist).

## Validation and limits

Version 0.7.1/code 14 is built and signed with the existing development key. Native navigation checks, access diagnostics, Wi-Fi form rejection tests and installed/source hash verification are recorded under `test-results/20261106-parity/`. The [native regression](test-results/20261106-parity/native-check/result.json), [access/form checks](test-results/20261106-parity/access-check/result.json), and dependency-free `WifiSecurityTest` passed. The form check exercised WPA3 selection and rejected an empty password before submitting anything; it read the Vibe-owned suggestion list without adding/removing a network. It verified denied language/keyboard/automatic-time gates and captured the actual app-process permission report.

[Final installed/source verification](test-results/20261106-parity/final-verification.json): **82,466 bytes**, SHA-256 `7bd8f8eb6aa13829602713b36bd9f7dbd2bd88d08b9b02e47ee107ac6305e4cf`, matching installed APK, manifest version and current source hashes. HOME, disabled packages, tracked projection/screen-saver/USB properties and selected clock, volume, network and input settings were unchanged. The pre-existing location grant was preserved, the two development permissions remained denied, and the current-process bounded log sample had no Vibe fatal-exception/ANR marker.

Reproduce:

```sh
python3 launcher/build.py
python3 launcher/check_native_settings.py --serial 192.168.10.15:5555
python3 launcher/check_settings_access.py --serial 192.168.10.15:5555
javac -d launcher/test-classes launcher/src/com/vibehome/launcher/WifiSecurity.java launcher/tests/WifiSecurityTest.java
java -cp launcher/test-classes com.vibehome.launcher.WifiSecurityTest
```

These UI tests expect the optional development permissions to be denied; the access test stops if they were granted unexpectedly. It must be revised deliberately for any later approved grant/write trial. The native regression skips the scan button if location is already granted, so the check does not start discovery. Successful non-mutating UI tests do not establish that a permission-gated write, Bluetooth operation, actual Wi-Fi connection or projection adjustment works. No privileged backend, firmware update, reset, grant, calibration change or default-HOME change was performed.
