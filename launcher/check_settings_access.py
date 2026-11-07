#!/usr/bin/env python3
"""Check native access reporting, permission gates and Wi-Fi validation without writes.

Does not grant access, submit/remove a network, scan, pair, change time/language,
apply projection, reset, update or select HOME. Requires the authorized HY320.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    out=args.output or Path(__file__).parent/'test-results'/(dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-access')
    out.mkdir(parents=True,exist_ok=False)
    adb=[shutil.which('adb') or '/opt/homebrew/bin/adb','-s',args.serial]
    commands=[]
    def run(*argv):
        r=subprocess.run(adb+list(argv),capture_output=True,timeout=40)
        commands.append({'argv':argv,'returncode':r.returncode,'stderr':r.stderr.decode(errors='replace')})
        if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout
    def ui(name):
        raw=run('exec-out','uiautomator','dump','/dev/tty').decode();(out/(name+'.xml')).write_text(raw)
        root=ET.fromstring(raw[raw.index('<?xml'):raw.rindex('</hierarchy>')+12])
        assert any(n.get('package')=='com.vibehome.launcher' for n in root.iter('node'))
        assert not any(n.get('package')=='com.htc.htcsettings' for n in root.iter('node'))
        return root
    def texts(root):return [n.get('text') for n in root.iter('node') if n.get('text')]
    def tap_node(n):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
        run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    def tap(root,label):
        tap_node(next(n for n in root.iter('node') if n.get('text')==label))
    def key(code):run('shell','input','keyevent',str(code))
    def find(label,name):
        for i in range(30):
            tree=ui(name+'-'+str(i))
            node=next((n for n in tree.iter('node') if n.get('text')==label),None)
            if node is not None:
                bounds=list(map(int,re.findall(r'\d+',node.get('bounds'))))
                if bounds[3]-bounds[1]>=24:return tree
            run('shell','input','swipe','1600','760','1600','400','220')
        raise AssertionError('Cannot reach '+label)
    try:
        assert b'versionName=0.7.' in run('shell','dumpsys','package','com.vibehome.launcher'), 'Historical native-only check; use check_hybrid_settings.py for 0.8+'
        for prop,want in [('ro.product.model',b'HY320'),('ro.hardware',b'sun50iw12p1'),('ro.build.display.id',b'UI_Veng.projector.20241225.164550')]:assert run('shell','getprop',prop).strip()==want
        run('shell','am','start','-W','-n','com.vibehome.launcher/.MainActivity')
        tree=ui('home');tap(tree,'Settings');tree=ui('settings');tap(tree,'System')
        tree=ui('system');tap(tree,'Control availability');tree=ui('access')
        report=next(text for text in texts(tree) if 'Vibe UID:' in text)
        (out/'access.txt').write_text(report+'\n')
        assert 'Vendor settings UID: 1000' in report and 'Vendor signing certificate matches Vibe: no' in report
        for permission in ['WRITE_SECURE_SETTINGS','CHANGE_CONFIGURATION','SET_TIME','SET_TIME_ZONE','NETWORK_SETTINGS','TETHER_PRIVILEGED','BLUETOOTH_PRIVILEGED','MASTER_CLEAR','RECOVERY']:
            assert permission+': denied' in report, 'Access changed unexpectedly; stop before testing guarded forms'
        (out/'access.png').write_bytes(run('exec-out','screencap','-p'));key(4)
        tree=ui('system-return');tap(tree,'Language & input');tree=ui('language');tap(tree,'Device language')
        tree=ui('language-gate');assert 'Additional access needed' in texts(tree);key(4)
        tree=ui('language-return');tap(tree,'Choose keyboard');tree=ui('keyboard-gate');assert 'Additional access needed' in texts(tree);key(4)
        tree=ui('keyboard-return');tap(tree,'Date & time');tree=ui('date');tap(tree,'Automatic date & time')
        tree=ui('time-gate');assert 'Additional access needed' in texts(tree);key(4)
        tree=ui('date-return');tap(tree,'Connections')
        tree=find('Add Wi-Fi network','network-form-scroll');tap(tree,'Add Wi-Fi network');tree=ui('network-form')
        tap_node(next(n for n in tree.iter('node') if n.get('class')=='android.widget.EditText'))
        run('shell','input','text','VibeValidation')
        if b'mInputShown=true' in run('shell','dumpsys','input_method'):key(4)
        tree=ui('network-with-ssid');tap(tree,'Security · WPA2 personal');tree=ui('security-selector');tap(tree,'WPA3 personal')
        tree=ui('wpa3-form');assert 'Security · WPA3 personal' in texts(tree)
        tap(tree,next(text for text in texts(tree) if text.lower()=='request connection'))
        tree=ui('wpa3-rejected')
        assert any('does not report WPA3' in text or '1–63' in text for text in texts(tree)), 'An empty-password WPA3 request must not be submitted'
        (out/'wifi-validation.png').write_bytes(run('exec-out','screencap','-p'));key(4)
        tree=find('Networks added by Vibe','suggestions-scroll');tap(tree,'Networks added by Vibe');tree=ui('suggestions')
        assert 'Networks added by Vibe' in texts(tree);key(4)
        key(4);ui('final-home')
        result={'passed':True,'actual_app_access_checked':True,'permission_gates_checked':['language','keyboard','automatic_time'],
                'wpa3_request_rejected_before_submission':True,'own_suggestions_read':True,'mutating_action_executed':False,'full_legacy_parity':False}
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print(out.resolve())


if __name__=='__main__':main()
