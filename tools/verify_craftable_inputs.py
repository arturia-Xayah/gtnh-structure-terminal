#!/usr/bin/env python3
"""Link the 0.1.5 development distribution to headless evidence. Never starts a game."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--jar', type=Path, required=True)
    ap.add_argument('--fixture', type=Path, required=True)
    ap.add_argument('--report', type=Path, required=True)
    ap.add_argument('--unit-results', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    a = ap.parse_args()
    checks = []

    def check(name, ok):
        checks.append({'check': name, 'passed': bool(ok)})
        if not ok:
            raise SystemExit('Failed: ' + name)

    with zipfile.ZipFile(a.jar) as jar, zipfile.ZipFile(a.fixture) as fixture:
        classes = [n for n in jar.namelist() if n.endswith('.class')]
        check('no_test_fixture_in_distribution', not any('/packtest/' in n or '/servertest/' in n for n in classes))
        check('java_8_bytecode', all(int.from_bytes(jar.read(n)[6:8], 'big') <= 52 for n in classes))
        check('all_production_classes_match_tested_fixture', all(n in fixture.namelist() and jar.read(n) == fixture.read(n) for n in classes))
        required = ['dev/structureterminal/ae/CraftableInputs.class',
                    'dev/structureterminal/client/ae/CraftablePatternSlot.class',
                    'dev/aeoptimizer/mixin/client/PatternInputMixin.class',
                    'dev/aeoptimizer/mixin/client/PatternTooltipMixin.class',
                    'dev/aeoptimizer/mixin/client/TerminalRepoAccessor.class',
                    'dev/aeoptimizer/mixin/client/ItemRepoAccessor.class']
        check('badge_classes_packaged', all(n in classes for n in required))
        for lang in ('zh_CN', 'en_US'):
            check('tooltip_translation_' + lang, b'structureterminal.ae.craftable_input=' in jar.read('assets/structureterminal/lang/' + lang + '.lang'))
        slot = jar.read('dev/structureterminal/client/ae/CraftablePatternSlot.class')
        check('client_font_call_remapped', b'func_78261_a' in slot and b'field_71466_p' in slot)
        metadata = json.loads(jar.read('mcmod.info'))['modList']
        check('mod_ids_and_versions', {m['modid'] for m in metadata} == {'structureterminal', 'aeorderoptimizer'} and all(m['version'] == '0.1.5-alpha' for m in metadata))

    report = json.loads(a.report.read_text())
    cases = report['cases']
    check('headless_suite_completed', report.get('completed') and not report.get('fatal_error') and not report.get('client_launched', True))
    check('all_headless_cases_passed', bool(cases) and all(c.get('passed') is True for c in cases))
    badges = [c for c in cases if c['case'].startswith('badge_')]
    check('18_native_badge_cases_passed', len(badges) == 18)
    roots = [ET.parse(p).getroot() for p in a.unit_results.glob('TEST-*.xml')]
    check('unit_tests_passed', bool(roots) and all(int(r.get('failures', '0')) == 0 and int(r.get('errors', '0')) == 0 for r in roots))
    check('4_client_hook_checks_passed', any(r.get('name') == 'dev.structureterminal.ae.PatternInputHookTest' and int(r.get('tests', '0')) == 4 for r in roots))
    result = {
        'version': '0.1.5-alpha', 'target': 'GTNH-2.9.0-RC-2',
        'jar_sha256': sha(a.jar.read_bytes()), 'fixture_sha256': sha(a.fixture.read_bytes()),
        'production_classes': len(classes),
        'unit_tests': sum(int(r.get('tests', '0')) for r in roots),
        'headless_cases': len(cases), 'badge_cases': len(badges),
        'client_launched': False, 'client_mixin_execution_tested': False, 'visual_validation': False,
        'checks': checks,
    }
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
