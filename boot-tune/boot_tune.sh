#!/bin/sh
# Faster power-on for the HY320 over ADB, without root. Every change is reversible.
# Usage: boot-tune/boot_tune.sh apply|revert <serial>   (e.g. 192.168.1.50:5555)
set -eu
action=${1:?apply or revert}; s=${2:?adb serial}
a() { adb -s "$s" shell "$@"; }

# Factory tests, OTA updaters, vendor store, print, cell broadcast, demos.
# Kept on purpose: com.htc.closedialog (focus/temperature), com.htc.htcsettings (keystone),
# com.softwinner.vis / awsource / awtvinputservice (HDMI), both launchers, GMS, Play Store.
PKGS="com.htc.storeos com.htc.htcotaupdate android.rockchip.update.service com.softwinner.update
com.htc.hyk_test com.hotack.writesn com.mobisystems.office com.softwinner.agingdragonbox
com.softwinner.dragonatt com.softwinner.dragonbox com.softwinner.awlogsettings
com.softwinner.miracastReceiver com.android.egg com.android.calculator2 com.android.music
com.android.musicfx com.android.dreams.basic com.android.dreams.web com.android.printspooler
com.android.bips com.android.printservice.recommendation com.android.cellbroadcastreceiver
com.android.cellbroadcastservice com.android.dynsystem com.android.wallpaperbackup
com.android.wallpapercropper com.android.htmlviewer"
# Streaming apps that otherwise start at boot and sit in RAM; they still open normally.
BG="com.netflix.mediaclient com.disney.disneyplus com.android.chrome com.google.android.youtube.tv"

case $action in
apply)
  for p in $PKGS; do a pm disable-user --user 0 "$p" || true; done
  for p in $BG; do
    a cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore || true
    a am set-standby-bucket "$p" restricted || true
  done
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do a settings put global $k 0; done
  a settings put secure screensaver_enabled 0
  # Stock value 6 = vendor shutdown; 1 = sleep. Wake from sleep is ~2 s vs ~19 s cold boot.
  a settings put global power_button_short_press 1 ;;
revert)
  for p in $PKGS; do a pm enable --user 0 "$p" || true; done
  for p in $BG; do
    a cmd appops set "$p" RUN_ANY_IN_BACKGROUND allow || true
    a am set-standby-bucket "$p" active || true
  done
  a settings put global window_animation_scale 1.0
  a settings put global transition_animation_scale 1.0
  a settings delete global animator_duration_scale
  a settings put secure screensaver_enabled 1
  a settings put global power_button_short_press 6 ;;
*) echo "usage: $0 apply|revert <serial>" >&2; exit 2 ;;
esac
