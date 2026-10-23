#!/usr/bin/env python3
"""Upgrade a copy of an earlier disposable vault test world, then restart it again."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from run_modernization_backend import prepare


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('pack', 'java', 'fixture', 'old-run', 'run-dir'):
        ap.add_argument('--' + name, type=Path, required=True)
    args = ap.parse_args()
    pack, java, fixture, old, target = (p.resolve() for p in
        (args.pack, args.java, args.fixture, args.old_run, args.run_dir))
    if target.exists() or not (old / 'vault-restart.nbt').is_file():
        ap.error('Use a new output directory and an earlier disposable vault suite with its restart marker')
    target.parent.mkdir(parents=True, exist_ok=True)
    prepare(pack, target, fixture, 'vault-validation')
    shutil.copytree(old / 'vault-validation', target / 'vault-validation')
    shutil.copy2(old / 'vault-restart.nbt', target / 'vault-restart.nbt')
    rows = []
    for stage in (1, 2):
        cmd = [str(java), '-Xms1G', '-Xmx6G', '-XX:+UseG1GC', '-Dmodernization.tests=true',
               '-Daestoragevault.tests=true', '-Djava.awt.headless=true', '-Duser.language=en',
               '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
        with (target / f'server-{stage}.log').open('w') as log:
            process = subprocess.run(cmd, cwd=target, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
        report = target / 'vault-tests-restart.json'
        data = json.loads(report.read_text())
        modules = json.loads((target / 'modernization-modules.json').read_text())
        passed = process.returncode == 0 and data['passed'] and modules['passed'] and not data['client_launched']
        copy = f'upgrade-{stage}.json'
        shutil.copy2(report, target / copy)
        row = dict(stage=stage, passed=bool(passed), exit=process.returncode, cases=len(data['tests']), report=copy)
        rows.append(row)
        summary = dict(passed=all(r['passed'] for r in rows), client_launched=False,
                       fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest(), runs=rows)
        (target / 'upgrade-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        print(json.dumps(row), flush=True)
        if not passed: raise SystemExit(1)


if __name__ == '__main__': main()
