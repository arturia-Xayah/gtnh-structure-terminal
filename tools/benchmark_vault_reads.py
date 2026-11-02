#!/usr/bin/env python3
"""Compare frozen and optimized single-vault adapters in isolated official dedicated servers."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess

from benchmark_vault_quantities import install_probe
from run_modernization_backend import prepare

ROOT = Path(__file__).resolve().parents[1]


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--java', type=Path, required=True)
    ap.add_argument('--pack', type=Path, required=True)
    ap.add_argument('--fixture', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--trials', type=int, default=3)
    args = ap.parse_args()
    java, pack, fixture, out = [p.resolve() for p in (args.java,args.pack,args.fixture,args.output)]
    if out.exists() or args.trials < 1:
        ap.error('Use a new output directory and positive trial count')
    out.mkdir(parents=True)
    shutil.copy2(fixture,out/'fixture.jar')
    results = []
    for trial in range(args.trials):
        folder = out / f'round-{trial}'
        prepare(pack,folder,fixture,'vault-read-validation')
        install_probe(fixture,folder)
        cmd=[str(java),'-Xms1G','-Xmx6G','-XX:+UseG1GC','-Dmodernization.tests=true',
             '-Daestoragevault.readTests=true',f'-Dvault.readRound={trial}',
             '-Djava.awt.headless=true','-Duser.language=en','@java9args.txt','-jar','lwjgl3ify-forgePatches.jar','nogui']
        with (out/f'round-{trial}.log').open('w') as log:
            proc=subprocess.run(cmd,cwd=folder,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
        report=folder/'vault-read-tests.json'
        if not report.exists():
            raise SystemExit(f'Missing report; inspect {out/f"round-{trial}.log"}')
        data=json.loads(report.read_text())
        shutil.copy2(report,out/f'round-{trial}.json')
        results.append(dict(trial=trial,exit=proc.returncode,passed=bool(data['passed'] and proc.returncode==0),report=f'round-{trial}.json'))
        summary=dict(passed=all(r['passed'] for r in results),client_launched=False,platform=platform.platform(),fixture_sha256=sha(fixture),runs=results)
        (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
        print(json.dumps(results[-1]),flush=True)
        if not results[-1]['passed']:
            raise SystemExit('Read benchmark failed; inspect '+str(report))


if __name__ == '__main__':
    main()
