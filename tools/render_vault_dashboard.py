#!/usr/bin/env python3
"""Render the production dashboard mesh without loading or launching Minecraft."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--java', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--language', choices=['zh_CN', 'en_US'], default='zh_CN')
    a = ap.parse_args()
    java = a.java.resolve()
    with tempfile.TemporaryDirectory(prefix='vault-dashboard-') as work:
        sources = list((ROOT/'src/main/java/dev/aestoragevault/ui/dashboard').glob('*.java'))
        sources += [ROOT/'src/main/java/dev/aestoragevault/core/ByteFormat.java', ROOT/'tools/ui-preview/VaultDashboardPreview.java']
        subprocess.run([str(java.with_name('javac')), '-encoding', 'UTF-8', '-d', work, *map(str,sources)], check=True)
        command = [str(java), '-Djava.awt.headless=true', '-cp', work, 'VaultDashboardPreview', str(a.output.resolve()),
                   str(ROOT/'src/main/resources/assets/aestoragevault/lang'/f'{a.language}.lang')]
        if a.language == 'en_US': command.append('en')
        subprocess.run(command, check=True)


if __name__ == '__main__': main()
