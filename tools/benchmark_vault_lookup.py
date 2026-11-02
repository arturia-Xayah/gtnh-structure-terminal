#!/usr/bin/env python3
"""Paired hot-query and component timings after the independent-process enumeration matrix."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from benchmark_vault_quantities import install_probe
from run_modernization_backend import prepare


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for name in ['java','pack','fixture','output']:
        ap.add_argument('--'+name,type=Path,required=True)
    args=ap.parse_args()
    java,pack,fixture,out=[p.resolve() for p in (args.java,args.pack,args.fixture,args.output)]
    if out.exists():ap.error('Use a new output directory')
    out.mkdir(parents=True)
    target=out/'server'
    prepare(pack,target,fixture,'vault-read-validation')
    install_probe(fixture,target)
    shutil.copy2(fixture,out/'fixture.jar')
    cmd=[str(java),'-Xms1G','-Xmx6G','-XX:+UseG1GC','-Dmodernization.tests=true',
         '-Daestoragevault.readTests=true','-Daestoragevault.lookupOnly=true',
         '-Djava.awt.headless=true','-Duser.language=en','@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui']
    with (out/'server.log').open('w') as log:
        process=subprocess.run(cmd,cwd=target,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
    report=target/'vault-read-tests.json'
    if not report.exists():raise SystemExit('Missing report; inspect '+str(out/'server.log'))
    data=json.loads(report.read_text())
    shutil.copy2(report,out/'paired.json')
    passed=bool(process.returncode==0 and data['passed'] and len(data['paired_queries'])==4)
    (out/'summary.json').write_text(json.dumps(dict(passed=passed,exit=process.returncode,client_launched=False,
        fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),report='paired.json'),indent=2)+'\n')
    print(json.dumps(dict(passed=passed,exit=process.returncode)),flush=True)
    if not passed:raise SystemExit(1)


if __name__=='__main__':main()
