#!/usr/bin/env python3
"""Exercise every bundled feature in isolated dedicated GTNH servers; never starts a client."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time

from summarize_pack_validation import classify

EXCLUDED = ('structureterminal','aeorderoptimizer','aeoptimizer','aeorderbench','plasmaforgepower','aestoragevault','gtnh-modernization')
SUITES = {
    'vault_io': ('vault-io-validation', ['-Daestoragevault.ioTests=true'], ['vault-io-tests.json','vault-io-tests-restart.json']),
    'vault': ('vault-validation', ['-Daestoragevault.tests=true'], ['vault-tests.json','vault-tests-restart.json']),
    'forge': ('forge-power-validation', ['-Dplasmaforgepower.tests=true'], ['forge-power-tests.json']),
    'fusion': ('forge-power-validation', ['-Dplasmaforgepower.fusionTests=true'], ['fusion-power-tests.json']),
    'ae': ('ae-order-validation', ['-Daeoptimizer.tests=true'], ['ae-order-report.json']),
    'structures': ('terminal-pack-validation', ['-Dstructureterminal.packTests=true','-Dstructureterminal.machineIds=141,1000,15750','-Dstructureterminal.energyTier.141=3','-Dstructureterminal.explicitHatchOnly=true'], ['machine-validation/report.json']),
}

def prepare(pack,target,fixture,world):
    target.mkdir(); (target/'mods').mkdir()
    for p in (pack/'mods').iterdir():
        if not any(n in p.name.lower() for n in EXCLUDED):
            (target/'mods'/p.name).symlink_to(p,target_is_directory=p.is_dir())
    for name in ('config','scripts','serverutilities','blueprints'):
        if (pack/name).is_dir(): shutil.copytree(pack/name,target/name)
    for name in ('libraries','nestedmods'):
        if (pack/name).is_dir(): (target/name).symlink_to(pack/name,target_is_directory=True)
    for p in pack.iterdir():
        if p.is_file() and (p.suffix=='.jar' or p.name=='java9args.txt'): shutil.copy2(p,target/p.name)
    shutil.copy2(fixture,target/'mods/gtnh-modernization-tests.jar')
    (target/'eula.txt').write_text('eula=true\n')
    (target/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name={world}\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nallow-nether=false\nspawn-monsters=false\nspawn-animals=false\nenable-query=false\nenable-rcon=false\nallow-flight=true\nmax-tick-time=120000\n')

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--pack',type=Path,required=True);ap.add_argument('--java',type=Path,required=True)
    ap.add_argument('--fixture',type=Path,required=True);ap.add_argument('--run-dir',type=Path,required=True)
    ap.add_argument('--suites',nargs='+',choices=SUITES,default=list(SUITES));a=ap.parse_args()
    pack,target,fixture,java=(p.resolve() for p in (a.pack,a.run_dir,a.fixture,a.java))
    if target.exists() or pack==target or pack in target.parents: ap.error('Use a new directory outside the source pack')
    for p in (pack/'mods',pack/'java9args.txt',pack/'lwjgl3ify-forgePatches.jar',fixture,java):
        if not p.exists(): ap.error('Missing '+str(p))
    target.mkdir(parents=True); rows=[]
    for suite in a.suites:
        world,flags,reports=SUITES[suite];folder=target/suite;prepare(pack,folder,fixture,world)
        for stage,name in enumerate(reports,1):
            cmd=[str(java),'-Xms1G','-Xmx6G','-XX:+UseG1GC','-Dmodernization.tests=true',*flags,'-Djava.awt.headless=true','-Duser.language=en','-Dfml.readTimeout=180','@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui']
            with (folder/f'server-{stage}.log').open('w') as log:
                process=subprocess.Popen(cmd,cwd=folder,stdout=log,stderr=subprocess.STDOUT);deadline=time.monotonic()+1200
                while process.poll() is None:
                    time.sleep(2)
                    if time.monotonic()>deadline:
                        process.terminate();process.wait(timeout=30);raise SystemExit(f'{suite}: 20 minute timeout')
            report=folder/name
            if not report.exists():raise SystemExit(f'{suite}: missing report, inspect {folder/f"server-{stage}.log"}')
            data=json.loads(report.read_text());modules=json.loads((folder/'modernization-modules.json').read_text())
            if suite=='ae':passed=data.get('completed') and not data.get('fatal_error') and bool(data.get('cases')) and all(c['passed'] for c in data['cases']);cases=len(data.get('cases',[]))
            elif suite=='structures':
                passed=data.get('all_requested_machines_attempted') and not data.get('fatal_error') and bool(data.get('machines')) and all(classify(m)=='OPERATION_CHECKS_PASS' for m in data['machines']);cases=len(data.get('machines',[]))
                # Detailed per-phase assertions are evaluated by the existing summary tool after the run.
            else:passed=data.get('passed');cases=len(data.get('tests',[]))
            row=dict(suite=suite,stage=stage,exit=process.returncode,passed=bool(passed and modules.get('passed') and process.returncode==0),cases=cases,report=str(report.relative_to(target)))
            rows.append(row);print(json.dumps(row),flush=True)
            summary=dict(passed=all(r['passed'] for r in rows),client_launched=False,fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),runs=rows)
            (target/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
            if not row['passed']:raise SystemExit(1)
if __name__=='__main__':main()
