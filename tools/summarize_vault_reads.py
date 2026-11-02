#!/usr/bin/env python3
"""Summarize valid read-comparison reports without dropping cold-cache or regression measurements."""
import argparse
import csv
import json
from pathlib import Path
import statistics

METRICS = {
    'stable_enumeration_ns': (1e6, 'ms'),
    'changed_enumeration_ns': (1e6, 'ms'),
    'point_hot_batch_ns': (128e3, 'us/query_batch_average'),
    'point_stream_batch_ns': (128e3, 'us/query_batch_average'),
    'stable_enumeration_alloc': (1e6, 'MB'),
    'changed_enumeration_alloc': (1e6, 'MB'),
    'journal_changes_ns': (1e6, 'ms/16_changes'),
    'work_ns': (1e6, 'ms'),
    'whole_tick_ns': (1e6, 'ms'),
    'work_alloc': (1e6, 'MB'),
}
COLD = {'cold_enumeration_ns': (1e6,'ms'), 'cold_enumeration_alloc': (1e6,'MB'),
        'cold_point_ns_per_query': (1e3,'us'), 'retained_heap_delta_estimate': (1e6,'MB_estimate')}


def summarize(source, output):
    output.mkdir(parents=True,exist_ok=True)
    summary=json.loads((source/'summary.json').read_text())
    assert summary['passed'] and not summary['client_launched']
    reports=[json.loads((source/run['report']).read_text()) for run in summary['runs']]
    assert all(r['passed'] and not r['client_launched'] for r in reports)
    groups={}
    for report in reports:
        assert len(report['rows'])==8
        for row in report['rows']:
            assert row['measured_ticks']==120
            groups.setdefault((row['case'],row['variant']),[]).append(row)
    rows=[]
    for (case,variant),values in groups.items():
        assert len(values)==len(reports)
        for metric,(divisor,unit) in METRICS.items():
            p50=[v['metrics'][metric]['p50']/divisor for v in values]
            rows.append(dict(case=case,variant=variant,metric=metric,unit=unit,
                             p50_median=statistics.median(p50),p50_min=min(p50),p50_max=max(p50),
                             p95_max=max(v['metrics'][metric]['p95']/divisor for v in values),
                             maximum=max(v['metrics'][metric]['max']/divisor for v in values)))
        for metric,(divisor,unit) in COLD.items():
            estimates=[v[metric]/divisor for v in values]
            rows.append(dict(case=case,variant=variant,metric=metric,unit=unit,
                             p50_median=statistics.median(estimates),p50_min=min(estimates),p50_max=max(estimates),
                             p95_max='',maximum=''))
    data=dict(trials=len(reports),measured_ticks=sum(r['measured_ticks'] for report in reports for r in report['rows']),
              aggregation='median of independent-process phase medians; p95_max is worst phase p95; lookup values are 128-query batch averages, not individual-query latency percentiles',
              fixture_sha256=summary['fixture_sha256'],rows=rows)
    (output/'aggregate.json').write_text(json.dumps(data,indent=2)+'\n')
    with (output/'comparison.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    def value(case,variant,metric):
        return next(r['p50_median'] for r in rows if r['case']==case and r['variant']==variant and r['metric']==metric)
    lines=['# 单聚合器对比汇总','',f'{len(reports)} 个独立后台服务器进程，{data["measured_ticks"]} 个有效 tick。各值取各进程阶段中位数的中位值；保留 p95/max 和每轮原始数据。','',
           '| 数据集 | 原枚举 ms | 优化枚举 ms | 加速倍数 | 变化后原枚举 ms | 变化后优化 ms |','|---|---:|---:|---:|---:|---:|']
    for case in dict.fromkeys(r['case'] for r in rows):
        old=value(case,'baseline','stable_enumeration_ns');new=value(case,'optimized','stable_enumeration_ns')
        lines.append(f'| {case} | {old:.3f} | {new:.3f} | {old/new:.1f}× | {value(case,"baseline","changed_enumeration_ns"):.3f} | {value(case,"optimized","changed_enumeration_ns"):.3f} |')
    lines+=['','| 数据集 | 原常用查询 μs | 优化常用 μs | 原轮询 μs | 优化轮询 μs |','|---|---:|---:|---:|---:|']
    for case in dict.fromkeys(r['case'] for r in rows):
        lines.append('| '+case+' | '+' | '.join(f'{value(case,v,m):.3f}' for m in ['point_hot_batch_ns','point_stream_batch_ns'] for v in ['baseline','optimized'])+' |')
    lines+=['','查询值为 128 次调用批量耗时除以 128，包含结构可用性检查；并非单次查询的 p95。轮询工作集超过 4096 条缓存时允许失效与重新编码。','',
            '| 数据集 | 原首次枚举 ms | 优化首次枚举 ms | 原每次枚举分配 MB | 优化分配 MB | 优化缓存保留堆估计 MB |','|---|---:|---:|---:|---:|---:|']
    for case in dict.fromkeys(r['case'] for r in rows):
        vals=[value(case,'baseline','cold_enumeration_ns'),value(case,'optimized','cold_enumeration_ns'),value(case,'baseline','stable_enumeration_alloc'),value(case,'optimized','stable_enumeration_alloc'),value(case,'optimized','retained_heap_delta_estimate')]
        lines.append('| '+case+' | '+' | '.join(f'{v:.3f}' for v in vals)+' |')
    lines+=['','首次枚举为原生类型已预热、聚合器缓存清空；保留堆估计受全服务器 GC/JIT 影响，不是严格对象大小或内存上限。两通道都创建全新原生 AE 列表，原版适配器每通道分别复制全库表。','']
    (output/'summary.md').write_text('\n'.join(lines))
    print('\n'.join(lines))


if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--source',type=Path,required=True);ap.add_argument('--output',type=Path,required=True)
    args=ap.parse_args();summarize(args.source,args.output)
