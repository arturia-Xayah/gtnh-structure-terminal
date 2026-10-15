#!/usr/bin/env python3
"""Keep baseline and configuration retries separate while showing distinct controller coverage."""
import argparse
import csv
import json
from pathlib import Path


def read(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('verification_directory', type=Path)
    args = parser.parse_args()
    root = args.verification_directory
    baseline = read(root / 'full-pack/machines.csv')
    retries = {row['meta_id']: row for row in read(root / 'configuration-retry/machines.csv')}
    assert len(baseline) == len({row['meta_id'] for row in baseline})
    assert set(retries).issubset({row['meta_id'] for row in baseline})
    result = []
    for row in baseline:
        retry = retries.get(row['meta_id'], {})
        result.append({
            'meta_id': row['meta_id'], 'name_zh': row['name_zh'], 'name': row['name'],
            'baseline': row['classification'], 'retry': retry.get('classification', 'NOT_RETESTED'),
            'any_tested_configuration_passed': 'OPERATION_CHECKS_PASS' in (row['classification'], retry.get('classification')),
            'retry_energy_tier': retry.get('energy_tier_override', ''),
            'retry_explicit_hatch_choice': retry.get('explicit_hatch_choice', ''),
            'baseline_notes': row['notes'], 'retry_notes': retry.get('notes', ''),
        })
    with (root / 'machine-coverage.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(result[0]))
        writer.writeheader()
        writer.writerows(result)
    summary = {'distinct_controllers': len(result), 'retry_trials': len(retries),
               'baseline_passed': sum(r['baseline'] == 'OPERATION_CHECKS_PASS' for r in result),
               'additional_controllers_passed_in_retry': sum(r['baseline'] != 'OPERATION_CHECKS_PASS' and r['retry'] == 'OPERATION_CHECKS_PASS' for r in result),
               'at_least_one_tested_configuration_passed': sum(r['any_tested_configuration_passed'] for r in result),
               'note': 'Different configurations remain separate; this is not an all-machines compatibility certificate.'}
    (root / 'coverage-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
