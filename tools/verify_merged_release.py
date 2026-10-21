#!/usr/bin/env python3
"""Offline release/report checks. Never starts Minecraft or accesses credentials."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'verification/merged-0.1.4'
VERSION='0.1.4-alpha'


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--ae-fixture',type=Path)
    ap.add_argument('--structure-fixture',type=Path)
    a=ap.parse_args();checks={}
    def check(k,v):checks[k]=bool(v)
    runtime=ROOT/'dist/GTNH-2.9.0-RC-2'/('structureterminal-'+VERSION+'-GTNH-2.9.0-RC-2.jar')
    ae=json.loads((OUT/'ae-report.json').read_text());cases={r['case']:r for r in ae['cases']}
    check('33_merged_ae_cases_pass',ae['completed'] and len(cases)==33 and all(r['passed'] for r in cases.values()))
    check('no_ae_client_launched',ae['client_launched'] is False)
    check('10000_three_batches_same_tick',cases['optimized_4']['batches']==[4096,4096,1808] and len(set(cases['optimized_4']['dispatch_world_ticks']))==1)
    check('million_input_output_conserved',cases['optimized_5']['input_items']==cases['optimized_5']['returned_items']==1000000)
    check('native_without_component_is_10000_unit_calls',cases['native_10000']['push_calls']==10000)
    for k in ('multiple_receivers_round_robin','cpu_dispatch_respects_non_opt_in_ordering_grid','cpu_dispatch_respects_opt_in_ordering_grid','gui_toggle_and_nbt','network_merge_disconnects_duplicate'):
        check(k,cases[k]['passed'])
    struct_report=json.loads((OUT/'structure-report.json').read_text())
    progress=json.loads((OUT/'structure-progress.json').read_text())
    summary=json.loads((OUT/'structure-summary.json').read_text())
    check('three_structure_samples_finished',struct_report['all_requested_machines_attempted'] and progress['finished'] and progress['completed']==3)
    check('three_structure_samples_pass',summary['classification_counts']=={'OPERATION_CHECKS_PASS':3})
    check('ten_structure_phases',sum(summary['phase_attempts'].values())==10)
    check('structure_materials_balanced',summary['machines_with_any_material_delta']==0)
    check('structure_state_preserved',summary['idle_scan_state_preserved']==3 and summary['idle_scan_state_changed']==0)
    check('scope_no_gui_or_running_recipe_claim',not struct_report['gui_tested'] and not struct_report['running_recipe_tested'])
    units=[ET.parse(p).getroot().attrib for p in (OUT/'unit-tests').glob('*.xml')]
    check('29_unit_tests_pass',sum(int(x['tests']) for x in units)==29 and all(int(x[k])==0 for x in units for k in ('failures','errors','skipped')))
    baseline=json.loads((OUT/'baseline-classes.json').read_text())
    with zipfile.ZipFile(runtime) as z:
        names=z.namelist();classes=[n for n in names if n.endswith('.class')]
        check('75_runtime_classes',len(classes)==75)
        check('java8_bytecode',all(struct.unpack('>H',z.read(n)[6:8])[0]==52 for n in classes))
        check('no_test_or_external_dependency_classes',not any('/packtest/' in n or '/qa/' in n or '/servertest/' in n or n.startswith(('appeng/','org/spongepowered/','it/unimi/')) for n in classes))
        check('unique_archive_entries',len(names)==len(set(names)))
        check('terminal_core_plugin_retained','FMLCorePlugin: dev.structureterminal.coremod.TerminalLoadingPlugin' in z.read('META-INF/MANIFEST.MF').decode())
        mods=json.loads(z.read('mcmod.info'))['modList']
        check('two_preserved_mod_ids_one_jar',{m['modid'] for m in mods}=={'structureterminal','aeorderoptimizer'})
        check('consistent_versions',all(m['version']==VERSION for m in mods))
        check('required_ae_resources',all(n in names for n in ('mixins.aeorderoptimizer.late.json','dev/aeoptimizer/LateMixins.class','assets/aeorderoptimizer/textures/blocks/core.png','NOTICE-AE-OPTIMIZER')))
        check('ae_and_terminal_are_remapped',b'func_74757_a' in z.read('dev/aeoptimizer/network/TileOptimizer.class') and b'func_77655_b' in z.read('dev/structureterminal/TerminalItem.class'))
        for module,expected in (('structures',45),('ae',27)):
            originals=baseline[module]['classes']
            check(module+'_logic_classes_unchanged',len(originals)==expected and all(hashlib.sha256(z.read(n)).hexdigest()==h for n,h in originals.items()))
        with zipfile.ZipFile(ROOT/'dist/structureterminal-0.1.3-alpha.jar') as old:
            icon='assets/structureterminal/textures/items/structure_terminal.png'
            check('approved_blue_white_texture_unchanged',z.read(icon)==old.read(icon))
        fixtures={}
        for label,path in (('ae',a.ae_fixture),('structure',a.structure_fixture)):
            if path:
                with zipfile.ZipFile(path) as test:
                    check(label+'_fixture_matches_all_75_runtime_classes',all(z.read(n)==test.read(n) for n in classes))
                fixtures[label]=sha(path)
                log=OUT/('ae-runner.log' if label=='ae' else 'structures-runner.log')
                check(label+'_actual_fixture_hash_recorded',fixtures[label] in log.read_text())
    check('distribution_sha256_recorded',sha(runtime) in (ROOT/'dist/SHA256SUMS').read_text())
    (OUT/'verification.json').touch(exist_ok=True)
    docs=['README.md','README.en.md','VERIFICATION.md','docs/AE_ORDER_OPTIMIZER.md','docs/AE_BENCHMARK.md','docs/BUILDING.md','verification/merged-0.1.4/README.md']
    for filename in docs:
        doc=ROOT/filename
        check(filename+':relative_links_exist',all('://' in link or (doc.parent/link.split('#')[0]).exists() for link in re.findall(r'\]\(([^)]+)\)',doc.read_text())))
    files=[p for base in (ROOT/'src',ROOT/'docs',OUT) for p in base.rglob('*') if p.is_file() and p!=OUT/'verification.json']
    files += [runtime,ROOT/'addon.gradle',ROOT/'dependencies.gradle',ROOT/'README.md',ROOT/'README.en.md',Path(__file__)]
    report=dict(all_checks_pass=all(checks.values()),checks=checks,runtime_sha256=sha(runtime),fixture_sha256=fixtures,fixture_bytecode_checked=bool(a.ae_fixture and a.structure_fixture),sha256={str(p.relative_to(ROOT)):sha(p) for p in sorted(files)})
    (OUT/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(f'{sum(checks.values())}/{len(checks)} checks passed; runtime SHA256 {sha(runtime)}')
    if not all(checks.values()):raise SystemExit('Failed: '+', '.join(k for k,v in checks.items() if not v))

if __name__=='__main__':main()
