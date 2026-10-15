#!/usr/bin/env python3
"""Summarize observed per-controller results without equating native completion with a formed machine."""
import argparse
from collections import Counter
import csv
import json
from pathlib import Path
import re
import zipfile


def clean(value):
    return re.sub(r"§.", "", str(value)).replace("\r", " ").replace("\n", " ")


def translations(jar):
    lookup = {}
    if not jar:
        return lookup
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        for english in names:
            if not english.endswith("/en_US.lang"):
                continue
            chinese = english.replace("/en_US.lang", "/zh_CN.lang")
            if chinese not in names:
                continue
            def parse(name):
                result = {}
                for line in archive.read(name).decode("utf-8-sig", errors="replace").splitlines():
                    if "=" in line and not line.lstrip().startswith("#"):
                        key, value = line.split("=", 1)
                        result[key.strip()] = value.strip()
                return result
            en, zh = parse(english), parse(chinese)
            for key, value in en.items():
                if key in zh:
                    lookup[clean(value)] = clean(zh[key])
    return lookup


def classify(row):
    phases = row.get("phases", {})
    if row.get("harness_error"):
        return "TEST_ERROR"
    if row.get("scan_status") == "NO_STRUCTURELIB_INTERFACE":
        return "NO_STRUCTURELIB_INTERFACE"
    if row.get("scan_status") != "PASS":
        message = row.get("scan_error", "")
        return "SCAN_ENVIRONMENT_OR_LIMIT" if "unloaded" in message or "exceeded" in message else "SCAN_ERROR"
    if row.get("idle_scan_state_preserved") is False:
        return "IDLE_SCAN_STATE_CHANGED"
    if row.get("build_status") == "NO_SURVIVAL_INTERFACE":
        return "NO_NATIVE_SURVIVAL_BUILDER"
    if row.get("build_status") != "NATIVE_COMPLETE":
        outcome = phases.get("baseline_build", {}).get("outcome", "")
        return "BUILD_TIME_LIMIT" if "TIMEOUT" in outcome else "BUILD_BLOCKED"
    formed = row.get("formed_after_build")
    if formed is not True:
        return "BUILT_NOT_FORMED" if formed is False else "FORMATION_NOT_ASSESSED"
    for name in ("partial_rebuild", "replace"):
        stage = phases.get(name)
        if stage:
            if not stage.get("outcome", "").endswith(".complete"):
                return name.upper() + "_BLOCKED"
            if stage.get("formed_after_operation") is False:
                return "POST_OPERATION_NOT_FORMED"
    if any(not stage.get("material_accounting_balanced", False) for stage in phases.values()):
        return "MATERIAL_ACCOUNTING_REVIEW"
    if phases.get("demolish", {}).get("outcome") != "structureterminal.demolished":
        return "DEMOLITION_BLOCKED"
    if row.get("remaining_ordinary_blocks", 0):
        return "DEMOLITION_REMAINDER"
    if not all(stage.get("controller_preserved", False) for stage in phases.values()):
        return "CONTROLLER_CHECK_FAILED"
    return "OPERATION_CHECKS_PASS"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report_directory", type=Path)
    parser.add_argument("output_directory", type=Path)
    parser.add_argument("--gt-jar", type=Path)
    parser.add_argument("--profile", choices=("baseline", "configuration-retry"), default="baseline")
    args = parser.parse_args()
    progress = json.loads((args.report_directory / "progress.json").read_text())
    if not progress.get("finished"):
        raise SystemExit("Validation is still running; do not summarize a stale report.json.")
    report = json.loads((args.report_directory / "report.json").read_text())
    machines = report["machines"]
    if len(machines) != progress["total_multiblocks"] or len({r["meta_id"] for r in machines}) != len(machines):
        raise SystemExit("Incomplete or duplicate machine results")
    zh = translations(args.gt_jar)
    args.output_directory.mkdir(parents=True, exist_ok=True)
    fields = ["meta_id", "name_zh", "name", "class", "classification", "scan_status", "cells", "unknown_cells",
              "baseline_build", "formed_after_build", "partial_rebuild", "formed_after_partial", "replace_status",
              "replacement_channel", "formed_after_replace", "demolish_status", "remaining_ordinary_blocks",
              "idle_scan_state_preserved", "all_material_accounts_balanced", "energy_tier_override",
              "native_hatch_channel_explicit", "explicit_hatch_choice", "notes"]
    rows = []
    for machine in machines:
        stages = machine.get("phases", {})
        errors = [machine.get("scan_error", ""), machine.get("harness_error", "")]
        if machine.get("structure_errors"):
            errors.append(json.dumps(machine["structure_errors"], ensure_ascii=False))
        for name, stage in stages.items():
            if not stage.get("outcome", "").endswith((".complete", ".demolished")):
                errors.extend(stage.get("messages", [])[:4])
            if stage.get("material_delta"):
                errors.append(name + " material_delta=" + json.dumps(stage["material_delta"], ensure_ascii=False))
        rows.append({
            "meta_id": machine["meta_id"], "name_zh": zh.get(clean(machine.get("name", "")), ""),
            "name": machine.get("name", ""), "class": machine["class"], "classification": classify(machine),
            "scan_status": machine.get("scan_status", "NOT_REACHED"), "cells": machine.get("cells", ""),
            "unknown_cells": machine.get("unknown_cells", ""),
            "baseline_build": stages.get("baseline_build", {}).get("outcome", "NOT_RUN"),
            "formed_after_build": machine.get("formed_after_build", "NOT_RUN"),
            "partial_rebuild": stages.get("partial_rebuild", {}).get("outcome", "NOT_RUN"),
            "formed_after_partial": stages.get("partial_rebuild", {}).get("formed_after_operation", "NOT_RUN"),
            "replace_status": machine.get("replace_status", "NOT_RUN"),
            "replacement_channel": machine.get("replacement_channel", ""),
            "formed_after_replace": stages.get("replace", {}).get("formed_after_operation", "NOT_RUN"),
            "demolish_status": machine.get("demolish_status", "NOT_RUN"),
            "remaining_ordinary_blocks": machine.get("remaining_ordinary_blocks", "NOT_RUN"),
            "idle_scan_state_preserved": machine.get("idle_scan_state_preserved", "NOT_RUN"),
            "all_material_accounts_balanced": bool(stages) and all(s.get("material_accounting_balanced", False) for s in stages.values()),
            "energy_tier_override": machine.get("energy_tier_override", "NOT_SET"),
            "native_hatch_channel_explicit": machine.get("native_hatch_channel_explicit", "BASELINE_GT_HATCH_1"),
            "explicit_hatch_choice": machine.get("explicit_hatch_choice", "NOT_SET"),
            "notes": clean("; ".join(str(e) for e in errors if e)),
        })
    with (args.output_directory / "machines.csv").open("w", encoding="utf-8-sig", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    counts = Counter(row["classification"] for row in rows)
    summary = {"pack": report.get("pack"), "profile": args.profile, "controllers_attempted": len(rows), "classification_counts": dict(counts),
               "scan_success": sum(m.get("scan_status") == "PASS" for m in machines),
               "native_build_complete": sum(m.get("build_status") == "NATIVE_COMPLETE" for m in machines),
               "formed_after_build": sum(m.get("formed_after_build") is True for m in machines),
               "replacement_native_complete": sum(m.get("replace_status") == "NATIVE_COMPLETE" for m in machines),
               "idle_scan_state_preserved": sum(m.get("idle_scan_state_preserved") is True for m in machines),
               "idle_scan_state_changed": sum(m.get("idle_scan_state_preserved") is False for m in machines),
               "phase_attempts": dict(Counter(k for m in machines for k in m.get("phases", {}))),
               "machines_with_any_material_delta": sum(any(s.get("material_delta") for s in m.get("phases", {}).values()) for m in machines),
               "scans_over_default_500ms": sum(m.get("scan_ms", 0) > 500 for m in machines),
               "scans_with_unknown_cells": sum(m.get("unknown_cells", 0) > 0 for m in machines),
               "running_recipe_tested": False, "gui_tested": False}
    (args.output_directory / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    profile = "启用 gt_hatch=1，使用自动选择的基础仓室。" if args.profile == "baseline" else "按 retry-plan.json 指定能源仓等级，通过显式选择仓室自动启用原生仓室规则；非 GT TileEntity 已纳入材料账。此复测不覆盖基线。"
    title = "逐机后台验证" if args.profile == "baseline" else "配置复测"
    text = ["# GTNH 2.9.0-RC-2 " + title, "", f"实际尝试 **{len(rows)} 个 GT 注册表多方块控制器条目**。同一机器的不同注册 ID 分别记录。",
            "", "完整整包、真实方块和生存库存操作；默认尺寸 / 模式，" + profile + "每阶段有 240 tick 时间预算。",
            "", "这不涵盖每台机器的所有尺寸、仓室等级、附属模块或运行配方；非 GT 注册表控制器不在本轮清单内。",
            "", "原生搭建器报告完成、实际机器成型、材料账平衡分别统计；没有把缺料、超时或环境限制算作通过。", "",
            "| 分类 | 数量 |", "|---|---:|"]
    text += [f"| {key} | {count} |" for key, count in sorted(counts.items())]
    text += ["", "`OPERATION_CHECKS_PASS` 只表示本轮配置的结构操作检查通过，不表示机器已经通电运行配方或 GUI 已验收。",
             "`BUILT_NOT_FORMED` 需要核对仓室电压等级、催化物、控制器物品或机器专用配置。`MATERIAL_ACCOUNTING_REVIEW` 保留逐物品差额，其中可能包含流体流动 / 元数据转换，不能直接解释为复制或丢失漏洞。",
             "", "逐台明细见 `machines.csv`，原始阶段结果见 `report.json`。", "", "| ID | 机器 | 分类 |", "|---:|---|---|"]
    text += [f"| {r['meta_id']} | {(r['name_zh'] or r['name']).replace('|', '/')} | {r['classification']} |" for r in rows]
    (args.output_directory / "README.md").write_text("\n".join(text) + "\n")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
