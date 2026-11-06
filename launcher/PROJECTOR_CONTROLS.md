# Native projector controls: verified boundary

The 0.2.0 launcher opens **our own native Projector activity**. It no longer redirects the Projection card to the vendor menu. The old controls remain an explicitly selected fallback.

## Implemented and tested without changing the picture

- Reads the current display geometry, device size setting, firmware and vendor configuration.
- Reads the real panel mode from the H713 TV-server service. Both the ADB shell probe and the ordinary launcher process returned **0 (front/table)** on this device.
- Projection-mode preview, a size slider relative to the current picture, and eight four-corner offset controls.
- Draws the current and draft picture outlines. Size adjustment scales around the picture center while retaining the existing trapezoid, instead of assuming rectangular calibration.
- Validates offset bounds and a non-crossing polygon; rejects enlargement that would leave the panel.
- Reviews and saves a draft in the launcher's private preferences. This is **not a hardware Apply button**. UI navigation, moving sliders, and saving a draft cannot call a projector setter.
- A read-only provider lets an already authorized ADB shell retrieve the draft. It accepts only this app's UID or shell UID 2000, rejects writes, and exposes no general-purpose shell or system-setting interface.

The test moved the size **preview** to 99%, reviewed and saved it, visited the keystone and mode tabs, and returned home. The projected picture and 23 projection-related properties remained unchanged. The saved 99% draft is only a demonstration; it was not applied.

## What the actual firmware uses

This is specific to `HY320`, hardware `sun50iw12p1`, firmware `UI_Veng.projector.20241225.164550`. It is not a portable Android projector API.

Read-only inspection of the installed vendor settings APK identified:

| Control | Actual path used by this unit's menu |
|---|---|
| Projection mode | HIDL `vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer`, getter transaction 128 and setter 127, MIRROR configuration index 3; vendor also writes `persist.sys.panelvalue`. |
| Screen zoom | Custom SurfaceFlinger transaction **1050**, eight floats; vendor also maintains `persist.sys.zoom.value` and `persist.vendor.overscan.main`. Camera-dependent branches use additional calibration math. |
| Manual corners | The active menu opens **CorrectionIndexActivity → CorrectionActivity**, not the similarly named KeystonecorrectionActivity. It reads normalized corner offsets, uses the same custom display path and has additional calibration/automatic-correction behavior. |

This distinction matters: `KeystonecorrectionActivity` contains an I2C/sysfs path for another platform. It would be wrong to copy that path into our H713 controls merely because the class name looks relevant.

The eight display offsets are ordered bottom-left, top-left, top-right, bottom-right, with x/y for each corner. The current persisted live-transform vector is `167,166,167,167,167,167,167,166`. Separate underlying correction values are asymmetric; replacing them with a generic rectangle would risk losing the user's correction. Our editor keeps the captured baseline immutable.

The vendor size indicator is 80%, while these transform offsets describe roughly two-thirds of the raster width. Those are different conventions. Our size preview deliberately starts at **100% of the current picture**, with clear labels, rather than silently treating the vendor number as a physical width measurement.

`/product/etc/config.ini` is the active vendor configuration: no OEM/SD-card override was present. It enables automatic keystone support but not camera checking or automatic four-corner support. `persist.sys.tpryauto=0`; `persist.sys.tpryxcrt=1` is still present even though that feature is not exposed by the configuration. Neither flag was changed. The probe records the configuration hash and the proposed preview rechecks this state instead of assuming the flags alone describe the hardware behavior.

The vendor's `projectValue` configuration also lists a mode ordering. Only mode 0 has been verified against the running panel; nonzero orientation labels/mapping require physical confirmation before a real mode setter is exposed.

Local evidence is under `../research/vendor-settings/` and `../research/reference-review/`. JADX 1.5.6 was downloaded from its official release and checked against the release SHA-256. Its overall APK decompile reported 15 errors; the identified getter, setter and geometry methods were inspected alongside raw DEX output. This is not a claim of a complete decompilation. No vendor code or assets are bundled into the new app.

## Remaining test: live control, approval required

Reading the HIDL mode works from our ordinary app. **Writing geometry or mode has not been validated.** A successful getter does not establish write permission, correct persistence, or safe synchronization with automatic correction. The app does not try to bypass Android restrictions.

`projector_control.py` defaults to read-only operations:

```sh
python3 launcher/projector_control.py status
python3 launcher/projector_control.py draft
python3 launcher/projector_control.py plan-preview --plan research/reference-review/preview-plan.json
```

The prepared small test plan changes the bottom-left horizontal live offset from **167 to 168 per thousand**: about **1.9 pixels** on the 1920-pixel raster. It lasts **10 seconds** and restores the captured vector. It writes no persistent projection property, panel configuration or calibration data. It does not test or change mode, fan, thermal control, HOME, packages or firmware.

The mutating branch requires the exact token of a separately approved plan. It rechecks the model, firmware, baseline geometry, automatic-keystone state and vendor configuration. A finite on-device shell process runs the preview and restore, detached from the ADB transport so a connection loss alone will not cancel the timer. There is also a best-effort shutdown restore hook. This is not a permanent daemon or root service.

That branch is prepared but **has not been run**. No `preview --approval ...` command was executed. The helper's IPC return/restore markers would need checking, plus confirmation of the physical picture; a successful IPC alone is not physical verification. Shell-process access also does not prove that the same setter is available to an ordinary app.

After this protocol test, the next decision is whether the validated setter works under the ordinary app UID or needs an explicitly authorized, narrowly scoped companion. Permanent Apply/Undo, mode mapping, and synchronization of vendor persistence need that evidence. No rooting or system-APK replacement is recommended to force this integration.
