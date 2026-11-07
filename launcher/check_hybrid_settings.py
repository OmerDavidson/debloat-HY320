#!/usr/bin/env python3
"""Exercise hybrid settings links and return focus without changing values.

Only opens vendor menus/editors and presses Back; never touches their controls.
Factory reset is tested only through Vibe's cancelled entry guard. No update,
reset, default-HOME selection, permission grant, projection adjustment or scan
is initiated by Vibe. Vendor network pages may perform their own discovery.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

PKG='com.vibehome.launcher';VENDOR='com.htc.htcsettings'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--serial',required=True);parser.add_argument('--output',type=Path);parser.add_argument('--routes-from',type=int,default=0,help='Resume remaining routes after inspecting an earlier partial run')
    args=parser.parse_args();out=args.output or Path(__file__).parent/'test-results'/(dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-hybrid')
    out.mkdir(parents=True,exist_ok=False);adb=[shutil.which('adb') or '/opt/homebrew/bin/adb','-s',args.serial];commands=[];routes=[]
    def run(*argv):
        r=subprocess.run(adb+list(argv),capture_output=True,timeout=45);commands.append({'argv':argv,'returncode':r.returncode,'stderr':r.stderr.decode(errors='replace')})
        if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout
    def save(name,*argv):raw=run(*argv);(out/name).write_bytes(raw);return raw.decode(errors='replace')
    def ui(name,package=PKG):
        raw=save(name+'.xml','exec-out','uiautomator','dump','/dev/tty');tree=ET.fromstring(raw[raw.index('<?xml'):raw.rindex('</hierarchy>')+12])
        assert any(n.get('package')==package for n in tree.iter('node')),('Unexpected foreground',name)
        return tree
    def texts(tree):return [n.get('text') for n in tree.iter('node') if n.get('text')]
    def tap(tree,label):
        n=next(n for n in tree.iter('node') if n.get('text')==label);x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
        run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    def find(label,name):
        for i in range(12):
            tree=ui(name+'-'+str(i));n=next((n for n in tree.iter('node') if n.get('text')==label),None)
            if n is not None:
                b=list(map(int,re.findall(r'\d+',n.get('bounds'))))
                if b[3]-b[1]>=24:return tree
            run('shell','input','swipe','1600','730','1600','620','500')
        raise AssertionError('Missing control '+label)
    def key(code):run('shell','input','keyevent',str(code))
    def state(prefix):
        home=save(prefix+'-home.txt','shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
        disabled=save(prefix+'-disabled.txt','shell','pm','list','packages','-d');raw=save(prefix+'-properties.txt','shell','getprop')
        props={k:v for k,v in re.findall(r'^\[([^\]]+)\]: \[(.*)\]$',raw,re.M) if k.startswith(('persist.display.keystone_','persist.sys.panel','persist.sys.prj.','persist.sys.projection.','persist.sys.tpry','persist.sys.camok','persist.sys.zoom.','persist.vendor.overscan.','persist.sys.screendream.','persist.sys.usb0device','persist.sys.wifiAp','persist.sys.locale'))}
        result={'home':home,'disabled':disabled,'props':props}
        for namespace,keys in {'system':['volume_music','time_12_24','date_format','screen_off_timeout'],'global':['auto_time','auto_time_zone','adb_enabled','wifi_on','bluetooth_on'],'secure':['default_input_method','location_mode','enabled_input_methods']}.items():
            raw=save(prefix+'-'+namespace+'.txt','shell','settings','list',namespace);values=dict(line.split('=',1) for line in raw.splitlines() if '=' in line)
            result[namespace]={k:values.get(k) for k in keys}
        return result
    def focused_text(tree):
        for n in tree.iter('node'):
            if n.get('focused')=='true':return [child.get('text') for child in n.iter('node') if child.get('text')]
        return []
    try:
        for prop,want in [('ro.product.model',b'HY320'),('ro.hardware',b'sun50iw12p1'),('ro.build.display.id',b'UI_Veng.projector.20241225.164550')]:assert run('shell','getprop',prop).strip()==want
        assert '1920x1080' in run('shell','wm','size').decode() and '320' in run('shell','wm','density').decode()
        before=state('before');assert PKG+'/.HomeEntry' in before['home']
        # DateTimeActivity reasserts its current 12/24-hour format on entry. Do not
        # open it if the setting is absent (that would create a new global value).
        assert before['system']['time_12_24'] in ('12','24')
        key(3);tree=ui('home');tap(tree,'Settings');tree=ui('settings-entry')
        if 'Vibe settings' in texts(tree):
            tap(tree,'Vibe settings');tree=ui('settings')
        (out/'settings.png').write_bytes(run('exec-out','screencap','-p'))
        for section,label,page in [
            ('Picture','Projection & keystone','ProjectionActivity'),('Picture','Brightness & color','PictureModeActivity'),
            ('Picture','Screen saver','ProjectionActivity'),('Connections','Wi-Fi','WIFIActivity'),
            ('Connections','Bluetooth','BluetoothSettingActivity'),('Connections','Wi-Fi hotspot','HotspotActivity'),
            ('Language & input','Device language','LanguageListActivity'),('Language & input','Keyboard settings','LanguageActivity'),
            ('Date & time','Date & time settings','DateTimeActivity'),('System','More device settings','HomeActivity'),
            ('System','System updates','AboutDeviceActivity'),('System','Developer & USB settings','OtherSettingsActivity')][args.routes_from:]:
            tap(tree,section);tree=find(label,'find-'+page);before_log=run('shell','logcat','-d','-v','threadtime','-t','1000').decode()
            tap(tree,label);external=ui('vendor-'+page,VENDOR);activity=save('activity-'+page+'.txt','shell','dumpsys','activity','activities')
            assert any(VENDOR+'/com.htc.activity.'+page in line or VENDOR+'/.activity.'+page in line for line in activity.splitlines() if 'mResumedActivity' in line or 'mResumedActivity' in line or 'ResumedActivity' in line),page
            (out/('vendor-'+page+'.png')).write_bytes(run('exec-out','screencap','-p'))
            logs=save('display-'+page+'.txt','shell','logcat','-d','-v','threadtime','-t','1000')
            new_lines=[line for line in logs.splitlines() if line not in before_log.splitlines() and 'Displayed' in line and page in line]
            routes.append({'section':section,'label':label,'activity':page,'displayed_log':new_lines})
            if page=='WIFIActivity':
                save('wifi-vibe-memory.txt','shell','dumpsys','meminfo',PKG);save('wifi-vendor-memory.txt','shell','dumpsys','meminfo',VENDOR)
            key(4);tree=ui('return-'+page);assert label in focused_text(tree),('Return focus not restored',label,focused_text(tree))
        tap(tree,'System');tree=find('Advanced Android settings','find-android');tap(tree,'Advanced Android settings');ui('android-settings','com.android.tv.settings')
        for package,name in [(PKG,'vibe'),(VENDOR,'vendor'),('com.android.tv.settings','android-tv')]:save('advanced-'+name+'-memory.txt','shell','dumpsys','meminfo',package)
        key(4);tree=ui('return-android');assert 'Advanced Android settings' in focused_text(tree)
        # Touch-scroll away from an old selected action, leave Settings, and
        # reopen it. Any restored focus must be visible (never a stale offscreen row).
        run('shell','input','swipe','1600','760','1600','400','220')
        key(3);tree=ui('scroll-home');tap(tree,'Settings');tree=ui('scroll-restored')
        if 'Vibe settings' in texts(tree):
            tap(tree,'Vibe settings');tree=ui('vibe-scroll-restored')
        focus=next(n for n in tree.iter('node') if n.get('focused')=='true')
        b=list(map(int,re.findall(r'\d+',focus.get('bounds'))))
        assert 341<=b[1]<b[3]<=828,('Restored focus is outside Settings viewport',focus.attrib)
        tap(tree,'Picture');tree=ui('reset-scroll-section');tap(tree,'System')
        tree=find('Factory reset','find-reset');tap(tree,'Factory reset');tree=ui('reset-guard');assert 'Open factory reset settings?' in texts(tree)
        assert 'Cancel' in focused_text(tree) or 'CANCEL' in focused_text(tree);key(4);tree=ui('reset-cancelled')
        tap(tree,'Sound');tree=ui('native-sound');assert any(n.get('class')=='android.widget.SeekBar' for n in tree.iter('node'));(out/'native-sound.png').write_bytes(run('exec-out','screencap','-p'))
        tap(tree,'Picture');tree=find('Projection preview','find-draft');tap(tree,'Projection preview');tree=ui('draft');assert any('Preview only' in text for text in texts(tree));key(4);tree=ui('return-draft');assert 'Projection preview' in focused_text(tree)
        # D-pad traversal from a touched category remains inside Settings.
        tap(tree,'Picture');key(20);key(23);tree=ui('remote-connections');assert 'Wi-Fi' in texts(tree);key(22);tree=ui('remote-link-focus');assert any(label in focused_text(tree) for label in ['Wi-Fi','Bluetooth','Wi-Fi hotspot'])
        key(4);tree=ui('final-home');assert any(n.get('class')=='android.widget.GridView' for n in tree.iter('node'))
        after=state('after');assert before==after,('Settings changed',before,after)
        save('logs.txt','shell','logcat','-d','-b','main','-b','crash','-v','threadtime','-t','1500')
        (out/'result.json').write_text(json.dumps({'passed':True,'routes':routes,'android_advanced_returned':True,'return_focus_preserved':True,'factory_reset_guard_cancelled':True,'device_state_unchanged':True,'default_home_unchanged':True,'settings_values_changed':False,'routes_from':args.routes_from},indent=2)+'\n')
    finally:(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print(out.resolve())


if __name__=='__main__':main()
