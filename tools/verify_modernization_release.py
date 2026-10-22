#!/usr/bin/env python3
"""Verify the merged artifact, document links and evidence without starting Minecraft."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

from summarize_pack_validation import classify

ROOT=Path(__file__).resolve().parents[1]
MODULES={'structureterminal','aeorderoptimizer','plasmaforgepower','aestoragevault'}
ICON='assets/structureterminal/textures/items/structure_terminal.png'

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--version',default='0.2.1-alpha');ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--fixtures',type=Path,nargs='+',required=True)
    ap.add_argument('--runs',type=Path,nargs='+',required=True);ap.add_argument('--output',type=Path,required=True)
    a=ap.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);checks=[]
    def check(name,value):
        checks.append(dict(check=name,passed=bool(value)))
        if not value:raise SystemExit('Failed: '+name)
    with zipfile.ZipFile(a.jar) as z:
        names=z.namelist();classes=[n for n in names if n.endswith('.class')]
        check('unique_jar_entries',len(names)==len(set(names)))
        check('java_8_bytecode',bool(classes) and all(int.from_bytes(z.read(n)[6:8],'big')==52 for n in classes))
        check('no_test_or_dependency_classes',not any('/packtest/' in n or '/servertest/' in n or '/qa/' in n or n.startswith(('appeng/','gregtech/','org/spongepowered/','it/unimi/')) for n in classes))
        mods=json.loads(z.read('mcmod.info'))['modList'];version={m['version'] for m in mods}
        check('preserved_four_module_ids',{m['modid'] for m in mods}==MODULES)
        check('unified_name',all(m['name'].startswith('GTNH现代化升级') for m in mods))
        check('shared_version',version=={a.version})
        check('child_metadata',all(m['parent']==('' if m['modid']=='structureterminal' else 'structureterminal') for m in mods))
        check('terminal_icon_for_all_modules',all(m['logoFile']==ICON for m in mods) and z.read(ICON)==(ROOT/'src/main/resources'/ICON).read_bytes())
        baseline=json.loads((ROOT/'verification/texture-0.1.3.json').read_text())
        check('approved_terminal_icon_unchanged',hashlib.sha256(z.read(ICON)).hexdigest()==baseline['sha256'])
        check('terminal_core_plugin',b'FMLCorePlugin: dev.structureterminal.coremod.TerminalLoadingPlugin' in z.read('META-INF/MANIFEST.MF'))
        check('independent_mixin_loaders',all(n in names for n in ['dev/aeoptimizer/LateMixins.class','dev/plasmaforgepower/LateMixins.class','mixins.aeorderoptimizer.late.json','mixins.plasmaforgepower.late.json']))
        if a.version != '0.2.0-alpha':
            check('vault_skin_mixin_loader',all(n in names for n in ['dev/aestoragevault/LateMixins.class','mixins.aestoragevault.late.json','dev/aestoragevault/world/VaultStructure.class','dev/aestoragevault/mixin/VaultHatchSkinMixin.class','dev/aestoragevault/client/VaultBlockRenderer.class']))
            for name in ['controller','access']:
                image=z.read('assets/aestoragevault/textures/blocks/'+name+'_overlay.png')
                check(name+'_64px_alpha_texture',image[:8]==b'\x89PNG\r\n\x1a\n' and int.from_bytes(image[16:20],'big')==64 and int.from_bytes(image[20:24],'big')==64 and image[25]==6)
        for language in ['zh_CN','en_US']:
            for namespace in ['structureterminal','aeorderoptimizer','plasmaforgepower','aestoragevault']:
                check('language_'+namespace+'_'+language,'assets/'+namespace+'/lang/'+language+'.lang' in names)
        for fixture in a.fixtures:
            with zipfile.ZipFile(fixture) as test:
                check('production_bytecode_matches_'+fixture.name,all(n in test.namelist() and z.read(n)==test.read(n) for n in classes))
        class_hashes={n:hashlib.sha256(z.read(n)).hexdigest() for n in classes}
    tests=[]
    unit_dir=out/'unit-tests';unit_dir.mkdir(exist_ok=True)
    for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        tests.append(ET.parse(p).getroot().attrib);shutil.copy2(p,unit_dir/p.name)
    check('all_unit_tests_pass',bool(tests) and all(int(t[k])==0 for t in tests for k in ['failures','errors','skipped']))
    runs=[]
    for folder in a.runs:
        summary=json.loads((folder/'summary.json').read_text())
        check('runner_'+folder.name,summary['passed'] and not summary['client_launched'])
        for row in summary['runs']:
            check(row['suite']+'_exit_'+str(row['stage']),row['passed'] and row['exit']==0)
            source=folder/row['report'];data=json.loads(source.read_text());suite=row['suite']
            if suite=='ae': check('all_ae_cases',data['completed'] and all(x['passed'] for x in data['cases']) and not data['client_launched'])
            elif suite=='structures':
                check('structure_operations',bool(data['machines']) and all(classify(m)=='OPERATION_CHECKS_PASS' for m in data['machines']))
                row['phases']=sum(len(m['phases']) for m in data['machines'])
            else:check(suite+'_all_cases_'+str(row['stage']),data['passed'] and bool(data['tests']) and all(x['passed'] for x in data['tests']) and not data['client_launched'])
            target=out/row['report'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
            module_file=folder/suite/'modernization-modules.json';modules=json.loads(module_file.read_text())
            check(suite+'_live_namespaces_'+str(row['stage']),modules['passed'] and set(modules['modules'])==MODULES)
            shutil.copy2(module_file,out/suite/'modernization-modules.json')
            excerpt=[]
            for line in (folder/suite/f"server-{row['stage']}.log").read_text(errors='replace').splitlines():
                if any(label in line for label in ['VAULT_TEST','FORGE_POWER_TEST','FUSION_POWER_TEST','AE_OPTIMIZER_TEST','PACK_TEST']): excerpt.append(line)
            (out/suite/f"server-{row['stage']}-excerpt.log").write_text('\n'.join(excerpt)+'\n')
            runs.append(row)
    check('every_feature_suite_exercised',{r['suite'] for r in runs}=={'vault','forge','fusion','ae','structures'})
    check('vault_cross_process_restart',any(r['suite']=='vault' and r['stage']==2 for r in runs))
    doc_errors=[]
    for target in [ROOT/'README.md',ROOT/'README.en.md',ROOT/'docs/development',ROOT/'docs/features']:
        for p in ([target] if target.is_file() else target.rglob('*.md')):
            for link in re.findall(r'\]\(([^\s)]+)\)',p.read_text()):
                if '://' in link or link.startswith(('#','mailto:')):continue
                path=link.split('#')[0]
                if path and not (p.parent/path).exists():doc_errors.append((str(p.relative_to(ROOT)),link))
    check('player_and_developer_document_links',not doc_errors)
    manifest=dict(passed=True,checks=checks,version=a.version,target='GTNH 2.9.0-RC-2',client_launched=False,
                  artifact=a.jar.name,sha256=sha(a.jar),classes=len(class_hashes),class_sha256=class_hashes,
                  unit_tests=sum(int(t['tests']) for t in tests),runs=runs,fixtures={p.name:sha(p) for p in a.fixtures})
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in manifest.items() if k not in ['checks','class_sha256']},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
