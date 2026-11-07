#!/usr/bin/env python3
"""Verify native Settings navigation without changing settings or granting permissions.

Requires an already installed Vibe build on the explicitly selected HY320.
Never scans, pairs, submits a network, moves volume, resets, updates or sets HOME.
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
SECTIONS = ('Picture', 'Connections', 'Sound', 'Language & input', 'Date & time', 'System')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    out = args.output or Path(__file__).parent / 'test-results' / (dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-native-settings')
    out.mkdir(parents=True, exist_ok=False)
    adb = [shutil.which('adb') or '/opt/homebrew/bin/adb', '-s', args.serial]
    commands = []

    def run(*argv):
        result = subprocess.run(adb + list(argv), capture_output=True, timeout=40)
        commands.append({'argv': argv, 'returncode': result.returncode, 'stderr': result.stderr.decode(errors='replace')})
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout

    def save(name, *argv):
        raw = run(*argv)
        (out/name).write_bytes(raw)
        return raw.decode(errors='replace')

    def ui(name):
        raw = save(name+'.xml', 'exec-out', 'uiautomator', 'dump', '/dev/tty')
        tree = ET.fromstring(raw[raw.index('<?xml'):raw.rindex('</hierarchy>')+12])
        assert any(n.get('package') == PACKAGE for n in tree.iter('node')), 'Vibe is not foreground'
        assert not any(n.get('package') == 'com.htc.htcsettings' for n in tree.iter('node')), 'Native Settings opened vendor UI'
        return tree

    def shot(name):
        (out/(name+'.png')).write_bytes(run('exec-out', 'screencap', '-p'))

    def texts(tree):
        return [n.get('text') for n in tree.iter('node') if n.get('text')]

    def bounds(node):
        return list(map(int, re.findall(r'\d+', node.get('bounds'))))

    def tap(tree, label, sidebar=False):
        nodes = [n for n in tree.iter('node') if n.get('text') == label and (not sidebar or bounds(n)[0] < 450)]
        assert nodes, ('Label missing', label, texts(tree))
        x1,y1,x2,y2 = bounds(nodes[0])
        run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

    def key(code):
        run('shell','input','keyevent',str(code))

    def find(label, name):
        for attempt in range(12):
            tree = ui(f'{name}-{attempt}')
            node = next((n for n in tree.iter('node') if n.get('text') == label), None)
            if node is not None and bounds(node)[3]-bounds(node)[1] >= 20:
                return tree
            run('shell','input','swipe','1600','730','1600','430','250')
        raise AssertionError('Cannot reach '+label)

    def state(prefix):
        props = save(prefix+'-properties.txt','shell','getprop')
        projection = {k:v for k,v in re.findall(r'^\[([^\]]+)\]: \[(.*)\]$', props, re.M)
                      if k.startswith(('persist.display.keystone_', 'persist.sys.panel', 'persist.sys.prj.',
                                       'persist.sys.projection.', 'persist.sys.tpry', 'persist.sys.camok',
                                       'persist.sys.zoom.', 'persist.vendor.overscan.', 'persist.sys.screendream.', 'persist.sys.usb0device'))}
        result = {'properties':projection,
                  'home':save(prefix+'-home.txt','shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME'),
                  'disabled':save(prefix+'-disabled.txt','shell','pm','list','packages','-d')}
        # Ignore volatile platform bookkeeping, compare only settings touched by covered controls.
        keys = {'system':('volume_music','time_12_24','date_format','screen_off_timeout'),
                'global':('auto_time','auto_time_zone','adb_enabled','wifi_on','bluetooth_on'),
                'secure':('default_input_method','location_mode')}
        for namespace, selected in keys.items():
            raw = save(prefix+'-'+namespace+'.txt','shell','settings','list',namespace)
            values = dict(line.split('=',1) for line in raw.splitlines() if '=' in line)
            result[namespace] = {k:values.get(k) for k in selected}
        return result

    try:
        assert b'versionName=0.7.' in run('shell','dumpsys','package','com.vibehome.launcher'), 'Historical native-only check; use check_hybrid_settings.py for 0.8+'
        assert run('shell','getprop','ro.product.model').strip() == b'HY320'
        assert run('shell','getprop','ro.hardware').strip() == b'sun50iw12p1'
        assert run('shell','getprop','ro.build.display.id').strip() == b'UI_Veng.projector.20241225.164550'
        assert '1920x1080' in run('shell','wm','size').decode()
        assert '320' in run('shell','wm','density').decode()
        before = state('before')
        save('launch.txt','shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
        tree = ui('initial')
        tap(tree,'Settings')
        tree = ui('settings')
        for label in SECTIONS + ('Use Vibe Home', 'Use original launcher', 'All apps'):
            assert label in texts(tree), ('Settings entry clipped or missing',label)
        shot('settings')
        tap(tree,'Projection preview')
        tree = ui('projection-preview')
        assert any('Preview only' in text for text in texts(tree))
        assert 'Original controls ↗' not in texts(tree)
        shot('projection-preview')
        key(4)
        tree = ui('returned-to-settings')
        for section in SECTIONS:
            tap(tree,section,sidebar=True)
            tree=ui(section.replace(' ','-').replace('&','and').lower())
            shot(section.replace(' ','-').replace('&','and').lower())
            if section=='Picture':
                tree=find('Brightness','picture-values')
                shot('picture-values')
                assert any('live value' in text or 'Live value unavailable' in text for text in texts(tree))
            elif section=='Sound':
                assert any(n.get('class')=='android.widget.SeekBar' for n in tree.iter('node'))
                assert any(text.startswith('Media volume') for text in texts(tree))
            elif section=='Connections':
                assert 'Find Wi-Fi networks' in texts(tree)
                # Confirm permission explanations cancel safely; no permission is granted.
                permissions=run('shell','dumpsys','package',PACKAGE).decode(errors='replace')
                if 'android.permission.ACCESS_FINE_LOCATION: granted=true' not in permissions:
                    tap(tree,'Find Wi-Fi networks')
                    request=ui('scan-explanation')
                    assert 'Allow nearby-device discovery?' in texts(request)
                    key(4)
                tree=ui('connections-after-cancel')
                tap(tree,'Add Wi-Fi network')
                network=ui('add-network')
                assert any(text.lower() == 'request connection' for text in texts(network))
                key(4)
                tree=ui('connections-after-network-cancel')
                assert 'Bluetooth' in texts(find('Bluetooth','connections-scroll'))
                tree=ui('connections-scrolled')
            elif section=='Language & input':
                assert 'Device language' in texts(tree) and 'Choose keyboard' in texts(tree)
            elif section=='Date & time':
                tree=find('Time format','time-format-scroll')
                tap(tree,'Time format')
                formats=ui('time-format-dialog')
                assert any(text in ('Settings access needed','Time format') for text in texts(formats))
                key(4)
                tree=ui('date-after-format-cancel')
            elif section=='System':
                tree=find('Factory reset','system-scroll')
                tap(tree,'Factory reset')
                explanation=ui('reset-unavailable')
                assert any('no recovery permission' in text for text in texts(explanation))
                assert 'Reset' not in texts(explanation), 'An executable reset was exposed'
                key(4)
                tree=ui('system-after-reset-cancel')
        # Exercise D-pad traversal of the categories and entry into the control pane.
        tap(tree,'Picture',sidebar=True)
        key(20);key(23)
        tree=ui('remote-connections')
        assert 'Wi-Fi' in texts(tree)
        key(22)
        tree=ui('remote-control-focus')
        assert any(n.get('focused')=='true' and bounds(n)[0]>=450 for n in tree.iter('node')), 'Right did not focus controls'
        key(4)
        tree=ui('back-home')
        assert any(n.get('class')=='android.widget.GridView' for n in tree.iter('node'))
        key(3)
        ui('home-button')
        after=state('after')
        assert before==after, ('Device state changed',before,after)
        save('package.txt','shell','dumpsys','package',PACKAGE)
        save('memory.txt','shell','dumpsys','meminfo',PACKAGE)
        logs=save('logs.txt','shell','logcat','-b','main','-b','crash','-d','-v','threadtime','-t','1500')
        version=run('shell','dumpsys','package',PACKAGE).decode()
        result={'passed':True,'sections':SECTIONS,'device_state_unchanged':True,'projection_properties_checked':len(before['properties']),
                'default_home':before['home'].strip().splitlines()[-1], 'permissions_granted':False,'settings_changed':False,
                'volume_or_pairing_or_connection_tested':False,'full_legacy_parity':False}
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:
        (out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print(out.resolve())


if __name__ == '__main__':
    main()
