#!/usr/bin/env python3
"""Capture a reversible launcher UI trial on the explicitly selected HY320.

Requires the already-authorized installation/UI testing of Vibe Home. Does not
install, set HOME, alter hardware values, change favorites, clear logs, or reset
frame counters. The optional --check-home-picker opens and cancels Android's
selection UI and verifies the starting default HOME. The test opens app options,
the Settings shortcuts, and About.
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
STOCK = 'com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity'
FIRMWARE = 'UI_Veng.projector.20241225.164550'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--check-home-picker', action='store_true', help='Open and cancel Android default launcher selection')
    args = parser.parse_args()
    output = args.output or Path(__file__).parent / 'test-results' / (dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-redesign')
    output.mkdir(parents=True, exist_ok=False)
    adb = [shutil.which('adb') or '/opt/homebrew/bin/adb', '-s', args.serial]
    commands = []

    def run(*argv, binary=False):
        result = subprocess.run(adb + list(argv), capture_output=True, timeout=40)
        commands.append({'argv':list(argv), 'returncode':result.returncode, 'stderr':result.stderr.decode(errors='replace')})
        if result.returncode:
            raise RuntimeError(f'ADB failed: {argv}: {result.stderr.decode(errors="replace")}')
        return result.stdout if binary else result.stdout.decode(errors='replace')

    def record(name, *argv):
        data = run(*argv, binary=True)
        (output / name).write_bytes(data)
        return data.decode(errors='replace') if not name.endswith('.png') else data

    def ui(name, package=PACKAGE):
        raw = record(name + '.xml', 'exec-out', 'uiautomator', 'dump', '/dev/tty')
        start = raw.find('<?xml')
        if start < 0:
            raise AssertionError('UI hierarchy unavailable')
        tree = ET.fromstring(raw[start:raw.rfind('</hierarchy>')+12])
        if not any(n.get('package') == package for n in tree.iter('node')):
            raise AssertionError(f'{package} is not the foreground UI')
        return tree

    def screenshot(name):
        record(name + '.png', 'exec-out', 'screencap', '-p')

    def tap(tree, text):
        matches = [n for n in tree.iter('node') if n.get('text') == text]
        if not matches:
            raise AssertionError(f'Missing UI label: {text}')
        bounds = list(map(int, re.findall(r'\d+', matches[0].get('bounds'))))
        run('shell', 'input', 'tap', str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))

    def key(code):
        run('shell','input','keyevent',str(code))

    def properties(name):
        raw = record(name, 'shell','getprop')
        all_props = dict(re.findall(r'^\[([^\]]+)\]: \[(.*)\]$',raw,re.M))
        return {k:v for k,v in all_props.items() if k.startswith(('persist.display.keystone_', 'persist.sys.panel',
                'persist.sys.prj.', 'persist.sys.projection.', 'persist.sys.tpry', 'persist.sys.camok',
                'persist.sys.zoom.', 'persist.vendor.overscan.'))}

    def home(name):
        return record(name,'shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').strip().splitlines()[-1]

    try:
        assert run('shell','getprop','ro.build.display.id').strip() == FIRMWARE
        assert run('shell','getprop','ro.product.model').strip().lower() == 'hy320'
        before = properties('before-properties.txt')
        before_home = home('before-home.txt')
        assert before_home in (STOCK, PACKAGE+'/.HomeEntry')
        disabled = record('before-disabled.txt','shell','pm','list','packages','-d')
        entry=PACKAGE+('/.HomeEntry' if before_home==PACKAGE+'/.HomeEntry' else '/.MainActivity')
        record('launch.txt','shell','am','start','-W','-n',entry)
        key(4)
        key(4)
        tree = ui('home')
        assert not any(n.get('text')=='Home' for n in tree.iter('node')), 'Home button is still visible'
        assert any(n.get('focused')=='true' and n.get('class')=='android.widget.GridView' for n in tree.iter('node')), 'Home grid lacks remote focus'
        assert any(n.get('selected')=='true' and n.get('content-desc') for n in tree.iter('node')), 'Home tile lacks visible selection'
        assert not any(n.get('text') in ('Projector','Wi-Fi','Bluetooth','OK  Open','Favorites','READY TO PLAY') for n in tree.iter('node')), 'Home contains settings or hints'
        screenshot('home')
        record('navigation-before.txt','shell','dumpsys','gfxinfo',PACKAGE)
        for _ in range(8):
            key(22);key(21)
        record('navigation-after.txt','shell','dumpsys','gfxinfo',PACKAGE)
        record('home-memory.txt','shell','dumpsys','meminfo',PACKAGE)
        key(82)
        menu = ui('app-options')
        assert any(n.get('text')=='APP OPTIONS' for n in menu.iter('node'))
        screenshot('app-options')
        key(4)
        tree = ui('home-return')
        tap(tree,'Settings')
        dropdown = ui('settings-dropdown')
        assert {n.get('text') for n in dropdown.iter('node')} >= {'Wi-Fi','Bluetooth','Projector settings','About'}
        assert not {'All apps','Vibe settings'} & {n.get('text') for n in dropdown.iter('node')}
        assert any(n.get('text')=='Wi-Fi' and n.get('focused')=='true' for n in dropdown.iter('node'))
        screenshot('settings-dropdown')
        routes = [('Wi-Fi','com.android.tv.settings'), ('Bluetooth','com.android.tv.settings'),
                  ('Projector settings','com.htc.htcsettings')]
        for index, (label, package) in enumerate(routes):
            for _ in range(index):
                key(20)
            key(23)
            ui(label, package)
            activity = record(label + '-activity.txt','shell','dumpsys','activity','activities')
            assert any(package + '/' in line for line in activity.splitlines() if 'mResumedActivity' in line), label
            screenshot(label)
            key(4)
            tree = ui('return-' + label)
            assert any(n.get('content-desc')=='Settings, collapsed' and n.get('focused')=='true' for n in tree.iter('node'))
            key(23)
            dropdown = ui('reopen-' + label)
        tap(dropdown,'About')
        tree = ui('about')
        labels = {n.get('text') for n in tree.iter('node')}
        assert {'About Vibe Home','Use Vibe Home','Use original launcher'} <= labels
        assert any(label.startswith('Default launcher') for label in labels if label)
        screenshot('about')
        if args.check_home_picker:
            if before_home == STOCK:
                tap(tree,'Use Vibe Home')
            else:
                tap(tree,'Use original launcher')
            picker = ui('android-home-request','com.android.permissioncontroller')
            if before_home != STOCK:
                assert {'HTCLauncherEN','Vibe Home'} <= {n.get('text') for n in picker.iter('node')}
            screenshot('android-home-request')
            key(4)  # Cancel; never select or grant a different default.
            tree = ui('cancelled-home-request')
            assert any(n.get('text')=='About Vibe Home' for n in tree.iter('node'))
            assert home('home-after-cancel.txt') == before_home
            if before_home == STOCK:
                candidates = record('home-candidates-after-cancel.txt','shell','cmd','package','query-activities','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
                assert PACKAGE not in candidates, 'Cancelled HOME alias remains active'
        key(4)
        tree = ui('about-dismissed')
        assert any(n.get('class')=='android.widget.GridView' for n in tree.iter('node'))
        assert not any(n.get('text') in ('All apps','Vibe settings','About Vibe Home') for n in tree.iter('node'))
        key(3)
        record('stock-activity.txt','shell','dumpsys','activity','activities')
        assert home('after-home.txt') == before_home
        record('return-to-vibe.txt','shell','am','start','-W','-n',entry)
        tree = ui('final-home');screenshot('final-home')
        after = properties('after-properties.txt')
        assert before == after, 'Projection properties changed'
        assert record('after-disabled.txt','shell','pm','list','packages','-d') == disabled
        record('final-memory.txt','shell','dumpsys','meminfo',PACKAGE)
        record('final-logcat.txt','shell','logcat','-b','crash','-b','main','-d','-v','threadtime','-t','1000')
        result = {'passed':True,'projection_properties_checked':len(before),'projection_property_changes':{},
                  'default_home':before_home,'hardware_write_performed':False,'favorites_changed':False,
                  'installed_package':PACKAGE,'serial':args.serial, 'home_picker_cancellation_checked':args.check_home_picker}
        (output/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:
        (output/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
        (output/'hashes.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in sorted(output.iterdir()) if p.is_file() and p.name!='hashes.json'},indent=2)+'\n')
    print(output.resolve())


if __name__ == '__main__':
    main()
