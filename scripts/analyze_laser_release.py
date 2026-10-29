"""Read-only analysis of the current laser trial frame JSON and local spark protobuf."""
import argparse
import collections
import json
import math
import pathlib
import struct


def varint(data, position):
    value = shift = 0
    while True:
        byte = data[position]
        position += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, position
        shift += 7
        if shift > 70:
            raise ValueError("Invalid protobuf varint")


def message(data):
    result = collections.defaultdict(list)
    position = 0
    while position < len(data):
        key, position = varint(data, position)
        field, wire = key >> 3, key & 7
        if wire == 0:
            value, position = varint(data, position)
        elif wire == 1:
            value = struct.unpack_from("<d", data, position)[0]
            position += 8
        elif wire == 2:
            size, position = varint(data, position)
            value = data[position:position + size]
            position += size
        elif wire == 5:
            value = struct.unpack_from("<f", data, position)[0]
            position += 4
        else:
            raise ValueError(f"Unsupported protobuf wire type {wire}")
        result[field].append(value)
    return result


def packed_doubles(values):
    result = []
    for value in values:
        if isinstance(value, bytes):
            if len(value) % 8:
                raise ValueError("Invalid packed double length")
            result.extend(struct.unpack("<" + "d" * (len(value) // 8), value))
        else:
            result.append(value)
    return result


def packed_ints(values):
    result = []
    for value in values:
        if isinstance(value, bytes):
            position = 0
            while position < len(value):
                item, position = varint(value, position)
                result.append(item)
        else:
            result.append(value)
    return result


def spark(path):
    root = message(path.read_bytes())
    metadata = message(root[1][0])
    start, end = metadata[2][0], metadata[11][0]
    result = {
        "path": str(path), "start_ms": start, "end_ms": end,
        "duration_ms": end - start, "interval": metadata[3][0],
        "time_windows": packed_ints(root[6]), "threads": [],
    }
    for entry in root[2]:
        thread = message(entry)
        name = thread[1][0].decode()
        if name not in ("Render thread", "Server thread"):
            continue
        nodes = [message(node) for node in thread[3]]
        roots = packed_ints(thread[5])
        times = packed_doubles(thread[4])
        inclusive = collections.Counter()
        exclusive = collections.Counter()
        invalid = 0
        visited_paths = 0
        todo = [(index, frozenset(), frozenset()) for index in roots]
        while todo:
            index, ancestors, path_ids = todo.pop()
            if index >= len(nodes) or index in path_ids:
                raise ValueError("Invalid spark node reference or cycle")
            visited_paths += 1
            node = nodes[index]
            label = node[3][0].decode() + "." + node[4][0].decode()
            weight = sum(packed_doubles(node[8]))
            children = packed_ints(node[9])
            child_weight = sum(sum(packed_doubles(nodes[child][8])) for child in children)
            self_weight = weight - child_weight
            if self_weight < -0.001:
                invalid += 1
            exclusive[label] += max(0.0, self_weight)
            if label not in ancestors:
                inclusive[label] += weight
            todo.extend((child, ancestors | {label}, path_ids | {index}) for child in children)
        total = sum(times)
        selected = {key: value for key, value in inclusive.items()
                    if any(word in key for word in ("unitego", "lowdrag", "geckolib", "gson",
                                                     "blaze3d", "MemoryStack", "GL20", "GL30",
                                                     "GL41", "GL45"))}
        result["threads"].append({
            "name": name, "root_ms": total, "window_weights": times,
            "node_count": len(nodes), "visited_paths": visited_paths,
            "negative_self_count": invalid,
            "top_self": [{"method": key, "ms": value, "percent": value * 100 / total}
                         for key, value in exclusive.most_common(30)],
            "top_relevant_inclusive": [
                {"method": key, "ms": value, "percent": value * 100 / total}
                for key, value in sorted(selected.items(), key=lambda item: -item[1])[:50]],
        })
    return result


def stats(values):
    values = sorted(value for value in values if value >= 0)
    if not values:
        return None
    def percentile(p):
        return values[max(0, math.ceil(len(values) * p) - 1)]
    return {"count": len(values), "mean": sum(values) / len(values),
            "p50": percentile(.5), "p95": percentile(.95),
            "p99": percentile(.99), "max": values[-1]}


def window(frames, start, end):
    heads = [value / 1e9 for value in frames["head_offset_ns"]]
    indices = [i for i in range(1, len(heads))
               if heads[i] >= start and heads[i - 1] <= end and frames["interval_ns"][i] > 0]
    intervals = [frames["interval_ns"][i] / 1e6 for i in indices]
    result = {"window_seconds": [start, end], "interval_ms": stats(intervals)}
    if not intervals:
        return result
    result["mean_fps"] = len(intervals) * 1000 / sum(intervals)
    result["slowest_frame_fps"] = 1000 / max(intervals)
    slow = sorted(intervals, reverse=True)
    result["one_percent_low"] = 1000 / (sum(slow[:math.ceil(len(slow) * .01)]) / math.ceil(len(slow) * .01))
    result["point_one_percent_low"] = 1000 / (sum(slow[:math.ceil(len(slow) * .001)]) / math.ceil(len(slow) * .001))
    left = 0
    rolling = []
    for right, head in enumerate(heads):
        while left <= right and heads[left] <= head - 1:
            left += 1
        if start + 1 <= head <= end:
            rolling.append(right - left + 1)
    result["minimum_rolling_one_second_fps"] = min(rolling) if rolling else None
    result["previous_minecraft_ms"] = stats([frames["previous_minecraft_ns"][i] / 1e6 for i in indices])
    result["render_cpu_ms"] = stats([frames["render_cpu_ns"][i] / 1e6 for i in indices])
    result["render_gpu_ms"] = stats([frames["render_gpu_ns"][i] / 1e6 for i in indices])
    result["allocation_mib_per_frame"] = stats([frames["render_thread_allocated_bytes"][i] / 2**20 for i in indices])
    result["limits"] = dict(collections.Counter(frames["actual_framerate_limit"][i] for i in indices))
    result["throttle_reasons"] = dict(collections.Counter(frames["throttle_reason"][i] for i in indices))
    result["flags"] = dict(collections.Counter(frames["flags"][i] for i in indices))
    result["framebuffer_sizes"] = dict(collections.Counter(
        f'{frames["framebuffer_width"][i]}x{frames["framebuffer_height"][i]}' for i in indices))
    result["longest_frames"] = [
        {"index": i, "head_seconds": heads[i], "interval_ms": frames["interval_ns"][i] / 1e6,
         "previous_render_cpu_ms": frames["render_cpu_ns"][i - 1] / 1e6,
         "previous_render_gpu_ms": frames["render_gpu_ns"][i - 1] / 1e6,
         "current_render_cpu_ms": frames["render_cpu_ns"][i] / 1e6,
         "previous_minecraft_ms": frames["previous_minecraft_ns"][i] / 1e6}
        for i in sorted(indices, key=lambda i: -frames["interval_ns"][i])[:8]]
    return result


def frame_trial(path):
    marks = json.loads(path.read_text(encoding="utf-8"))
    capture = json.loads(pathlib.Path(marks["capture"]["saved_path"]).read_text(encoding="utf-8"))
    start_ns = capture["summary"]["started_ns"]
    phases = collections.defaultdict(dict)
    for mark in marks["marks"]:
        if mark["event"] == "client_phase":
            identifier, phase = mark["detail"].split(":")
            phases[identifier][int(float(phase))] = (mark["nano_time"] - start_ns) / 1e9
    result = {"marks_path": str(path), "spark": marks["spark"],
              "summary": capture["summary"], "cycles": []}
    for identifier, phase in phases.items():
        if 0 not in phase or 1 not in phase:
            raise ValueError("Missing charge or output marker")
        cycle = {"animation": identifier, "phases": phase,
                 "cast_to_release": window(capture["frames"], phase[0] - .2, phase[1] + 2),
                 "charge": window(capture["frames"], phase[0] - .2, phase[0] + .8),
                 "release": window(capture["frames"], phase[1] - .2, phase[1] + 2),
                 "sustain": window(capture["frames"], phase[1] + 2, phase.get(2, phase[1] + 10) - .2)}
        result["cycles"].append(cycle)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("spark", "frames"))
    parser.add_argument("path", type=pathlib.Path)
    args = parser.parse_args()
    result = spark(args.path) if args.kind == "spark" else frame_trial(args.path)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
