# Quick Wake

Quick Wake makes a short press of the remote's Power button put the projector to sleep. Press Power again to wake it. The switch starts off on a projector using the stock Power off / Restart menu.

## Set up

Quick Wake is available for HY320 firmware `UI_Veng.projector.20241225.164550` and HY300Pro firmware `UI_Veng.projector.20240815.134535` with a recognized Power-button setting. Install Vibe Home 0.14.9 or newer, then connect ADB and run the command below with your projector's serial from `adb devices -l`. See the [debugging guide](../README.md#install-the-launcher) for ADB setup.

```sh
projector=192.168.1.50:5555
adb -s "$projector" shell pm grant com.vibehome.launcher android.permission.WRITE_SECURE_SETTINGS
```

The permission lets Vibe write the device-wide Power-button setting. Granting it leaves the current behavior in place. In Vibe Home, open **Settings → Quick Wake** and turn on **Sleep on Power press**. You can disconnect ADB after setup.

## Restore the power menu

Turn the switch off to restore the Power off / Restart menu. The setting belongs to the projector, so turn it off before uninstalling Vibe or revoking the optional permission. If Vibe is already unavailable, restore the stock value on the supported firmware with ADB.

```sh
adb -s "$projector" shell settings put global power_button_short_press 6
```

Once you've restored the menu, you can revoke the permission if desired.

```sh
adb -s "$projector" shell pm revoke com.vibehome.launcher android.permission.WRITE_SECURE_SETTINGS
```

Shutting down or losing power still leads to a cold boot. The sleep setting comes from [OmerDavidson's HY300Pro trial](https://github.com/oshriagronov/debloat-HY320/pull/1), which reported about 1.7 seconds to wake and about 19 seconds from cold boot on that unit.

On the HY320, Vibe’s switch was verified in both directions with D-pad input. Injected Power key events put Android to sleep and woke it again. Turning the switch off restored the stock power menu.
