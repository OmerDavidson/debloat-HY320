#!/usr/bin/env python3
"""Check the sharp home row and half-height preview through every remote-navigation row on the authorized HY320.

Installs nothing, changes no defaults/favorites/projection values, and never
opens a default-launcher request. Captures UI, screenshots and read-only device
state while navigating Home, Apps, app options and Settings.
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
    output = args.output or Path(__file__).parent / 'test-results' / (dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-home-rows')
    output.mkdir(parents=True, exist_ok=False)
    adb = [shutil.which('adb') or '/opt/homebrew/bin/adb', '-s', args.serial]
    commands, checks = [], []

    def run(*argv):
        r = subprocess.run(adb + list(argv), capture_output=True, timeout=40)
        commands.append({'argv':argv, 'returncode':r.returncode, 'stderr':r.stderr.decode(errors='replace')})
        if r.returncode:
            raise RuntimeError(f'ADB failed: {argv}: {r.stderr.decode(errors="replace")}')
        return r.stdout

    def capture(name, *argv):
        data = run(*argv)
        (output / name).write_bytes(data)
        return data.decode(errors='replace')

    def ui(name):
        raw = capture(name+'.xml', 'exec-out','uiautomator','dump','/dev/tty')
        start = raw.find('<?xml')
        assert start >= 0, 'Missing UI hierarchy'
        tree = ET.fromstring(raw[start:raw.rfind('</hierarchy>')+12])
        assert any(n.get('package') == PACKAGE for n in tree.iter('node')), 'Vibe is not foreground'
        return tree

    def key(code):
        run('shell','input','keyevent',str(code))

    def bounds(node):
        return list(map(int,re.findall(r'\d+',node.get('bounds'))))

    def tap(tree, label):
        node = next(n for n in tree.iter('node') if n.get('text') == label)
        x1,y1,x2,y2 = bounds(node)
        run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

    def check_row(name):
        tree = ui(name)
        grid = next(n for n in tree.iter('node') if n.get('class') == 'android.widget.GridView')
        gx1,gy1,gx2,gy2 = bounds(grid)
        # This diagnostic is scoped to the supplied projector's 1080p / density-320 layout.
        assert gy2-gy1 == 382, ('Home must fit a 236px row, 28px gap and 118px preview',bounds(grid))
        cards = list(grid)
        assert 1 <= len(cards) <= 8, ('Unexpected visible-card count',len(cards))
        selected = [n for n in cards if n.get('selected') == 'true']
        assert len(selected) == 1, 'Exactly one card should be selected'
        assert not any(n.get('text','').startswith('●  ') for n in tree.iter('node')), 'App-name badge must be absent'
        for card in cards:
            x1,y1,x2,y2 = bounds(card)
            assert card.get('content-desc'), 'Visible card has no app label'
            expected = 236 if y1 == gy1 else 118
            assert y1 in (gy1,gy1+264), ('Row is not aligned',card.get('content-desc'),bounds(card))
            assert y2-y1 == expected, ('Incorrect tile/preview height',card.get('content-desc'),bounds(card))
            if card.get('selected') == 'true':
                assert y1 == gy1 and expected == 236, 'Focused tile must be fully visible in the top row'
                face = list(card)[0]
                fx1,fy1,fx2,fy2 = bounds(face)
                assert x1 < fx1 < fx2 < x2 and y1 <= fy1 < fy2 <= y2, 'Raised artwork must remain inside its cell'
                peers = [list(other)[0] for other in cards if other != card and bounds(other)[1] == gy1]
                for peer in peers:
                    px1,py1,px2,py2 = bounds(peer)
                    assert fx2-fx1 > (px2-px1)*1.18, 'Focused artwork must be clearly larger'
                    assert fy1+20 < py1, 'Focused artwork must be visibly raised'
            assert gx1 <= x1 < x2 <= gx2 and gy1 <= y1 < y2 <= gy2, ('Tile outside viewport',card.attrib)
        entry = {'capture':name,'selected':selected[0].get('content-desc'),'visible':[n.get('content-desc') for n in cards], 'focused_row_full_height':True,'next_row_half_height':True}
        checks.append(entry)
        capture(name+'.png','exec-out','screencap','-p')
        return entry['selected']

    def properties(name):
        raw = capture(name,'shell','getprop')
        return {k:v for k,v in re.findall(r'^\[([^\]]+)\]: \[(.*)\]$',raw,re.M) if k.startswith((
            'persist.display.keystone_','persist.sys.panel','persist.sys.prj.','persist.sys.projection.',
            'persist.sys.tpry','persist.sys.camok','persist.sys.zoom.','persist.vendor.overscan.'))}

    def home(name):
        return capture(name,'shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').strip().splitlines()[-1]

    try:
        assert run('shell','getprop','ro.build.display.id').decode().strip() == 'UI_Veng.projector.20241225.164550'
        assert '1920x1080' in run('shell','wm','size').decode()
        assert '320' in run('shell','wm','density').decode()
        before_properties = properties('before-properties.txt')
        before_home = home('before-home.txt')
        disabled = capture('before-disabled.txt','shell','pm','list','packages','-d')
        capture('launch.txt','shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
        tap(ui('initial'),'Home')
        key(122)
        start = selected = check_row('row-0')
        down = [selected]
        for index in range(1,20):
            key(20)
            current = check_row(f'down-{index}')
            if current == selected:
                break
            down.append(current)
            selected = current
        else:
            raise AssertionError('Did not reach the last home row')
        assert len(down) >= 3, 'Too few rows exercised'
        for index in range(len(down)-1):
            key(19)
            selected = check_row(f'up-{index+1}')
        assert selected == start, ('Up did not return to original column/item',selected,start)
        key(82)
        tree = ui('options')
        assert any(n.get('text') == 'APP OPTIONS' for n in tree.iter('node'))
        key(4)
        tap(ui('home-return'),'Apps')
        tree = ui('apps')
        assert any(n.get('text') == 'All apps' for n in tree.iter('node'))
        key(4)
        tap(ui('home-before-settings'),'Settings')
        tree = ui('settings')
        assert all(any(n.get('text') == label for n in tree.iter('node')) for label in ('Projector','Use Vibe Home','Use original launcher'))
        key(4)
        key(122);key(22);key(20)
        check_row('final-second-row')
        assert properties('after-properties.txt') == before_properties, 'Projection properties changed'
        assert home('after-home.txt') == before_home, 'Default HOME changed'
        assert capture('after-disabled.txt','shell','pm','list','packages','-d') == disabled, 'Disabled packages changed'
        capture('memory.txt','shell','dumpsys','meminfo',PACKAGE)
        capture('package.txt','shell','dumpsys','package',PACKAGE)
        capture('logs.txt','shell','logcat','-b','main','-b','crash','-d','-v','threadtime','-t','1000')
        result = {'passed':True,'distinct_rows_checked':len(down),'full_card_height_px':236,'viewport_height_px':382,'preview_visible_height_px':118,
                  'navigation_checks':checks,'default_home_unchanged':before_home,'projection_properties_unchanged':len(before_properties),
                  'disabled_packages_unchanged':True,'favorites_changed':False,'hardware_writes':False}
        (output/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:
        (output/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
        (output/'hashes.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.iterdir()) if p.is_file() and p.name!='hashes.json'},indent=2)+'\n')
    print(output.resolve())


if __name__ == '__main__':
    main()
