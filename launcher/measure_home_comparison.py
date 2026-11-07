#!/usr/bin/env python3
"""One block of a matched default-HOME comparison; never selects a default.

Requires the specified launcher already selected through Android's user-approved
Home UI. No package stops, data/log/counter clears, grants, display changes,
settings adjustments or hardware writes. Opens the same vendor Wi-Fi page in
both conditions, then presses Back/Home. Vendor UI may perform its own scans.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET

VIBE='com.vibehome.launcher'
STOCK='com.htc.htclauncherhighenglishd08'
VENDOR='com.htc.htcsettings'
PACKAGES=[VIBE,STOCK,VENDOR,'com.android.tv.settings']
HOMES={'vibe':VIBE+'/.HomeEntry','legacy':STOCK+'/com.htc.activity.HomeActivity'}


def frame_rows(raw):
    header=None;rows={}
    for line in raw.splitlines():
        if line.startswith('Flags,'):header=line.rstrip(',').split(',')
        elif header and re.match(r'^\d+,\d+,',line):
            values=line.rstrip(',').split(',')
            if len(values)==len(header):
                row=dict(zip(header,map(int,values)))
                if row['Flags']==0 and 0<row['IntendedVsync']<row['FrameCompleted']<9e18:rows[row['IntendedVsync']]=row
    return rows


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True);parser.add_argument('--expected-home',choices=HOMES,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--package-record',type=Path,required=True)
    parser.add_argument('--samples',type=int,default=3);parser.add_argument('--seconds',type=float,default=8)
    args=parser.parse_args();out=args.output;out.mkdir(parents=True,exist_ok=False)
    adb=[shutil.which('adb') or '/opt/homebrew/bin/adb','-s',args.serial];commands=[]
    active=VIBE if args.expected_home=='vibe' else STOCK;expected=HOMES[args.expected_home]
    def run(*argv,optional=False):
        start=time.monotonic();r=subprocess.run(adb+list(argv),capture_output=True,timeout=60)
        commands.append({'argv':argv,'returncode':r.returncode,'elapsed_seconds':time.monotonic()-start,'stderr':r.stderr.decode(errors='replace')})
        if r.returncode and not optional:raise RuntimeError(str(argv)+': '+r.stderr.decode(errors='replace'))
        return r.stdout
    def save(name,*argv,optional=False):
        raw=run(*argv,optional=optional);(out/name).write_bytes(raw);return raw.decode(errors='replace')
    def home(name):
        raw=save(name,'shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
        return raw.strip().splitlines()[-1]
    def foreground(name):
        raw=save(name,'shell','dumpsys','activity','activities');return next((line.strip() for line in raw.splitlines() if 'mResumedActivity:' in line),'')
    def assert_home_foreground(name):
        assert home(name+'-home.txt')==expected,'Default HOME changed or was not selected'
        resumed=foreground(name+'-activity.txt');assert active+'/' in resumed,('Launcher is not foreground',resumed)
    def kernel(name):
        raw=save(name,'shell','cat','/proc/uptime','/proc/stat','/proc/meminfo','/proc/pressure/memory','/proc/vmstat',optional=True)
        cpu=next(line.split()[1:] for line in raw.splitlines() if line.startswith('cpu '));cpu=list(map(int,cpu[:8]))
        mem={k:int(v) for k,v in re.findall(r'^(\w+):\s+(\d+) kB$',raw,re.M)}
        assert all(k in mem for k in ['MemTotal','MemAvailable','SwapTotal','SwapFree'])
        pressure={kind:{key:int(value) for key,value in re.findall(r'(total)=(\d+)',tail)} for kind,tail in re.findall(r'^(some|full) (.*)$',raw,re.M)}
        vm={k:int(v) for k,v in re.findall(r'^(pswpin|pswpout|pgmajfault) (\d+)$',raw,re.M)}
        return {'uptime':float(raw.splitlines()[0].split()[0]),'cpu_total':sum(cpu),'cpu_idle':cpu[3]+cpu[4],'memory_kib':mem,'pressure':pressure,'vm':vm}
    def processes(name):
        raw=save(name,'shell','ps','-A','-o','PID,ARGS');groups={pkg:[] for pkg in PACKAGES};by_pid={}
        for line in raw.splitlines()[1:]:
            parts=line.strip().split(None,1)
            if len(parts)!=2 or not parts[0].isdigit():continue
            command=parts[1].split()[0]
            for pkg in PACKAGES:
                if command==pkg or command.startswith(pkg+':'):groups[pkg].append(parts[0]);by_pid[parts[0]]=pkg
        paths=['/proc/'+pid+'/stat' for pid in by_pid];ticks={}
        if paths:
            raw=save(name+'.stats','shell','cat',*paths,optional=True)
            for line in raw.splitlines():
                if ')' not in line:continue
                pid=line.split()[0]
                if pid not in by_pid:continue
                values=line[line.rindex(')')+2:].split()
                ticks[pid]={'ticks':int(values[11])+int(values[12]),'start':int(values[19]),'package':by_pid[pid]}
        return groups,ticks
    def snapshot(label):
        result=[]
        for index in range(args.samples):
            assert_home_foreground(label+'-'+str(index)+'-pre') if label!='wifi-open' else None
            groups,a=processes(label+'-'+str(index)+'-process-start.txt');ka=kernel(label+'-'+str(index)+'-kernel-start.txt')
            time.sleep(args.seconds)
            kb=kernel(label+'-'+str(index)+'-kernel-end.txt');end_groups,b=processes(label+'-'+str(index)+'-process-end.txt')
            dt_seconds=kb['uptime']-ka['uptime'];total=kb['cpu_total']-ka['cpu_total'];idle=kb['cpu_idle']-ka['cpu_idle']
            sample={'seconds':dt_seconds,'mem_available_kib':kb['memory_kib']['MemAvailable'],'mem_total_kib':kb['memory_kib']['MemTotal'],
                    'swap_used_kib':kb['memory_kib']['SwapTotal']-kb['memory_kib']['SwapFree'],'system_cpu_busy_percent':100*(total-idle)/total,
                    'packages':{},'pressure':kb['pressure'],'vm_delta':{key:kb['vm'].get(key,0)-ka['vm'].get(key,0) for key in kb['vm']}}
            for pkg,pids in end_groups.items():
                stable=[pid for pid in pids if pid in a and pid in b and a[pid]['start']==b[pid]['start']]
                cpu=100*sum(b[pid]['ticks']-a[pid]['ticks'] for pid in stable)/hz/dt_seconds if pids and len(stable)==len(pids) else None
                pss=0;rss=0
                for pid in pids:
                    raw=save(label+'-'+str(index)+'-'+pkg+'-'+pid+'-memory.txt','shell','dumpsys','meminfo',pid)
                    match=re.search(r'TOTAL PSS:\s+(\d+)',raw);assert match,('No PSS',pkg,pid);pss+=int(match.group(1))
                    match=re.search(r'TOTAL RSS:\s+(\d+)',raw);rss+=int(match.group(1)) if match else 0
                sample['packages'][pkg]={'pids':pids,'pss_kib':pss,'rss_kib':rss,'cpu_percent_one_core':cpu}
            sample['tracked_process_pss_kib']=sum(v['pss_kib'] for v in sample['packages'].values())
            result.append(sample);print(label,index+1,'MemAvailable MiB',round(sample['mem_available_kib']/1024,1),'tracked PSS MiB',round(sample['tracked_process_pss_kib']/1024,1),flush=True)
        save(label+'-whole-memory.txt','shell','dumpsys','meminfo')
        summary={'samples':result}
        for key in ['mem_available_kib','swap_used_kib','system_cpu_busy_percent','tracked_process_pss_kib']:summary['median_'+key]=statistics.median(row[key] for row in result)
        summary['packages']={pkg:{'median_pss_kib':statistics.median(row['packages'][pkg]['pss_kib'] for row in result),'median_cpu_percent_one_core':statistics.median(v) if (v:=[row['packages'][pkg]['cpu_percent_one_core'] for row in result if row['packages'][pkg]['cpu_percent_one_core'] is not None]) else None,'present_samples':sum(bool(row['packages'][pkg]['pids']) for row in result)} for pkg in PACKAGES}
        (out/(label+'-result.json')).write_text(json.dumps(summary,indent=2)+'\n')
        return summary
    def capture(name):
        raw=save(name+'.xml','exec-out','uiautomator','dump','/dev/tty');tree=ET.fromstring(raw[raw.index('<?xml'):raw.rindex('</hierarchy>')+12]);(out/(name+'.png')).write_bytes(run('exec-out','screencap','-p'));return tree
    try:
        for prop,want in [('ro.product.model',b'HY320'),('ro.hardware',b'sun50iw12p1'),('ro.build.display.id',b'UI_Veng.projector.20241225.164550')]:assert run('shell','getprop',prop).strip()==want
        assert '1920x1080' in run('shell','wm','size').decode() and '320' in run('shell','wm','density').decode()
        assert home('home-before.txt')==expected,'Set the requested HOME through approved Android UI before running this block'
        records=json.loads(args.package_record.read_text())
        for pkg,info in records.items():
            path=run('shell','pm','path',pkg).decode().strip().splitlines()[0].split(':',1)[1]
            assert hashlib.sha256(run('exec-out','cat',path)).hexdigest()==info['sha256'],'Package changed: '+pkg
        hz=int(run('shell','getconf','CLK_TCK'));run('shell','input','keyevent','3');time.sleep(15);assert_home_foreground('settled')
        tree=capture('home-idle');assert not any(n.get('focused')=='true' and n.get('class') in ('android.widget.SeekBar','android.widget.Switch','android.widget.CheckBox') for n in tree.iter('node')),'Do not send navigation to a live setting control'
        phases={'home_idle':snapshot('home-idle')}
        # Matched focus-only navigation: no OK/Menu/volume/global settings key.
        frames_before=frame_rows(save('navigation-before.txt','shell','dumpsys','gfxinfo',active,'framestats'))
        for key in [22]*4+[21]*4+[22]*4+[21]*4:
            run('shell','input','keyevent',str(key));time.sleep(.35)
        time.sleep(1);frames_after=frame_rows(save('navigation-after.txt','shell','dumpsys','gfxinfo',active,'framestats'))
        fresh=[value for key,value in frames_after.items() if key not in frames_before]
        durations=sorted((f['FrameCompleted']-f['IntendedVsync'])/1e6 for f in fresh)
        navigation={'keys':'RIGHT x4, LEFT x4, twice; 350 ms explicit spacing plus ADB command latency','frames':len(durations),'median_frame_ms':statistics.median(durations) if durations else None,'p95_frame_ms':durations[min(len(durations)-1,int(.95*len(durations)))] if durations else None}
        assert_home_foreground('navigated');capture('home-navigated');run('shell','input','keyevent','3')
        save('wifi-launch.txt','shell','am','start','-W','-n',VENDOR+'/com.htc.activity.WIFIActivity');time.sleep(8)
        assert VENDOR+'/' in foreground('wifi-foreground.txt');capture('wifi-open')
        phases['wifi_open']=snapshot('wifi-open')
        run('shell','input','keyevent','4');time.sleep(1);assert active+'/' in foreground('wifi-back.txt'),'Back did not return to the selected launcher'
        run('shell','input','keyevent','3');time.sleep(12);assert_home_foreground('returned');capture('home-returned')
        phases['home_after_settings']=snapshot('home-after-settings');assert_home_foreground('finished')
        save('services-stock.txt','shell','dumpsys','activity','services',STOCK);save('logs.txt','shell','logcat','-d','-b','main','-b','crash','-v','threadtime','-t','1500')
        result={'expected_home':expected,'launcher':active,'created_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'phases':phases,'navigation':navigation,'package_hashes':records,
                'settings_changed_by_script':False,'default_changed_by_script':False,'processes_stopped_by_script':False,'samples_per_phase':args.samples,'cpu_window_seconds':args.seconds,
                'limitations':'Sequential alternating conditions; no reboot/cache purge/vendor kills. Both launchers may remain resident after switching. MemAvailable is a kernel estimate; PSS is apportioned. Small block count; no inference from APK size.'}
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    finally:(out/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print(out.resolve(),flush=True)


if __name__=='__main__':main()
