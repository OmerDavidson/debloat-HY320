# Optional native settings access — review only

**Historical document:** version 0.14.4 removes these permission declarations, with user approval. The current launcher opens Android/vendor settings and has no native-settings access screen. Do not use the historical grant commands below with the current app.

The 0.7.1 APK declares `WRITE_SECURE_SETTINGS` and `CHANGE_CONFIGURATION` and contains native handlers for automatic date/time, selection among enabled keyboards and device language. It does **not** grant these permissions. The read-only check on this HY320 reports both as denied and `signature | privileged | development`; locale adapter lookup succeeds. Actual writes remain untested.

These are broader capabilities than ordinary runtime permissions. Granting them is a separate, explicit device-access decision. Do not run the commands merely because this file exists. Before any approved grant, reidentify the HY320 / sun50iw12p1 / firmware `UI_Veng.projector.20241225.164550`, verify the installed candidate hash, record existing permission state and retain the setting values needed for restore. Do not change HOME, network, calibration or package states as part of granting access.

## Exact proposed grants

Run **only after the user explicitly approves both grants**:

```sh
adb -s 192.168.10.15:5555 shell pm grant com.vibehome.launcher android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.10.15:5555 shell pm grant com.vibehome.launcher android.permission.CHANGE_CONFIGURATION
```

Afterwards, verify access through **Vibe Settings → System → Control availability**. Do not infer a successful operation from a grant: separately review and test each native change and restore, including the physical/read-back result. Granting access itself changes no time, language or keyboard value.

## Revoke only access added by this trial

If those permissions were absent before the approved trial, they can be revoked with:

```sh
adb -s 192.168.10.15:5555 shell pm revoke com.vibehome.launcher android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.10.15:5555 shell pm revoke com.vibehome.launcher android.permission.CHANGE_CONFIGURATION
```

Revocation does not undo settings already changed. Restore reviewed values before revocation where appropriate; do not revoke permissions that existed before a trial without approval. This document does not authorize grants, revokes or setting changes.

These grants do not provide `SET_TIME`, `SET_TIME_ZONE`, `NETWORK_SETTINGS`, `TETHER_PRIVILEGED`, `BLUETOOTH_PRIVILEGED`, `MASTER_CLEAR`, `RECOVERY`, root, system UID or arbitrary vendor-property write access. They do not complete native projector, hotspot, Bluetooth administration, update or reset parity.
