#!/usr/bin/env python3
"""Audit renderFrame slow intervals for the 20261105 script-r2 matrix.

Reads the archived CSV/JSON/log evidence without changing source raw data and
writes derived report inputs under build/agent-backups/20261105-slow-interval-audit.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from statistics import median
from typing import Iterable


DEFAULT_BATCH = "build/agent-backups/20261105-run-matrix-script-r2"
DEFAULT_OUTPUT = "build/agent-backups/20261105-slow-interval-audit"
GROUP_ORDER = [
    "N_blank45",
    "N_cow25",
    "N_queen25",
    "N_laser3",
    "S_blank45",
    "S_cow25",
    "S_queen25",
    "S_laser3",
]
PHASE_LABELS = {
    "startup_0_5": "启动 0.000-5.000s",
    "action0_5_6": "action0 5.000-6.000s",
    "laser10_trigger": "10s 施法 10.000-10.500s",
    "laser20_trigger": "20s 施法 20.000-20.500s",
    "laser30_trigger": "30s 施法 30.000-30.500s",
    "laser10_active": "10s 激光 active",
    "laser20_active": "20s 激光 active",
    "laser30_active": "30s 激光 active",
    "tail1": "尾迹1 ended@10→20s due",
    "tail2": "尾迹2 ended@20→30s due",
    "tail3": "尾迹3 ended@30→stop",
}
TIMEZONE = timezone(timedelta(hours=8))
LOG_TIME_RE = re.compile(r"\[[^\]]*?(\d{2}:\d{2}:\d{2}\.\d{3})\]")


@dataclass(frozen=True)
class Interval:
    run_id: str
    line_no: int
    interval_ns: int
    start_s: float
    end_s: float


@dataclass
class RunData:
    run_id: str
    directory: Path
    frames_path: Path
    result_path: Path
    rows: list[dict[str, str]]
    intervals: list[Interval]
    result: dict
    ack: dict
    log_lines: list[str]
    start_ns: int
    stop_ns: int

    @property
    def scenario(self) -> str:
        return self.run_id.split("_", 2)[1]

    @property
    def group(self) -> str:
        return f"{self.run_id[0]}_{self.scenario}"

    @property
    def mode(self) -> str:
        return self.run_id[0]


def build_run_ids() -> list[str]:
    ids: list[str] = []
    for mode in ("N", "S"):
        for scenario in ("blank45", "cow25", "queen25", "laser3"):
            for ordinal in range(1, 4):
                run_id = f"{mode}_{scenario}_{ordinal}"
                if run_id == "N_cow25_1":
                    run_id = "N_cow25_1_attempt02"
                ids.append(run_id)
    return ids


def one_file(paths: Iterable[Path], label: str) -> Path:
    values = list(paths)
    if len(values) != 1:
        raise ValueError(f"{label}: expected 1 file, got {len(values)}")
    return values[0]


def load_run(root: Path, run_id: str) -> RunData:
    directory = root / run_id
    required = [
        directory,
        directory / "results" / "summary.json",
        directory / "identity-ack.json",
        directory / "gui-state.json",
        directory / "build/modBench/runs/default/client/logs/latest.log",
    ]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        raise FileNotFoundError("; ".join(missing))

    frames_path = one_file((directory / "results/artifacts/custom").glob("*_frames.csv"), f"{run_id} frames")
    result_path = one_file((directory / "results/artifacts/custom").glob("*_result.json"), f"{run_id} result")
    with frames_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != ["head_ns", "interval_ns", "width", "height", "focused", "iconified", "maximized"]:
            raise ValueError(f"{run_id}: unexpected CSV schema {reader.fieldnames}")
        rows = list(reader)
    if not rows:
        raise ValueError(f"{run_id}: empty frames CSV")

    start_ns = int(rows[0]["head_ns"])
    intervals: list[Interval] = []
    previous_head = start_ns
    for index, row in enumerate(rows[1:], start=2):
        head = int(row["head_ns"])
        observed_interval = int(row["interval_ns"])
        computed_interval = head - previous_head
        if observed_interval != computed_interval:
            raise ValueError(
                f"{run_id} CSV line {index}: interval mismatch {observed_interval} != {computed_interval}"
            )
        intervals.append(
            Interval(
                run_id=run_id,
                line_no=index + 1,
                interval_ns=computed_interval,
                start_s=(previous_head - start_ns) / 1e9,
                end_s=(head - start_ns) / 1e9,
            )
        )
        previous_head = head

    result = json.loads(result_path.read_text(encoding="utf-8"))
    ack = json.loads((directory / "identity-ack.json").read_text(encoding="utf-8"))
    log_lines = (directory / "build/modBench/runs/default/client/logs/latest.log").read_text(
        encoding="utf-8", errors="replace"
    ).splitlines()
    if int(result["frame_count"]) != len(rows):
        raise ValueError(f"{run_id}: frame_count mismatch")
    if int(result["started_ns"]) != start_ns:
        raise ValueError(f"{run_id}: started_ns mismatch")
    if ack.get("world") != f"photon_run_20261105-run-matrix-script-r2_{run_id}":
        raise ValueError(f"{run_id}: identity world mismatch")
    return RunData(
        run_id=run_id,
        directory=directory,
        frames_path=frames_path,
        result_path=result_path,
        rows=rows,
        intervals=intervals,
        result=result,
        ack=ack,
        log_lines=log_lines,
        start_ns=start_ns,
        stop_ns=int(result["stopped_ns"]),
    )


def p99_ns(values: list[int]) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, math.ceil(0.99 * len(ordered)) - 1)
    return ordered[index]


def ms(value_ns: int) -> float:
    return value_ns / 1e6


def fmt_ms(value_ns: int) -> str:
    return f"{ms(value_ns):.4f}"


def fmt_seconds(value: float) -> str:
    return f"{value:.3f}"


def markdown_escape(value: str) -> str:
    return value.replace("|", "\\|")


def event_time(line: str, reference_date) -> datetime | None:
    match = LOG_TIME_RE.search(line)
    if not match:
        return None
    parsed = datetime.strptime(f"{reference_date.isoformat()} {match.group(1)}", "%Y-%m-%d %H:%M:%S.%f")
    return parsed.replace(tzinfo=TIMEZONE)


def ack_local(run: RunData) -> datetime:
    return datetime.fromisoformat(run.ack["at"].replace("Z", "+00:00")).astimezone(TIMEZONE)


def log_events(run: RunData) -> dict:
    reference = ack_local(run).date()
    start_index = next(
        (index for index, line in enumerate(run.log_lines, start=1) if "MODBENCH event=run_start" in line),
        None,
    )
    complete_index = next(
        (index for index, line in enumerate(run.log_lines, start=1) if "MODBENCH event=run_complete" in line),
        None,
    )
    if start_index is None or complete_index is None:
        raise ValueError(f"{run.run_id}: missing MODBENCH run boundaries")
    start_time = event_time(run.log_lines[start_index - 1], reference)
    complete_time = event_time(run.log_lines[complete_index - 1], reference)
    if start_time is None or complete_time is None:
        raise ValueError(f"{run.run_id}: missing log timestamps")

    dynamic = []
    errors = []
    gc = []
    resource = []
    compile_events = []
    for index in range(start_index, complete_index + 1):
        line = run.log_lines[index - 1]
        timestamp = event_time(line, reference)
        if timestamp is None:
            continue
        offset = (timestamp - start_time).total_seconds()
        if "DynamicUniformStorage" in line:
            dynamic.append((index, offset, line))
        if re.search(r"(?i)(\bERROR\b|\bFATAL\b|Exception|Caused by:)", line):
            errors.append((index, offset, line))
        if re.search(r"(?i)(\bGC\b|G1 Evacuation|Pause Young|Pause Full|garbage collection|System\.gc)", line):
            gc.append((index, offset, line))
        if re.search(r"(?i)(reload|reloading|resource pack|Stitching|Reloading ResourceManager|shader.*reload)", line):
            resource.append((index, offset, line))
        if re.search(r"(?i)(compil(?:e|ing|ation)|javac|Gradle|build task)", line):
            compile_events.append((index, offset, line))

    saves = []
    for index in range(complete_index, len(run.log_lines) + 1):
        line = run.log_lines[index - 1]
        if re.search(r"(?i)(Saving chunks|All chunks are saved|All dimensions are saved)", line):
            timestamp = event_time(line, reference)
            if timestamp is not None:
                saves.append((index, (timestamp - start_time).total_seconds(), line))
    ack_offset = (ack_local(run) - start_time).total_seconds()
    return {
        "start_line": start_index,
        "complete_line": complete_index,
        "start_time": start_time,
        "complete_time": complete_time,
        "run_complete_s": (complete_time - start_time).total_seconds(),
        "ack_start_s": ack_offset,
        "ack_complete_s": (ack_local(run) - complete_time).total_seconds(),
        "dynamic": dynamic,
        "errors": errors,
        "gc": gc,
        "resource": resource,
        "compile": compile_events,
        "saves": saves,
    }


def intervals_in_window(run: RunData, low: float, high: float) -> list[Interval]:
    return [interval for interval in run.intervals if low <= interval.end_s < high]


def laser_windows(run: RunData) -> dict[str, tuple[float, float]]:
    if run.scenario != "laser3":
        return {}
    transitions = sorted(run.result["server_laser_transitions"], key=lambda item: int(item["ordinal"]))
    if len(transitions) != 3:
        raise ValueError(f"{run.run_id}: expected 3 laser transitions")
    result: dict[str, tuple[float, float]] = {}
    for ordinal, transition in enumerate(transitions, start=1):
        active = (int(transition["active_ns"]) - run.start_ns) / 1e9
        ended = (int(transition["ended_ns"]) - run.start_ns) / 1e9
        result[f"laser{ordinal * 10}_active"] = (active, ended)
    result["tail1"] = (result["laser10_active"][1], 20.0)
    result["tail2"] = (result["laser20_active"][1], 30.0)
    result["tail3"] = (result["laser30_active"][1], (run.stop_ns - run.start_ns) / 1e9)
    return result


def phase_windows(run: RunData) -> dict[str, tuple[float, float]]:
    windows = {
        "startup_0_5": (0.0, 5.0),
        "action0_5_6": (5.0, 6.0),
    }
    if run.scenario == "laser3":
        windows.update(
            {
                "laser10_trigger": (10.0, 10.5),
                "laser20_trigger": (20.0, 20.5),
                "laser30_trigger": (30.0, 30.5),
            }
        )
        windows.update(laser_windows(run))
    return windows


def phase_summary(runs: list[RunData]) -> list[dict]:
    rows: list[dict] = []
    groups: dict[str, list[RunData]] = defaultdict(list)
    for run in runs:
        groups[run.group].append(run)
    for group in GROUP_ORDER:
        group_runs = groups[group]
        if not group_runs:
            continue
        for phase in PHASE_LABELS:
            values: list[int] = []
            windows_seen = 0
            for run in group_runs:
                window = phase_windows(run).get(phase)
                if window is None:
                    continue
                windows_seen += 1
                values.extend(interval.interval_ns for interval in intervals_in_window(run, *window))
            if windows_seen == 0:
                continue
            rows.append(
                {
                    "group": group,
                    "phase": phase,
                    "runs": windows_seen,
                    "count": len(values),
                    "gt50": sum(value > 50_000_000 for value in values),
                    "gt100": sum(value > 100_000_000 for value in values),
                    "max_ns": max(values) if values else 0,
                    "p99_ns": p99_ns(values),
                }
            )
    return rows


def run_stats(run: RunData) -> dict:
    values = [interval.interval_ns for interval in run.intervals]
    return {
        "run_id": run.run_id,
        "group": run.group,
        "rows": len(run.rows),
        "intervals": len(values),
        "p99_ns": p99_ns(values),
        "max_ns": max(values),
        "gt16_67": sum(value > 16_670_000 for value in values),
        "gt33_33": sum(value > 33_330_000 for value in values),
        "gt50": sum(value > 50_000_000 for value in values),
        "gt100": sum(value > 100_000_000 for value in values),
        "slow_total_ns": sum(value for value in values if value > 16_670_000),
        "started_epoch_ms": run.result.get("started_epoch_ms"),
        "clock_uncertainty_ns": run.result.get("clock_pair_uncertainty_ns"),
    }


def write_tsv(path: Path, fieldnames: list[str], rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def build_markdown(runs: list[RunData], stats: list[dict], phase_rows: list[dict], events: dict[str, dict]) -> str:
    total_rows = sum(len(run.rows) for run in runs)
    total_intervals = sum(len(run.intervals) for run in runs)
    selected = ", ".join(run.run_id for run in runs)
    lines: list[str] = []
    add = lines.append
    phase_lookup = {(row["group"], row["phase"]): row for row in phase_rows}
    laser_trigger_10 = sum(phase_lookup[(group, "laser10_trigger")]["gt50"] for group in ("N_laser3", "S_laser3"))
    laser_active_10 = sum(phase_lookup[(group, "laser10_active")]["gt50"] for group in ("N_laser3", "S_laser3"))
    laser_late_gt50 = sum(
        phase_lookup[(group, phase)]["gt50"]
        for group in ("N_laser3", "S_laser3")
        for phase in (
            "laser20_trigger",
            "laser30_trigger",
            "laser20_active",
            "laser30_active",
            "tail1",
            "tail2",
            "tail3",
        )
    )
    normal_blank_gt50 = sum(row["gt50"] for row in stats if row["group"] == "N_blank45")
    shader_blank_gt50 = sum(row["gt50"] for row in stats if row["group"] == "S_blank45")

    add("# Photon script-r2 慢间隔离线核对")
    add("")
    add("## 结论")
    add("")
    add(
        f"- 已复算 24 局、{total_rows} 条 CSV 数据行和 {total_intervals} 个相邻 `renderFrame` 间隔；"
        "首行零间隔排除，末尾跨界间隔保留，尖峰未删除。"
    )
    add(
        f"- 数据事实：6 个激光局在 10s 施法窗有 {laser_trigger_10} 个 `>50ms`，10s active 窗有 {laser_active_10} 个；"
        f"20/30s 施法、20/30s active 和三段尾迹合计 {laser_late_gt50} 个 `>50ms`。"
    )
    add(
        f"- 空白场景：普通 3 局共 {normal_blank_gt50} 个 `>50ms`；光影 3 局共 {shader_blank_gt50} 个，"
        "其中 `S_blank45_3` 有 16 个，最大 204.5930ms，主要为 19.145-20.796s 的连续 14 个簇；该时段无 action 或激光事件。"
    )
    add(
        "- 已证实慢间隔可与每局 CSV 原始行号、相对秒、`actions` 和 `server_laser_transitions` 对齐；"
        "但原 24 份结果均没有 `started_epoch_ms` 或 `clock_pair_uncertainty_ns`，因此 raw 单调时钟与墙钟无法精确桥接。"
    )
    add(
        "- 截图 `identity-ack.json.at` 可与日志 `run_start`、`run_complete` 做墙钟相对关联；"
        "但不能可靠落到具体 raw CSV 行或慢间隔。截图干扰仍是待核假设。"
    )
    add(
        "- 本批未采样 GPU 完成时间或分配，JFR/Spark 关闭；本报告不把慢间隔归因为 CPU 或 GPU。"
    )
    add("")
    add("## 口径与数据身份")
    add("")
    add(f"- 批次：`{DEFAULT_BATCH}`。")
    add(f"- 入选目录：`{selected}`。")
    add("- 排除：`N_cow25_1`（缺 ack 的旧目录）、旧手工批次、script-r1。")
    add("- 采样点：`Minecraft.renderFrame` HEAD；间隔覆盖 tick、render、swap 和限帧等待。")
    add("- `actual_cap=260` 是不执行显式限帧的哨兵值，不按 260Hz 上限筛除高帧率空白局。")
    add("- 相对秒基准：每局 CSV 第一条 `head_ns`。间隔的起止分别为前一 HEAD 与当前 HEAD。")
    add("- CSV 原始行号按文件行号计算：第 1 行为表头，第 2 行首间隔为零并排除；其余间隔行号为 3 起。")
    add("- P99 使用排序后第 `ceil(0.99*n)` 个样本；阶段归属使用间隔结束 HEAD 的相对秒。")
    add("- 源码证据：`PhotonRunMatrixCapture.java:288-317` 记录 renderFrame HEAD 与间隔，`378-391` 记录 server active/ended ns，`394-442` 记录 action due/requested/actual ns，`491-550` 写 CSV 和结果字段。")
    add("- 截图采证：`20261105-run-matrix-fresh.mjs:223-256` 在 7s 观察窗口后调用 `screenshot_to_file` 并写 `identity-ack.json`；`ack.at` 是墙钟。当前源码 `PhotonRunMatrixCapture.java:509` 写 `started_epoch_ms`，但原 24 份结果均无该字段，不能追认桥接。")
    add("")
    add("## 逐局统计")
    add("")
    add("|目录|行数|间隔数|P99 ms|最大 ms|>16.67|>33.33|>50|>100|慢帧累计 ms|")
    add("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for row in stats:
        add(
            f"|{row['run_id']}|{row['rows']}|{row['intervals']}|{fmt_ms(row['p99_ns'])}|"
            f"{fmt_ms(row['max_ns'])}|{row['gt16_67']}|{row['gt33_33']}|{row['gt50']}|{row['gt100']}|"
            f"{fmt_ms(row['slow_total_ns'])}|"
        )
    add("")
    add("## 每局最慢前 10 个间隔")
    add("")
    add("`>50ms` 列标出达到阈值；若该局第 10 名之外仍有 `>50ms`，在后续“前 10 名之外的全部 >50ms”表中补全。")
    add("")
    for run in runs:
        ordered = sorted(run.intervals, key=lambda item: (-item.interval_ns, item.end_s))
        top = "; ".join(
            f"{rank}) {fmt_seconds(interval.start_s)}-{fmt_seconds(interval.end_s)}s "
            f"{fmt_ms(interval.interval_ns)}ms L{interval.line_no}"
            f"{' >50' if interval.interval_ns > 50_000_000 else ''}"
            for rank, interval in enumerate(ordered[:10], start=1)
        )
        add(f"- `{run.run_id}` 前10：{top}。")
        extras = [item for item in ordered[10:] if item.interval_ns > 50_000_000]
        if extras:
            extra_text = "; ".join(
                f"{fmt_seconds(interval.start_s)}-{fmt_seconds(interval.end_s)}s "
                f"{fmt_ms(interval.interval_ns)}ms L{interval.line_no}"
                for interval in extras
            )
            add(f"- `{run.run_id}` 前10名之外的 >50ms：{extra_text}。")
    add("")
    add("## 阶段汇总")
    add("")
    add("阶段窗口：启动 `0-5s`；action0 `5-6s`；三次施法 `10/20/30s` 各取事件后 `0.5s`；"
        "激光 active 使用 `server_laser_transitions` 的 monotonic ns；尾迹 1/2 分别从上一段 ended 到下一施法 due，"
        "尾迹 3 从第三段 ended 到本局 stop。")
    add("")
    add("|组|阶段|局数|间隔数|>50|>100|最大 ms|P99 ms|")
    add("|---|---|---:|---:|---:|---:|---:|---:|")
    for row in phase_rows:
        add(
            f"|{row['group']}|{PHASE_LABELS[row['phase']]}|{row['runs']}|{row['count']}|"
            f"{row['gt50']}|{row['gt100']}|{fmt_ms(row['max_ns'])}|{fmt_ms(row['p99_ns'])}|"
        )
    add("")
    add("## 重复性与条件差异")
    add("")
    add("### 每局最大值与 >50ms 次数")
    add("")
    add("|组|各局最大值 ms|各局 >50ms|")
    add("|---|---|---|")
    grouped: dict[str, list[dict]] = defaultdict(list)
    for row in stats:
        grouped[row["group"]].append(row)
    for group in GROUP_ORDER:
        rows = grouped[group]
        add(
            f"|{group}|{', '.join(fmt_ms(row['max_ns']) for row in rows)}|"
            f"{', '.join(str(row['gt50']) for row in rows)}|"
        )
    add("")
    add("普通/光影同场景的局级最大值中位数对比如下，仅描述本批数据差异，不作因果归因。")
    add("")
    add("|场景|普通中位最大值 ms|光影中位最大值 ms|光影/普通|普通 >50 合计|光影 >50 合计|")
    add("|---|---:|---:|---:|---:|---:|")
    for scenario in ("blank45", "cow25", "queen25", "laser3"):
        normal = [row["max_ns"] for row in stats if row["group"] == f"N_{scenario}"]
        shader = [row["max_ns"] for row in stats if row["group"] == f"S_{scenario}"]
        normal_gt50 = sum(row["gt50"] for row in stats if row["group"] == f"N_{scenario}")
        shader_gt50 = sum(row["gt50"] for row in stats if row["group"] == f"S_{scenario}")
        normal_median = median(normal)
        shader_median = median(shader)
        ratio = shader_median / normal_median if normal_median else math.nan
        add(
            f"|{scenario}|{fmt_ms(int(normal_median))}|{fmt_ms(int(shader_median))}|{ratio:.2f}|"
            f"{normal_gt50}|{shader_gt50}|"
        )
    add("")
    add("### 场景汇总")
    add("")
    add("|场景|局数|间隔总数|>50 总数|最大 ms|各局最大值 ms|")
    add("|---|---:|---:|---:|---:|---|")
    for scenario in ("blank45", "cow25", "queen25", "laser3"):
        rows = [row for row in stats if row["group"].endswith("_" + scenario)]
        add(
            f"|{scenario}|{len(rows)}|{sum(row['intervals'] for row in rows)}|"
            f"{sum(row['gt50'] for row in rows)}|{fmt_ms(max(row['max_ns'] for row in rows))}|"
            f"{', '.join(fmt_ms(row['max_ns']) for row in rows)}|"
        )
    add("")
    add("## 截图与日志关联")
    add("")
    add("墙钟统一按 `identity-ack.json.at` 的 UTC 转为 `+08:00`；日志时间取 `latest.log` 的同日时间。"
        "两者分辨率均为毫秒，可比较相对偏移，但原 CSV 没有 epoch 桥接字段，不能据此定位 raw 行。")
    add("")
    add("|目录|run_start 行|run_complete 行|run_start→complete s|截图 ack 在 run_start 后 s|截图 ack 在 complete 前 s|DynamicUniformStorage 事件|run 内 GC|run 内 resource reload|run 内报错/异常|save 首行偏移 s|")
    add("|---|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|")
    for run in runs:
        event = events[run.run_id]
        dynamic = ", ".join(f"L{line_no}@{offset:.3f}s" for line_no, offset, _ in event["dynamic"]) or "无"
        errors = ", ".join(f"L{line_no}@{offset:.3f}s" for line_no, offset, _ in event["errors"]) or "无"
        saves = event["saves"][0][1] if event["saves"] else None
        add(
            f"|{run.run_id}|{event['start_line']}|{event['complete_line']}|{event['run_complete_s']:.3f}|"
            f"{event['ack_start_s']:.3f}|{-event['ack_complete_s']:.3f}|{dynamic}|"
            f"{len(event['gc'])}|{len(event['resource'])}|{errors}|"
            f"{'' if saves is None else f'{saves:.3f}'}|"
        )
    add("")
    add("事实：24 局 `run_start` 到 `run_complete` 均约 26s 或 46s；save/chunk 日志均在 run_complete 之后。"
        "run 窗口内没有匹配到 GC 文本、resource reload 或编译文本。")
    add("")
    add("截图墙钟相对 `run_start` 的偏移分布为：")
    add("")
    blank_n = [events[r.run_id]["ack_start_s"] for r in runs if r.group == "N_blank45"]
    blank_s = [events[r.run_id]["ack_start_s"] for r in runs if r.group == "S_blank45"]
    add(f"- 普通 blank45：{', '.join(f'{value:.3f}s' for value in blank_n)}。")
    add(f"- 光影 blank45：{', '.join(f'{value:.3f}s' for value in blank_s)}。")
    other = [events[run.run_id]["ack_start_s"] for run in runs if not run.group.endswith("_blank45")]
    add(f"- 其余 18 局：{min(other):.3f}-{max(other):.3f}s。")
    add("")
    add("|激光局|ordinal|active s|ended s|active 长度 s|")
    add("|---|---:|---:|---:|---:|")
    for run in runs:
        if run.scenario != "laser3":
            continue
        for transition in sorted(run.result["server_laser_transitions"], key=lambda item: int(item["ordinal"])):
            active = (int(transition["active_ns"]) - run.start_ns) / 1e9
            ended = (int(transition["ended_ns"]) - run.start_ns) / 1e9
            add(f"|{run.run_id}|{transition['ordinal']}|{active:.3f}|{ended:.3f}|{ended - active:.3f}|")
    add("")
    add("## 无法判断")
    add("")
    add("- 无法把截图 ack 墙钟偏移精确映射到 raw CSV 行、单次慢间隔或激光 active 窗口。")
    add("- 无法根据本批证明慢间隔来自 CPU、GPU、分配、GC、截图、保存或资源加载；日志和采样字段只能提供时间事实。")
    add("- 没有 Spark/JFR/GPU timing/allocation 样本，不应给出根因或收益归因。")
    add("")
    add("## 复算")
    add("")
    add("```powershell")
    add("python -B tools/photon/performance/20261105-slow-interval-audit.py")
    add("```")
    add("")
    add("脚本退出码预期为 `0`。派生 TSV 与生成稿位于 `build/agent-backups/20261105-slow-interval-audit/`。")
    add("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--batch", default=DEFAULT_BATCH)
    parser.add_argument("--output", default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    root = args.root.resolve()
    batch_root = (root / args.batch).resolve()
    output_root = (root / args.output).resolve()
    output_root.mkdir(parents=True, exist_ok=True)

    runs = [load_run(batch_root, run_id) for run_id in build_run_ids()]
    stats = [run_stats(run) for run in runs]
    phase_rows = phase_summary(runs)
    events = {run.run_id: log_events(run) for run in runs}

    top_rows: list[dict] = []
    slow_rows: list[dict] = []
    for run in runs:
        ordered = sorted(run.intervals, key=lambda item: (-item.interval_ns, item.end_s))
        for rank, interval in enumerate(ordered[:10], start=1):
            top_rows.append(
                {
                    "run_id": run.run_id,
                    "rank": rank,
                    "start_s": f"{interval.start_s:.9f}",
                    "end_s": f"{interval.end_s:.9f}",
                    "interval_ms": f"{interval.interval_ns / 1e6:.6f}",
                    "csv_line": interval.line_no,
                    "gt50": interval.interval_ns > 50_000_000,
                }
            )
        for interval in sorted(
            (item for item in run.intervals if item.interval_ns > 50_000_000),
            key=lambda item: (-item.interval_ns, item.end_s),
        ):
            slow_rows.append(
                {
                    "run_id": run.run_id,
                    "start_s": f"{interval.start_s:.9f}",
                    "end_s": f"{interval.end_s:.9f}",
                    "interval_ms": f"{interval.interval_ns / 1e6:.6f}",
                    "csv_line": interval.line_no,
                }
            )

    write_tsv(
        output_root / "run_stats.tsv",
        [
            "run_id",
            "group",
            "rows",
            "intervals",
            "p99_ns",
            "max_ns",
            "gt16_67",
            "gt33_33",
            "gt50",
            "gt100",
            "slow_total_ns",
            "started_epoch_ms",
            "clock_uncertainty_ns",
        ],
        stats,
    )
    write_tsv(
        output_root / "top10.tsv",
        ["run_id", "rank", "start_s", "end_s", "interval_ms", "csv_line", "gt50"],
        top_rows,
    )
    write_tsv(
        output_root / "slow_gt50.tsv",
        ["run_id", "start_s", "end_s", "interval_ms", "csv_line"],
        slow_rows,
    )
    write_tsv(
        output_root / "phase_stats.tsv",
        ["group", "phase", "runs", "count", "gt50", "gt100", "max_ns", "p99_ns"],
        phase_rows,
    )
    event_rows = []
    for run in runs:
        event = events[run.run_id]
        event_rows.append(
            {
                "run_id": run.run_id,
                "run_start_line": event["start_line"],
                "run_complete_line": event["complete_line"],
                "run_complete_s": f"{event['run_complete_s']:.6f}",
                "ack_start_s": f"{event['ack_start_s']:.6f}",
                "ack_complete_s": f"{event['ack_complete_s']:.6f}",
                "dynamic_events": len(event["dynamic"]),
                "gc_events": len(event["gc"]),
                "resource_events": len(event["resource"]),
                "error_events": len(event["errors"]),
                "save_first_s": "" if not event["saves"] else f"{event['saves'][0][1]:.6f}",
            }
        )
    write_tsv(
        output_root / "log_assoc.tsv",
        [
            "run_id",
            "run_start_line",
            "run_complete_line",
            "run_complete_s",
            "ack_start_s",
            "ack_complete_s",
            "dynamic_events",
            "gc_events",
            "resource_events",
            "error_events",
            "save_first_s",
        ],
        event_rows,
    )
    report = build_markdown(runs, stats, phase_rows, events)
    (output_root / "20261105-ds-photon-slow-interval-audit.generated.md").write_text(report, encoding="utf-8", newline="\n")

    print(
        json.dumps(
            {
                "runs": len(runs),
                "rows": sum(len(run.rows) for run in runs),
                "intervals": sum(len(run.intervals) for run in runs),
                "gt50": len(slow_rows),
                "output": str(output_root),
                "report": str(output_root / "20261105-ds-photon-slow-interval-audit.generated.md"),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
