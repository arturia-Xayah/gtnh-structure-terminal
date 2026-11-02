#!/usr/bin/env python3
"""Bind single-vault performance and focused backend evidence to the installable production bytecode."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','matrix','lookup','regression','output']:
        ap.add_argument('--'+name,type=Path,required=True)
    a=ap.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);checks={}
    def check(name,condition):
        checks[name]=bool(condition)
        if not condition:raise SystemExit('Failed: '+name)
    fixtures={'matrix':a.matrix/'fixture.jar','lookup_and_regression':a.lookup/'fixture.jar'}
    with zipfile.ZipFile(a.jar) as prod:
        names=prod.namelist();classes=[n for n in names if n.endswith('.class')]
        check('unique_entries',len(names)==len(set(names)))
        check('no_fixture_or_bundled_dependency_classes',not any('/packtest/' in n or '/servertest/' in n or '/qa/' in n or n.startswith(('appeng/','gregtech/')) for n in classes))
        check('java8_bytecode',bool(classes) and all(int.from_bytes(prod.read(n)[6:8],'big')==52 for n in classes))
        for label,path in fixtures.items():
            with zipfile.ZipFile(path) as fixture:
                check('byte_identical_'+label,all(n in fixture.namelist() and prod.read(n)==fixture.read(n) for n in classes))
        mods=json.loads(prod.read('mcmod.info'))['modList']
        check('four_module_ids_preserved',{m['modid'] for m in mods}=={'structureterminal','aeorderoptimizer','plasmaforgepower','aestoragevault'})
        check('local_development_version',{m['version'] for m in mods}=={'0.3.0-alpha'})
        class_hashes={n:hashlib.sha256(prod.read(n)).hexdigest() for n in classes}
        old=ROOT/'dist/0.3.0-alpha-GTNH-2.9.0/gtnh-modernization-upgrade-0.3.0-alpha-GTNH-2.9.0.jar'
        with zipfile.ZipFile(old) as initial:
            changed=[n for n in classes if n not in initial.namelist() or prod.read(n)!=initial.read(n)]
            check('only_storage_classes_changed',all(n.startswith('dev/aestoragevault/') for n in changed))
    frozen=out/'baseline-sources'
    for name in ['VaultInventory','ResourceCodec']:
        text=(ROOT/f'src/vaultPackTest/java/dev/aestoragevault/packtest/read/Baseline{name}.java').read_text()
        normalized=text.replace('package dev.aestoragevault.packtest.read;','package dev.aestoragevault.ae;').replace('BaselineVaultInventory','VaultInventory').replace('BaselineResourceCodec','ResourceCodec')
        check('frozen_'+name,normalized==(frozen/(name+'.java')).read_text())
    matrix=json.loads((a.matrix/'summary.json').read_text())
    check('matrix_fresh_processes',matrix['passed'] and len(matrix['runs'])==3 and not matrix['client_launched'] and matrix['fixture_sha256']==sha(fixtures['matrix']))
    valid_ticks=0
    for run in matrix['runs']:
        source=a.matrix/run['report'];d=json.loads(source.read_text())
        check('matrix_round_'+str(run['trial']),run['exit']==0 and d['passed'] and not d['client_launched'] and len(d['rows'])==8 and all(c['passed'] for c in d['checks']))
        for row in d['rows']:
            raw=row['raw'];count=row['measured_ticks'];valid_ticks+=count
            check(f"sample_counts_{run['trial']}_{row['phase']}",count==120 and all(len(v)==120 for v in raw.values()))
            check(f"whole_tick_clock_{run['trial']}_{row['phase']}",all(t>=w for t,w in zip(raw['whole_tick_ns'],raw['work_ns'])))
        shutil.copy2(source,out/run['report'])
    check('valid_tick_total',valid_ticks==2880)
    lookup=json.loads((a.lookup/'summary.json').read_text());paired=json.loads((a.lookup/'paired.json').read_text())
    check('paired_queries',lookup['passed'] and lookup['exit']==0 and paired['passed'] and not paired['client_launched'] and len(paired['paired_queries'])==4 and lookup['fixture_sha256']==sha(fixtures['lookup_and_regression']))
    check('paired_sample_counts',all(r['samples']==120 and r['batch_size']==128 and all(len(v)==120 for v in r['raw'].values()) for r in paired['paired_queries']))
    shutil.copy2(a.lookup/'paired.json',out/'paired-query.json');shutil.copy2(a.lookup/'summary.json',out/'paired-summary.json')
    regression=json.loads((a.regression/'summary.json').read_text())
    check('focused_regression',regression['passed'] and not regression['client_launched'] and regression['fixture_sha256']==sha(fixtures['lookup_and_regression']) and regression['pack_version']=='2.9.0' and len(regression['runs'])==6)
    check('all_three_storage_restarts',{(r['suite'],r['stage']) for r in regression['runs']}=={(s,n) for s in ['vault','vault_io','big_quantity'] for n in [1,2]})
    cases=0
    expected={'gregtech':'5.09.54.265','appliedenergistics2':'rv3-beta-1095-GTNH','structurelib':'1.4.47','modularui2':'2.3.94-1.7.10','gtnhlib':'0.11.54','NotEnoughItems':'2.8.171-GTNH'}
    for run in regression['runs']:
        source=a.regression/run['report'];d=json.loads(source.read_text());cases+=len(d['tests'])
        check(run['suite']+'_stage_'+str(run['stage']),run['passed'] and run['exit']==0 and d['passed'] and not d['client_launched'] and all(t['passed'] for t in d['tests']))
        target=out/'backend'/run['report'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        module=a.regression/run['suite']/'modernization-modules.json';m=json.loads(module.read_text())
        check('official_dependencies_'+run['suite']+'_'+str(run['stage']),m['passed'] and m['dependencies']==expected and m['pack']=='2.9.0')
        shutil.copy2(module,target.parent/'modernization-modules.json')
    check('66_storage_checks',cases==66)
    shutil.copy2(a.regression/'summary.json',out/'backend/summary.json')
    unit=out/'unit-tests';unit.mkdir(exist_ok=True);total=0
    for path in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        r=ET.parse(path).getroot();total+=int(r.get('tests',0));check(path.name,all(int(r.get(k,0))==0 for k in ['errors','failures','skipped']));shutil.copy2(path,unit/path.name)
    check('102_unit_tests',total==102)
    source_dir=out/'optimized-sources';source_dir.mkdir(exist_ok=True)
    sources={}
    for relative in ['ae/VaultInventory.java','ae/NativeResourceCache.java','core/VaultStore.java','core/NativeInventoryView.java','world/TileAccess.java']:
        path=ROOT/'src/main/java/dev/aestoragevault'/relative
        sources[str(path.relative_to(ROOT))]=sha(path)
        dest=source_dir/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
    doc_errors=[]
    for p in [ROOT/'README.md',ROOT/'README.en.md',*(ROOT/'docs/development').glob('*.md'),*(ROOT/'docs/features').glob('*.md')]:
        for link in re.findall(r'\]\(([^\s)]+)\)',p.read_text()):
            if '://' in link or link.startswith(('#','mailto:')):continue
            path=link.split('#')[0];resolved=(p.parent/path).resolve()
            if path and not resolved.exists() and resolved!=out/'manifest.json':doc_errors.append((str(p),link))
    check('documentation_links',not doc_errors)
    manifest=dict(passed=True,client_launched=False,scope='single-vault reads; full stock, AE IO and restarts; other feature bytecode unchanged from initial local 0.3.0',
        artifact=str(a.jar.resolve()),sha256=sha(a.jar),classes=len(classes),class_sha256=class_hashes,changed_classes=changed,
        unit_tests=total,backend_checks=cases,valid_performance_ticks=valid_ticks,paired_query_cases=4,
        fixtures={k:sha(v) for k,v in fixtures.items()},sources=sources,checks=checks)
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in manifest.items() if k not in ['class_sha256','sources','checks']},ensure_ascii=False,indent=2))


if __name__=='__main__':main()
