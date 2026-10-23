#!/usr/bin/env python3
"""Compare isolated ledger arithmetic and WAL costs, without starting Minecraft."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CORE = Path('src/main/java/dev/aestoragevault/core')


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--java', type=Path, required=True)
    ap.add_argument('--baseline-ref', default='bf87456570ffc208be29386b5a7fab4ff7a07024')
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    java = args.java.resolve()
    results = []
    with tempfile.TemporaryDirectory(prefix='vault-capacity-') as directory:
        work = Path(directory)
        for variant in ('baseline', 'current'):
            folder = work / variant
            source = folder / CORE
            source.mkdir(parents=True)
            names = ['Resource.java', 'VaultStore.java']
            if variant == 'current': names.append('CapacityValue.java')
            for name in names:
                path = CORE / name
                data = subprocess.check_output(['git', 'show', f'{args.baseline_ref}:{path}'], cwd=ROOT) if variant == 'baseline' else (ROOT / path).read_bytes()
                (source / name).write_bytes(data)
            classes = folder / 'classes'
            subprocess.run([str(java.with_name('javac')), '-encoding', 'UTF-8', '-d', str(classes),
                            *map(str, source.glob('*.java')), str(ROOT / 'tools/capacity_benchmark/CapacityBenchmark.java')], check=True)
            for bits in ([63] if variant == 'baseline' else [63, 201, 1025]):
                result = subprocess.check_output([str(java), '-Xms256m', '-Xmx256m', '-cp', str(classes),
                                                 'CapacityBenchmark', str(folder / str(bits)), str(bits)], text=True)
                row = dict(variant=variant, **json.loads(result))
                results.append(row)
                print(json.dumps(row), flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(baseline_ref=args.baseline_ref, scope='isolated ledger, median samples; not a TPS guarantee', results=results), indent=2) + '\n')


if __name__ == '__main__': main()
