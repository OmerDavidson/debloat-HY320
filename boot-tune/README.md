# Boot tune

Makes the projector turn on faster, over ADB, without root. Every change is reversible.

```sh
boot-tune/boot_tune.sh apply  192.168.1.50:5555
boot-tune/boot_tune.sh revert 192.168.1.50:5555
```

## What it does

| Change | Effect |
|---|---|
| Remote power button sleeps instead of shutting down (`power_button_short_press` 6 → 1) | Lamp and fan turn off; next press wakes in ~1.7 s instead of a ~19 s cold boot |
| Disables 27 packages (factory tests, OTA updaters, `com.htc.storeos`, `com.hotack.writesn`, print, cell broadcast, demos, Miracast) | Fewer apps started at boot |
| Background-restricts Netflix, Disney+, Chrome, YouTube | They no longer load at boot or sit in RAM; they open normally |
| Animations and screensaver off | Snappier menus |

Kept on purpose: `com.htc.closedialog` (focus and temperature services), `com.htc.htcsettings` (keystone), HDMI input (`com.softwinner.vis`, `awsource`, `awtvinputservice`), both launchers, Google services and the Play Store.

## Measured

On one unit: model `HY300Pro`, firmware `UI_Veng.projector.20240815.134535`, Android 11, with Vibe Home as the default Home.

| | Before | After |
|---|---:|---:|
| Remote power-on, until the screen is on | ~19 s cold boot | **~1.7 s** wake from sleep |
| Cold boot (`boot_progress_enable_screen`) | 20.3 s | 18.7 s |
| Apps running right after boot | Netflix, storeos, Play Store and others | None of them |

The projector woke from sleep with the real remote's power button (`WAKE_REASON_POWER_BUTTON`). All settings survived a cold reboot.

## Tradeoffs

- **Standby power:** the projector draws a little power while asleep (not measured).
- **Unplugging still cold boots:** pulling the plug or a power cut means the full ~19 s boot.
- **No phone mirroring:** Miracast stops working.
- **No vendor updates:** the projector no longer checks for vendor OTA updates.
- **Netflix and others:** background-restricted apps can't refresh in the background.

Each sample was taken once, on one device. Other HY320 firmware may use a different `power_button_short_press` value. Check yours first with `adb shell settings get global power_button_short_press`. The revert step restores 6.

## Not done

Root. The known root method flashes a full stock image. There is no image for this build, units differ in board and panel, and a flash wipes all data. The remaining boot time is mostly the ~7 s kernel and init stage plus system_server startup. Root would save an estimated 1–3 s, while sleep saves ~17 s.
