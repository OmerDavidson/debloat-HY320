#!/usr/bin/env python3
"""Matched short Vibe Settings sample. Restarts only Vibe; never clears data/logs.

Captures PSS, process CPU ticks, and incremental frame statistics without
resetting counters. Fixed Home→Settings navigation does not change settings.
Use a fresh output directory for each installed artifact.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET

PKG='com.vibehome.launcher'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--cycles',type=int,default=5)
    args=parser.parse_args();out=args.output;out.mkdir(parents=True,exist_ok=False)
    adb=[shutil.which('adb') or '/opt/homebrew/bin/adb','-s',args.serial];commands=[]
    def run(*argv):
        r=subprocess.run(adb+list(argv),capture_output=True,timeout=45)
        commands.append({'argv':argv,'returncode':r.returncode,'stderr':r.stderr.decode(errors='replace')})
        if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout
    def save(name,*argv):
        raw=run(*argv);(out/name).write_bytes(raw);return raw.decode(errors='replace')
    def ui(name):
        raw=save(name+'.xml','exec-out','uiautomator','dump','/dev/tty')
        return ET.fromstring(raw[raw.index('<?xml'):raw.rindex('</hierarchy>')+12])
    def tap(tree,label):
        n=next(n for n in tree.iter('node') if n.get('text')==label)
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
        run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    def frames(raw):
        result={};header=None
        for line in raw.splitlines():
            if line.startswith('Flags,'):header=line.rstrip(',').split(',');continue
            if header and re.match(r'^\d+,\d+,',line):
                values=line.rstrip(',').split(',')
                if len(values)!=len(header):continue
                item=dict(zip(header,map(int,values)))
                if item['Flags']==0 and item['FrameCompleted']>item['IntendedVsync']:
                    result[item['IntendedVsync']]=item
        return result
    def gfx(name):return frames(save(name+'.txt','shell','dumpsys','gfxinfo',PKG,'framestats'))
    def ticks(pid):
        raw=run('shell','cat','/proc/'+pid+'/stat').decode();v=raw[raw.rindex(')')+2:].split()
        return {'ticks':int(v[11])+int(v[12]),'start':int(v[19]),'uptime':float(run('shell','cat','/proc/uptime').split()[0])}
    def state_sample(label):
        time.sleep(3);pid=run('shell','pidof',PKG).decode().strip();assert pid.isdigit()
        samples=[]
        for i in range(3):
            a=ticks(pid);time.sleep(8);b=ticks(pid);assert a['start']==b['start']
            seconds=b['uptime']-a['uptime'];cpu=100*(b['ticks']-a['ticks'])/hz/seconds
            pss=save(label+'-memory-'+str(i)+'.txt','shell','dumpsys','meminfo',PKG)
            value=re.search(r'TOTAL PSS:\s+(\d+)',pss);assert value
            samples.append({'seconds':seconds,'cpu_ticks':b['ticks']-a['ticks'],'cpu_percent_one_core':cpu,'pss_kib':int(value.group(1))})
        for package in ['com.htc.htcsettings','com.htc.htclauncherhighenglishd08']:
            save(label+'-'+package+'-memory.txt','shell','dumpsys','meminfo',package)
        save(label+'-processes.txt','shell','ps','-A')
        return {'samples':samples,'median_pss_kib':statistics.median(s['pss_kib'] for s in samples),'median_cpu_percent_one_core':statistics.median(s['cpu_percent_one_core'] for s in samples)}
    try:
        for prop,want in [('ro.product.model',b'HY320'),('ro.hardware',b'sun50iw12p1'),('ro.build.display.id',b'UI_Veng.projector.20241225.164550')]:assert run('shell','getprop',prop).strip()==want
        home=save('home-before.txt','shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
        assert PKG+'/.HomeEntry' in home,'Requires Vibe already selected as HOME; never selects it'
        assert '1920x1080' in run('shell','wm','size').decode() and '320' in run('shell','wm','density').decode()
        path=run('shell','pm','path',PKG).decode().strip().split(':',1)[1]
        apk_hash=hashlib.sha256(run('exec-out','cat',path)).hexdigest()
        package=save('package.txt','shell','dumpsys','package',PKG)
        assert re.search(r'versionName=0\.[78]\.',package), 'Historical direct Settings benchmark requires 0.7.x or 0.8.x'
        hz=int(run('shell','getconf','CLK_TCK'))
        # Reversible app-only process restart makes both phases use one fresh HOME root.
        run('shell','am','force-stop',PKG)
        save('start.txt','shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n',PKG+'/.HomeEntry')
        tree=ui('initial-home');tap(tree,'Settings');tree=ui('warmup-settings');tap(tree,'Home');ui('warmup-home')
        home_samples=state_sample('home')
        collected=[];cycles=[]
        for i in range(args.cycles):
            before=gfx('cycle-'+str(i)+'-before')
            # Fixed coordinates are verified against this exact 1080p/density-320 UI.
            run('shell','input','tap','1755','112');time.sleep(.8)
            after=gfx('cycle-'+str(i)+'-after')
            fresh=[v for k,v in after.items() if k not in before]
            collected.extend(fresh);cycles.append({'cycle':i,'new_frames':len(fresh)})
            tree=ui('settings-'+str(i));assert any(n.get('text')=='Picture' for n in tree.iter('node'))
            if i+1<args.cycles:tap(tree,'Home');time.sleep(.5)
        settings_samples=state_sample('settings-picture')
        (out/'settings.png').write_bytes(run('exec-out','screencap','-p'))
        # Native volume page is shared by both implementations.
        tap(ui('settings-before-sound'),'Sound');ui('sound');(out/'sound.png').write_bytes(run('exec-out','screencap','-p'))
        durations=sorted((f['FrameCompleted']-f['IntendedVsync'])/1e6 for f in collected)
        input_latency=[(f['FrameCompleted']-f['OldestInputEvent'])/1e6 for f in collected if 0<f['OldestInputEvent']<f['FrameCompleted'] and f['OldestInputEvent']<9e18]
        result={'apk_sha256':apk_hash,'cycles':cycles,'home':home_samples,'settings_picture':settings_samples,'settings_transition_frames':len(durations),
                'median_frame_ms':statistics.median(durations) if durations else None,'p95_frame_ms':durations[min(len(durations)-1,int(len(durations)*.95))] if durations else None,
                'frames_over_16_67_ms':sum(d>16.67 for d in durations),'input_frame_completion_ms':input_latency,
                'conditions':f'Fresh Vibe HOME process, one Home/Settings warmup, {args.cycles} warm Settings entries, 3x8-second idle windows per page, no resets/GC/vendor kills',
                'limitations':'Sequential short sample; caches/JIT/background activity differ. Input frame completion is a rendering metric, not end-to-end perceived settling time.'}
        assert save('home-after.txt','shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')==home
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print(out.resolve())


if __name__=='__main__':main()
