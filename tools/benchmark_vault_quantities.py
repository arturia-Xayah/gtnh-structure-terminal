#!/usr/bin/env python3
"""Reproduce quantity preflight, without changing production code or starting a client."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CORE = Path('src/main/java/dev/aestoragevault/core')
BASELINE = '31aec05fc4b0256683d876e16f9f578643e1ca9d'


def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()


def install_probe(fixture, target):
    with zipfile.ZipFile(fixture) as source, zipfile.ZipFile(target/'mods/quantity-tick-probe.jar', 'w') as out:
        out.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nFMLCorePlugin: dev.modernization.packtest.quantity.probe.QuantityLoadingPlugin\n\n')
        for name in source.namelist():
            if name.startswith('dev/modernization/packtest/quantity/probe/') and name.endswith('.class'):
                out.writestr(name, source.read(name))


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--java', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--trials', type=int, default=3)
    ap.add_argument('--types', type=int, nargs='+', default=[1, 1000, 100000])
    ap.add_argument('--bits', type=int, nargs='+', default=[40, 127, 255, 1023])
    ap.add_argument('--production',action='store_true',help='Use the current production ledger instead of the frozen prototype')
    ap.add_argument('--baseline-ref', default=BASELINE)
    ap.add_argument('--pack', type=Path)
    ap.add_argument('--fixture', type=Path)
    args = ap.parse_args()
    java, out = args.java.resolve(), args.output.resolve()
    if out.exists(): ap.error('Use a new output directory')
    if args.trials < 1 or any(n < 1 for n in args.types): ap.error('Invalid matrix')
    if bool(args.pack) != bool(args.fixture): ap.error('Use --pack and --fixture together')
    out.mkdir(parents=True)
    for variant in ['baseline', 'bigint']:
        folder = out / variant
        folder.mkdir()
        source_names = ['Resource.java', 'CapacityValue.java', 'VaultStore.java']
        if variant == 'bigint' and args.production:
            source_names.append('NativeInventoryView.java')
        for name in source_names:
            if variant == 'bigint' and args.production:
                data = (ROOT/CORE/name).read_text()
            elif variant == 'bigint' and name == 'VaultStore.java':
                data = (ROOT / 'src/modernizationPackTest/java/dev/modernization/packtest/quantity/BigVaultStore.java').read_text()
                data = data.replace('package dev.modernization.packtest.quantity;', 'package dev.aestoragevault.core;').replace('BigVaultStore', 'VaultStore')
            else:
                data = subprocess.check_output(['git', 'show', f'{args.baseline_ref}:{CORE / name}'], cwd=ROOT, text=True)
            (folder / name).write_text(data)
        subprocess.run([str(java.with_name('javac')), '-encoding', 'UTF-8', '-d', str(folder / 'classes'),
                        *[str(folder / name) for name in source_names],
                        str(ROOT / 'tools/quantity_benchmark/QuantityBenchmark.java')], check=True)
    rows = []
    metadata = dict(production=args.production,baseline_ref=args.baseline_ref, java=str(java), platform=platform.platform(),
                    scope='isolated actual ledger; server fixture uses native codecs but no AE grid routing or GUI clients',
                    source_sha256={str(p.relative_to(ROOT)):digest(p) for p in [
                        ROOT/'tools/quantity_benchmark/QuantityBenchmark.java',
                        ROOT/'src/modernizationPackTest/java/dev/modernization/packtest/quantity/BigVaultStore.java',
                        ROOT/'src/modernizationPackTest/java/dev/modernization/packtest/quantity/QuantityPreflight.java']})
    for types in args.types:
        for trial in range(args.trials):
            choices = [('baseline', 40)] + [('bigint', bits) for bits in args.bits]
            if trial % 2: choices.reverse()
            for variant, bits in choices:
                result = subprocess.check_output([str(java), '-Xms512m', '-Xmx512m', '-cp',
                    str(out/variant/'classes'), 'QuantityBenchmark', str(out/f'data-{variant}-{types}-{bits}-{trial}'),
                    str(types), str(bits)], text=True)
                row = dict(variant=variant, trial=trial, **json.loads(result))
                rows.append(row)
                print(json.dumps(row), flush=True)
                (out/'isolated.json').write_text(json.dumps(dict(metadata=metadata,rows=rows),indent=2)+'\n')
    if args.pack:
        from run_modernization_backend import prepare
        target = out/'server'
        prepare(args.pack.resolve(), target, args.fixture.resolve(), 'quantity-preflight')
        install_probe(args.fixture.resolve(), target)
        command = [str(java), '-Xms1G', '-Xmx6G', '-XX:+UseG1GC', '-Dmodernization.tests=true',
                   '-Daestoragevault.quantityPreflight=true', '-Djava.awt.headless=true', '-Duser.language=en',
                   '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
        with (out/'server.log').open('w') as log:
            process = subprocess.run(command,cwd=target,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
        report = json.loads((target/'quantity-preflight.json').read_text())
        if process.returncode or not report['passed'] or report['client_launched']:
            raise SystemExit('Server preflight failed; inspect server.log')
        metadata['fixture_sha256'] = digest(args.fixture)
    (out/'metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')


if __name__ == '__main__': main()
