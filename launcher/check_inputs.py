#!/usr/bin/env python3
"""Exercise the Inputs dropdown and vendor source apps on the authorized HY320.

Opens HDMI and the USB file browser through ordinary UI. Never changes HOME,
calibration, favorites, system-package state, or USB mounts. No drive is required;
physical HDMI signal and USB hotplug still require attached hardware.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

PACKAGE = 'com.vibehome.launcher'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    output = args.output or Path(__file__).parent / 'test-results' / (dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-inputs')
    output.mkdir(parents=True, exist_ok=False)
    adb = [shutil.which('adb') or '/opt/homebrew/bin/adb', '-s', args.serial]
    commands = []

    def run(*argv):
        result = subprocess.run(adb + list(argv), capture_output=True, timeout=40)
        commands.append({'argv': list(argv), 'returncode': result.returncode, 'stderr': result.stderr.decode(errors='replace')})
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout

    def record(name, *argv):
        data = run(*argv)
        (output / name).write_bytes(data)
        return data.decode(errors='replace')

    def ui(name):
        raw = record(name + '.xml', 'exec-out', 'uiautomator', 'dump', '/dev/tty')
        return ET.fromstring(raw[raw.index('<?xml'):raw.rfind('</hierarchy>') + 12])

    def shot(name):
        record(name + '.png', 'exec-out', 'screencap', '-p')

    def tap(tree, label):
        node = next(n for n in tree.iter('node') if n.get('text') == label)
        b = list(map(int, re.findall(r'\d+', node.get('bounds'))))
        run('shell', 'input', 'tap', str((b[0]+b[2])//2), str((b[1]+b[3])//2))

    def key(code):
        run('shell', 'input', 'keyevent', str(code))

    def texts(tree):
        return {n.get('text') for n in tree.iter('node')}

    def focus(tree, prefix):
        assert any(n.get('focused') == 'true' and n.get('content-desc', '').startswith(prefix) for n in tree.iter('node')), f'Expected focus: {prefix}'

    def props(name):
        raw = record(name, 'shell', 'getprop')
        return {k: v for k, v in re.findall(r'^\[([^\]]+)\]: \[(.*)\]$', raw, re.M) if k.startswith(('persist.display.keystone_', 'persist.sys.panel', 'persist.sys.prj.', 'persist.sys.projection.', 'persist.sys.tpry', 'persist.sys.camok', 'persist.sys.zoom.', 'persist.vendor.overscan.'))}

    def home(name):
        return record(name, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')

    def resumed(name, package):
        raw = record(name, 'shell', 'dumpsys', 'activity', 'activities')
        assert any(package in line for line in raw.splitlines() if 'mResumedActivity' in line), f'{package} did not open'

    def start():
        run('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')

    try:
        assert run('shell', 'getprop', 'ro.build.display.id').decode().strip() == 'UI_Veng.projector.20241225.164550'
        assert b'allwinner,tv303' in run('shell', 'cat', '/proc/device-tree/compatible')
        before = props('before-properties.txt')
        original_home = home('before-home.txt')
        disabled = record('before-disabled.txt', 'shell', 'pm', 'list', 'packages', '-d')
        record('before-inputs.txt', 'shell', 'dumpsys', 'tv_input')
        volumes = record('before-volumes.txt', 'shell', 'sm', 'list-volumes', 'all')
        # Start from an unambiguous page, without changing app preferences.
        start()
        key(4)
        tree = ui('home')
        assert 'Inputs' in texts(tree) and 'Apps' not in texts(tree)
        assert '⌄' not in texts(tree), 'Inputs still has a down arrow'
        tap(tree, 'Inputs')
        tree = ui('dropdown')
        assert {'HDMI', 'USB'} <= texts(tree)
        assert not {'HDMI 1', 'HDMI 2', 'HDMI 3', 'AV', 'Live TV'} & texts(tree)
        focus(tree, 'HDMI,')
        shot('dropdown')
        key(20)
        focus(ui('usb-focused'), 'USB,')
        shot('usb-focused')
        key(19)
        focus(ui('hdmi-focused'), 'HDMI,')
        key(19)  # Up from the first row returns to Inputs.
        tree = ui('up-dismissed')
        focus(tree, 'Inputs, collapsed')
        key(23)
        focus(ui('remote-open'), 'HDMI,')
        key(4)
        focus(ui('back-dismissed'), 'Inputs, collapsed')
        key(20)  # Down from the tab opens its menu too.
        focus(ui('down-open'), 'HDMI,')
        key(22)
        tree = ui('right-dismissed')
        assert any(n.get('text') == 'Edit' and n.get('focused') == 'true' for n in tree.iter('node'))
        key(22)
        assert any(n.get('text') == 'Settings' and n.get('focused') == 'true' for n in ui('settings-focus').iter('node'))
        key(21)
        key(21)
        focus(ui('back-to-inputs'), 'Inputs, collapsed')
        key(23)
        ui('outside-open')
        run('shell', 'input', 'tap', '600', '350')
        assert 'USB' not in texts(ui('outside-dismissed'))
        # Open USB media through the actual dropdown, even without a drive.
        tap(ui('before-usb'), 'Inputs')
        ui('usb-menu')
        key(20)
        key(23)
        ui('usb-browser')
        resumed('usb-activity.txt', 'com.softwinner.TvdFileManager')
        shot('usb-browser')
        key(4)
        tree = ui('return-from-usb')
        assert 'Inputs' in texts(tree), 'Back did not return from USB browser'
        tap(tree, 'Inputs')
        ui('hdmi-menu')
        key(23)
        ui('hdmi-viewer')
        resumed('hdmi-activity.txt', 'com.softwinner.awlivetv')
        shot('hdmi-viewer')
        key(4)
        tree = ui('return-from-hdmi')
        assert 'Inputs' in texts(tree), 'Back did not return from HDMI'
        tap(tree, 'Settings')
        tap(ui('settings-menu'), 'About')
        tree = ui('about')
        assert {'About Vibe Home', 'Use Vibe Home', 'Use original launcher'} <= texts(tree)
        key(4)
        # Rapid open/cancel must leave no popup or callback taking focus.
        tree = ui('return-home')
        tap(tree, 'Inputs')
        ui('rapid-start')
        for _ in range(6):
            key(4)
            key(23)
        focus(ui('rapid-final'), 'HDMI,')
        key(4)
        tree = ui('final-home')
        shot('final-home')
        assert before == props('after-properties.txt'), 'Projection property changed'
        assert original_home == home('after-home.txt'), 'Default HOME changed'
        assert disabled == record('after-disabled.txt', 'shell', 'pm', 'list', 'packages', '-d')
        record('logcat.txt', 'shell', 'logcat', '-b', 'crash', '-b', 'main', '-d', '-v', 'threadtime', '-t', '1000')
        result = {'passed': True, 'serial': args.serial, 'projection_properties_checked': len(before), 'default_home_unchanged': True,
                  'system_package_states_unchanged': True, 'hdmi_viewer_opened': True, 'usb_browser_opened': True,
                  'usb_volumes_at_test': volumes, 'physical_signal_and_usb_hotplug_verified': False}
        (output/'result.json').write_text(json.dumps(result, indent=2)+'\n')
    finally:
        (output/'commands.json').write_text(json.dumps(commands, indent=2)+'\n')
        (output/'hashes.json').write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.iterdir()) if p.is_file() and p.name != 'hashes.json'}, indent=2)+'\n')
    print(output.resolve())


if __name__ == '__main__':
    main()
