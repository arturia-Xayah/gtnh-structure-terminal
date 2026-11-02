#!/usr/bin/env python3
"""Verify the configured-cache artifact and focused real-server evidence; no Minecraft launch."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','fixture','runs','output']:ap.add_argument('--'+name,type=Path,required=True)
    a=ap.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);checks={}
    def check(name,value):
        checks[name]=bool(value)
        if not value:raise SystemExit('Failed: '+name)
    with zipfile.ZipFile(a.jar) as prod,zipfile.ZipFile(a.fixture) as fixture:
        names=prod.namelist();classes=[n for n in names if n.endswith('.class')]
        check('unique_entries',len(names)==len(set(names)))
        check('java8_bytecode',bool(classes) and all(int.from_bytes(prod.read(n)[6:8],'big')==52 for n in classes))
        check('no_fixture_or_bundled_dependencies',not any('/packtest/' in n or '/servertest/' in n or '/qa/' in n or n.startswith(('appeng/','gregtech/')) for n in classes))
        check('byte_identical_to_executed_fixture',all(n in fixture.namelist() and prod.read(n)==fixture.read(n) for n in classes))
        check('weighted_cache_and_config_in_distribution',all(n in names for n in ['dev/aestoragevault/core/CachePool.class','dev/aestoragevault/config/VaultCacheConfig.class']))
        mods=json.loads(prod.read('mcmod.info'))['modList']
        check('module_ids_and_version',{m['modid'] for m in mods}=={'structureterminal','aeorderoptimizer','plasmaforgepower','aestoragevault'} and {m['version'] for m in mods}=={'0.3.0-alpha'})
        class_hashes={n:hashlib.sha256(prod.read(n)).hexdigest() for n in classes}
        original=ROOT/'dist/0.3.0-alpha-GTNH-2.9.0/read-optimized/gtnh-modernization-upgrade-0.3.0-alpha-GTNH-2.9.0.jar'
        with zipfile.ZipFile(original) as old:
            changed=[n for n in classes if n not in old.namelist() or prod.read(n)!=old.read(n)]
            check('only_storage_production_classes_changed',all(n.startswith('dev/aestoragevault/') for n in changed))
    summary=json.loads((a.runs/'summary.json').read_text())
    check('runner',summary['passed'] and not summary['client_launched'] and summary['pack_version']=='2.9.0' and summary['fixture_sha256']==sha(a.fixture))
    check('four_suites_and_restarts',{(r['suite'],r['stage']) for r in summary['runs']}=={(s,n) for s in ['cache_config','big_quantity','vault_io','vault'] for n in [1,2]})
    total=0
    expected={'gregtech':'5.09.54.265','appliedenergistics2':'rv3-beta-1095-GTNH','structurelib':'1.4.47','modularui2':'2.3.94-1.7.10','gtnhlib':'0.11.54','NotEnoughItems':'2.8.171-GTNH'}
    for run in summary['runs']:
        source=a.runs/run['report'];data=json.loads(source.read_text());total+=len(data['tests'])
        check(run['suite']+'_'+str(run['stage']),run['exit']==0 and run['passed'] and data['passed'] and not data['client_launched'] and all(t['passed'] for t in data['tests']))
        target=out/'backend'/run['report'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        module=a.runs/run['suite']/'modernization-modules.json';m=json.loads(module.read_text())
        check('runtime_dependencies_'+run['suite']+'_'+str(run['stage']),m['passed'] and m['dependencies']==expected and m['pack']=='2.9.0')
        shutil.copy2(module,target.parent/module.name)
    check('79_backend_checks',total==79)
    shutil.copy2(a.runs/'summary.json',out/'backend/summary.json')
    def config_values(path):return {k:int(v) for k,v in re.findall(r'^\s*S:(\w+)=([0-9]+)\s*$',path.read_text(),re.M)}
    example=ROOT/'examples/config/gtnh-modernization.cfg'
    defaults=config_values(example)
    check('documented_defaults_match_generated_file',defaults==config_values(a.runs/'big_quantity/config/gtnh-modernization.cfg') and len(defaults)==8)
    check('restart_loads_zero_limits',set(config_values(a.runs/'cache_config/config/gtnh-modernization.cfg').values())=={0})
    shutil.copy2(example,out/'default-gtnh-modernization.cfg')
    shutil.copy2(a.runs/'cache_config/config/gtnh-modernization.cfg',out/'tested-zero-gtnh-modernization.cfg')
    unit=out/'unit-tests';unit.mkdir(exist_ok=True);count=0
    for path in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        root=ET.parse(path).getroot();count+=int(root.get('tests',0));check(path.name,all(int(root.get(k,0))==0 for k in ['failures','errors','skipped']));shutil.copy2(path,unit/path.name)
    check('111_unit_tests',count==111)
    errors=[]
    for p in [ROOT/'README.md',ROOT/'README.en.md',*(ROOT/'docs/development').glob('*.md'),*(ROOT/'docs/features').glob('*.md')]:
        for link in re.findall(r'\]\(([^\s)]+)\)',p.read_text()):
            if '://' in link or link.startswith(('#','mailto:')):continue
            target=(p.parent/link.split('#')[0]).resolve()
            if not target.exists() and target!=out/'manifest.json':errors.append((str(p),link))
    check('documentation_links',not errors)
    sources={str(p.relative_to(ROOT)):sha(p) for p in (ROOT/'src/main/java/dev/aestoragevault').rglob('*.java')}
    manifest=dict(passed=True,client_launched=False,artifact=str(a.jar.resolve()),sha256=sha(a.jar),classes=len(classes),class_sha256=class_hashes,
        fixture_sha256=sha(a.fixture),unit_tests=count,backend_checks=total,checks=checks,defaults=defaults,changed_classes=changed,sources=sources,
        scope='configured weighted retention, actual small/zero startup settings and process restarts; no new performance matrix or client GUI acceptance')
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in manifest.items() if k not in ['class_sha256','sources','checks']},ensure_ascii=False,indent=2))


if __name__=='__main__':main()
