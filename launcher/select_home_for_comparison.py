#!/usr/bin/env python3
"""Execute ONE already-approved comparison switch via Android's Home chooser.

No preferred-activity shell override, alias mutation, package stop or grant.
Requires the explicit four-switch approval record and expected current default.
This is a local diagnostic helper, not an exported app command endpoint.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

VIBE='com.vibehome.launcher/.HomeEntry'
STOCK='com.htc.htclauncherhighenglishd08/com.htc.activity.HomeActivity'
SEQUENCE={1:(VIBE,STOCK,'HTCLauncherEN'),2:(STOCK,VIBE,'Vibe Home'),3:(VIBE,STOCK,'HTCLauncherEN'),4:(STOCK,VIBE,'Vibe Home')}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--serial',required=True);parser.add_argument('--step',type=int,choices=SEQUENCE,required=True)
    parser.add_argument('--approval-record',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();approval=json.loads(args.approval_record.read_text())
    if approval.get('approved_by_user') is not True:
        raise RuntimeError('Explicit approval record required')
    if approval.get('restore')!=VIBE or 'Four temporary HOME selections' not in approval.get('scope',''):
        raise RuntimeError('Approval scope or restore target does not match')
    before,target,label=SEQUENCE[args.step];out=args.output;out.mkdir(parents=True,exist_ok=False);adb=[shutil.which('adb') or '/opt/homebrew/bin/adb','-s',args.serial];commands=[]
    def run(*argv):
        r=subprocess.run(adb+list(argv),capture_output=True,timeout=45);commands.append({'argv':argv,'returncode':r.returncode,'stderr':r.stderr.decode(errors='replace')})
        if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout
    def home():return run('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').decode().strip().splitlines()[-1]
    try:
        for prop,want in [('ro.product.model',b'HY320'),('ro.hardware',b'sun50iw12p1'),('ro.build.display.id',b'UI_Veng.projector.20241225.164550')]:
            if run('shell','getprop',prop).strip()!=want:
                raise RuntimeError('Device identity mismatch: '+prop)
        if home()!=before:
            raise RuntimeError('Default does not match this approved switch step')
        (out/'before-home.txt').write_text(before+'\n');(out/'start.txt').write_bytes(run('shell','am','start','-W','-a','android.settings.HOME_SETTINGS'))
        root=None
        for attempt in range(3):
            raw=run('exec-out','uiautomator','dump','/dev/tty').decode();(out/('chooser-'+str(attempt)+'.xml')).write_text(raw)
            if '<?xml' in raw:
                root=ET.fromstring(raw[raw.index('<?xml'):raw.rindex('</hierarchy>')+12]);break
            time.sleep(.5)
        if root is None:
            raise RuntimeError('Home chooser hierarchy unavailable; no choice made')
        node=next(n for n in root.iter('node') if n.get('text')==label)
        if node.get('package') not in ['com.android.permissioncontroller','com.android.settings','com.android.tv.settings']:
            raise RuntimeError('Unexpected chooser owner')
        (out/'chooser.png').write_bytes(run('exec-out','screencap','-p'))
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')));run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
        for _ in range(5):
            if home()==target:break
            time.sleep(.4)
        if home()!=target:
            raise RuntimeError('System choice not confirmed; no shell override attempted')
        (out/'after-home.txt').write_text(target+'\n');run('shell','input','keyevent','3')
        (out/'activities.txt').write_bytes(run('shell','dumpsys','activity','activities'));(out/'selected-home.png').write_bytes(run('exec-out','screencap','-p'))
        (out/'result.json').write_text(json.dumps({'step':args.step,'before':before,'after':target,'choice_label':label,'method':'Android HOME_SETTINGS UI; user-approved tap','passed':True},indent=2)+'\n')
        print(target,flush=True)
    finally:(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')


if __name__=='__main__':main()
