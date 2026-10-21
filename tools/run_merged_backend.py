#!/usr/bin/env python3
"""Run either integration suite in a fresh GTNH dedicated server, never a client."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--suite',choices=['ae','structures'],required=True)
    ap.add_argument('--pack',type=Path,required=True)
    ap.add_argument('--java',type=Path,required=True)
    ap.add_argument('--fixture',type=Path,required=True)
    ap.add_argument('--run-dir',type=Path,required=True)
    ap.add_argument('--machine-ids',default='141,1000,15750')
    a=ap.parse_args(); pack=a.pack.resolve(); target=a.run_dir.resolve()
    if target.exists() or target==pack or pack in target.parents:ap.error('Use a new directory outside the source pack')
    for p in (pack/'mods', pack/'java9args.txt', pack/'lwjgl3ify-forgePatches.jar', a.java, a.fixture):
        if not p.exists():ap.error('Missing '+str(p))
    target.mkdir(parents=True); (target/'mods').mkdir()
    for p in (pack/'mods').iterdir():
        if any(x in p.name.lower() for x in ('structureterminal','aeorderoptimizer','aeoptimizer','aeorderbench','plasmaforgepower')):continue
        (target/'mods'/p.name).symlink_to(p,target_is_directory=p.is_dir())
    for name in ('config','scripts','serverutilities','blueprints'):
        if (pack/name).is_dir():shutil.copytree(pack/name,target/name)
    for name in ('libraries','nestedmods'):
        if (pack/name).is_dir():(target/name).symlink_to(pack/name,target_is_directory=True)
    for p in pack.iterdir():
        if p.is_file() and (p.suffix=='.jar' or p.name=='java9args.txt'):shutil.copy2(p,target/p.name)
    shutil.copy2(a.fixture.resolve(),target/'mods/structureterminal-test-fixture.jar')
    world='ae-order-validation' if a.suite=='ae' else 'terminal-pack-validation'
    (target/'eula.txt').write_text('eula=true\n')
    (target/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name='+world+'\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nallow-nether=false\nspawn-monsters=false\nspawn-animals=false\nenable-query=false\nenable-rcon=false\nallow-flight=true\nmax-tick-time=120000\n')
    flags=['-Daeoptimizer.tests=true'] if a.suite=='ae' else ['-Dstructureterminal.packTests=true','-Dstructureterminal.machineIds='+a.machine_ids,'-Dstructureterminal.energyTier.141=3','-Dstructureterminal.explicitHatchOnly=true']
    command=[str(a.java.resolve()),'-Xms1G','-Xmx6G','-XX:+UseG1GC',*flags,'-Daeorderbench.tests=false','-Daeorderbench.hooks=false','-Djava.awt.headless=true','-Duser.language=en','-Dfml.readTimeout=180','@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui']
    print('Fixture SHA256:',hashlib.sha256(a.fixture.read_bytes()).hexdigest(),flush=True)
    with (target/'benchmark-server.log').open('w') as log:
        proc=subprocess.Popen(command,cwd=target,stdout=log,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+1200
        while proc.poll() is None:
            time.sleep(5)
            if time.monotonic()>deadline:
                proc.terminate();proc.wait(timeout=30);raise SystemExit('20-minute limit; logs retained')
    result=target/('ae-order-report.json' if a.suite=='ae' else 'machine-validation/report.json')
    if not result.exists():raise SystemExit('No report; inspect '+str(target/'benchmark-server.log'))
    data=json.loads(result.read_text())
    print(json.dumps({'exit':proc.returncode,'suite':a.suite,'report':str(result),'completed':data.get('completed'),'fatal_error':data.get('fatal_error')},ensure_ascii=False),flush=True)
    if proc.returncode or data.get('fatal_error') or (a.suite=='ae' and not data.get('completed')):raise SystemExit(1)

if __name__=='__main__':main()
