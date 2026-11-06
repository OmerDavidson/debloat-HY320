#!/usr/bin/env python3
"""Read-only Android diagnostics. Writes only to a new host-side directory.

No root, installs, device writes, log clearing, service restarts, or reboots.
Usage: python3 collect_diagnostics.py --serial SERIAL [--label idle]
Use --usb to select the sole authorized USB transport (never selects TCP).
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time


# Fixed commands only. User input is never inserted into a remote shell command.
QUERIES = [
    ("identity", "id; uname -a; uptime; date -u; getenforce"),
    ("properties", "getprop"),
    ("cpu", "cat /proc/cpuinfo; cat /sys/devices/system/cpu/present; cat /sys/devices/system/cpu/online"),
    ("board", "for f in /proc/device-tree/model /proc/device-tree/compatible /sys/firmware/devicetree/base/model /sys/firmware/devicetree/base/compatible /sys/devices/soc0/machine /sys/devices/soc0/family /sys/devices/soc0/soc_id /sys/devices/soc0/revision; do if [ -r \"$f\" ]; then echo \"FILE: $f\"; cat \"$f\"; echo; fi; done"),
    ("boot_cmdline", "cat /proc/cmdline"),
    ("partitions", "cat /proc/partitions; ls -l /dev/block/by-name /dev/block/platform/*/by-name; cat /proc/mounts"),
    ("emmc", "for f in /sys/class/block/mmcblk0/size /sys/class/block/mmcblk0/device/name /sys/class/block/mmcblk0/device/manfid /sys/class/block/mmcblk0/device/oemid /sys/class/block/mmcblk0/device/life_time /sys/class/block/mmcblk0/device/pre_eol_info; do if [ -r \"$f\" ]; then echo \"FILE: $f\"; cat \"$f\"; fi; done"),
    ("memory", "cat /proc/meminfo; cat /proc/swaps; cat /proc/pressure/memory; cat /proc/vmstat; cat /sys/block/zram0/mm_stat; cat /sys/block/zram0/disksize"),
    ("meminfo", "dumpsys meminfo"),
    ("storage", "df -k /data /system /vendor /cache /sdcard"),
    ("processes", "ps -A -o USER,PID,PPID,VSZ,RSS,STAT,NAME,ARGS"),
    ("processes_fallback", "ps -A"),
    ("cpu_sample", "top -b -n 2 -d 2 -m 40"),
    ("cpuinfo", "dumpsys cpuinfo"),
    ("activity_processes", "dumpsys activity processes"),
    ("activity_services", "dumpsys activity services"),
    ("packages", "pm list packages -f -U -i --show-versioncode"),
    ("packages_system", "pm list packages -s -f"),
    ("packages_third_party", "pm list packages -3 -f"),
    ("packages_disabled", "pm list packages -d"),
    ("package_details", "dumpsys package"),
    ("home_resolved", "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"),
    ("home_candidates", "cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"),
    ("home_role", "cmd role get-role-holders --user 0 android.app.role.HOME"),
    ("activity", "dumpsys activity activities"),
    ("display", "wm size; wm density; dumpsys display"),
    ("window", "dumpsys window windows"),
    ("settings_observed", "settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale; settings get global always_finish_activities; settings get global background_process_limit; settings get global low_power; settings get global development_settings_enabled; settings get global adb_enabled"),
    ("thermal", "dumpsys thermalservice; for z in /sys/class/thermal/thermal_zone*; do if [ -r \"$z/temp\" ]; then echo \"ZONE: $z\"; cat \"$z/type\" \"$z/temp\"; fi; done"),
    ("cpu_frequency", "for p in /sys/devices/system/cpu/cpufreq/policy*; do echo \"POLICY: $p\"; for n in scaling_driver scaling_governor scaling_cur_freq cpuinfo_min_freq cpuinfo_max_freq scaling_min_freq scaling_max_freq; do if [ -r \"$p/$n\" ]; then echo \"$n\"; cat \"$p/$n\"; fi; done; done"),
    ("drivers", "cat /proc/modules; ls /vendor/lib/hw /vendor/lib64/hw /vendor/firmware /vendor/etc"),
    ("graphics", "dumpsys SurfaceFlinger"),
    ("graphics_frames", "dumpsys gfxinfo"),
    ("media_codecs", "dumpsys media.codec"),
    ("logcat_buffers", "logcat -g"),
    ("logcat_recent", "logcat -b main -b system -b crash -d -v threadtime -t 2000"),
    ("logcat_events", "logcat -b events -d -v threadtime -t 600"),
    ("kernel_log", "dmesg"),
    ("memory_end", "cat /proc/meminfo; cat /proc/pressure/memory; cat /proc/swaps; uptime"),
    ("hardware_files", "ls -la /Reserve0 /oem /vendor/etc/display /vendor/etc/display/mips /sys/class/udc; for f in /Reserve0/panel_config.ini /vendor/etc/panel_config.ini /vendor/etc/display/panel_config.ini /vendor/etc/display/mips/panel_config.ini /sys/firmware/devicetree/base/serial-number; do if [ -r \"$f\" ]; then echo \"FILE: $f\"; cat \"$f\"; echo; fi; done; ls -l /dev/block/by-name/boot_a /dev/block/by-name/vendor_boot_a /sys/firmware/fdt"),
    ("usb_observed", "dumpsys usb; for f in /sys/class/udc/*/state /config/usb_gadget/g1/UDC /sys/class/android_usb/android0/state; do if [ -r \"$f\" ]; then echo \"FILE: $f\"; cat \"$f\"; fi; done"),
    ("settings_named", "for ns in global system secure; do echo \"NAMESPACE: $ns\"; for key in window_animation_scale transition_animation_scale animator_duration_scale; do echo \"KEY: $key\"; settings get \"$ns\" \"$key\"; done; done"),
    ("services_available", "dumpsys -l"),
    ("codec_config", "ls /vendor/etc/media* /system/etc/media*; cat /vendor/etc/media_codecs.xml"),
    ("logcat_extended", "logcat -b main -b system -b crash -d -v threadtime -t 8000"),
    ("root_observed", "command -v su; ls -l /system/bin/su /system/xbin/su /sbin/su /vendor/bin/su; getprop ro.boot.flash.locked; getprop ro.boot.verifiedbootstate; getprop ro.oem_unlock_supported"),
    ("compatibility_hashes", "sha256sum /Reserve0/panel_config.ini /vendor/etc/display/mips/display.bin /vendor/etc/display/mips/display_cfg.xml; ls -l /dev/block/mmcblk0 /dev/block/mmcblk0p5 /dev/block/mmcblk0p7; pm help"),
    ("window_animation_state", "dumpsys window animator; dumpsys window policy"),
    ("casting_meminfo", "dumpsys meminfo com.android.toofifi; dumpsys meminfo com.huawei.connection"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--serial", help="Exact authorized ADB serial from adb devices -l")
    group.add_argument("--usb", action="store_true", help="Require exactly one authorized USB device")
    parser.add_argument("--label", default="baseline")
    parser.add_argument("--only", nargs="+", choices=[name for name, _ in QUERIES],
                        help="Run only these fixed read-only queries (identity/connectivity checks still run)")
    args = parser.parse_args()
    if not shutil.which("adb"):
        parser.error("adb is not installed or not on PATH")
    os.umask(0o077)
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    label = re.sub(r"[^A-Za-z0-9_-]", "_", args.label)[:60]
    out = Path(__file__).resolve().parent / "diagnostics" / f"{stamp}_{label}"
    out.mkdir(parents=True, exist_ok=False)
    manifest = {"started_utc": stamp, "label": args.label,
                "collector_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                "device_commands_read_only": True, "commands": []}

    def run(name, argv, timeout=35):
        start = time.monotonic()
        started = dt.datetime.now(dt.timezone.utc).isoformat()
        stdout_file, stderr_file = out / f"{name}.txt", out / f"{name}.stderr.txt"
        timed_out = False
        with stdout_file.open("wb") as stdout, stderr_file.open("wb") as stderr:
            try:
                process = subprocess.run(argv, stdout=stdout, stderr=stderr, timeout=timeout)
                code = process.returncode
            except subprocess.TimeoutExpired:
                code, timed_out = None, True
            except OSError as error:
                stderr.write(str(error).encode())
                code = -1
        manifest["commands"].append({
            "name": name, "argv": argv, "started_utc": started,
            "seconds": round(time.monotonic() - start, 3),
            "exit_code": code, "timed_out": timed_out,
            "stdout_sha256": hashlib.sha256(stdout_file.read_bytes()).hexdigest(),
            "stderr_sha256": hashlib.sha256(stderr_file.read_bytes()).hexdigest(),
        })
        (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"{name}: {'TIMEOUT' if timed_out else code}", flush=True)
        return stdout_file.read_text(errors="replace"), code

    print(f"Output: {out}", flush=True)
    run("adb_version", ["adb", "version"])
    inventory, _ = run("adb_devices", ["adb", "devices", "-l"])
    lines = [line.split() for line in inventory.splitlines()[1:] if line.strip()]
    authorized = [row for row in lines if len(row) > 1 and row[1] == "device"]
    if args.usb:
        matches = [row for row in authorized if any(item.startswith("usb:") for item in row[2:])]
    else:
        matches = [row for row in authorized if row[0] == args.serial]
    if len(matches) != 1:
        (out / "STATUS.txt").write_text("BLOCKED: requested transport not uniquely connected and authorized. No device diagnostics ran.\n")
        print("No unique authorized target. Connect/authorize it and rerun; see adb_devices.txt.", file=sys.stderr)
        return 2
    serial = matches[0][0]
    manifest["serial"] = serial
    state, code = run("adb_state", ["adb", "-s", serial, "get-state"])
    if code != 0 or state.strip() != "device":
        return 2
    for name, command in QUERIES:
        if args.only and name not in args.only:
            continue
        run(name, ["adb", "-s", serial, "shell", command], timeout=45)
    manifest["finished_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (out / "STATUS.txt").write_text("Collection finished. Check each exit status and stderr; denied/unsupported queries are expected. Compound shell exit codes cover only the last command.\n")
    print(f"Finished: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
