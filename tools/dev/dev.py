#!/usr/bin/env python3
"""Bounded external-terminal file and IDEA operations; no shell evaluation."""
import argparse
import fnmatch
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import tempfile
import uuid
import difflib
from urllib.parse import unquote, urlsplit

VERSION = "1.0.1"
DEFAULT_ENDPOINT = "http://127.0.0.1:64342/stream"
EXCLUDED = {".git", ".workspace", ".local", ".idea", ".gradle", "build", "run", "node_modules", "lib", "vendor"}
READ_OPS = {"doctor", "capabilities", "files", "read", "search", "package", "diff", "links"}
TOOLS = {
    "symbols": "mixin_search_symbols", "class": "mixin_find_class", "members": "mixin_find_class",
    "method-read": "mixin_find_class", "refs": "mixin_find_references", "symbol": "get_symbol_info",
    "callers": "mixin_call_hierarchy", "callees": "mixin_call_hierarchy",
    "hierarchy": "mixin_type_hierarchy", "overrides": "mixin_find_overrides",
    "super-methods": "mixin_super_methods", "implementations": "mixin_find_impls",
    "problems": "get_file_problems", "rename": "mixin_rename", "move": "mixin_move_file",
    "signature": "mixin_change_signature", "extract": "mixin_extract_method",
}
REFACTOR = {"rename", "move", "signature", "extract"}
WRITES = {"write", "rewrite", "replace", "insert", "patch", "method-replace"}
ALIASES = {"class": "className", "method": "methodName", "member": "memberName", "new": "newName",
           "parameters": "parameterTypes", "descriptor": "methodDescriptor", "depth": "maxDepth"}


class Failure(Exception):
    def __init__(self, code, message, details=None):
        super().__init__(message)
        self.code, self.details = code, details


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run(args, root, timeout):
    try:
        p = subprocess.run(args, cwd=root, capture_output=True, timeout=timeout, shell=False)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise Failure("COMMAND_FAILED", str(e)) from e
    if p.returncode not in (0, 1) or (p.returncode == 1 and args[0] != "rg"):
        raise Failure("COMMAND_FAILED", p.stderr.decode("utf-8", "replace")[:1500])
    return p.stdout.decode("utf-8", "replace")


def safe_path(root, name, writing=False):
    p = (root / name).resolve()
    if not p.is_relative_to(root):
        raise Failure("PATH_OUTSIDE_ROOT", str(name))
    rel = p.relative_to(root)
    parts = tuple(x.casefold() for x in rel.parts)
    if any(x in EXCLUDED for x in parts) or parts[:2] == ("src", "generated"):
        raise Failure("PATH_EXCLUDED", str(name))
    if writing and (not rel.parts or p.is_dir()):
        raise Failure("INVALID_PATH", str(name))
    return p


def file_list(root, pattern, timeout):
    if not shutil.which("rg"):
        raise Failure("RG_MISSING", "Install rg or put it on PATH")
    args = ["rg", "--files", "--hidden", "-0", "-g", pattern]
    for name in sorted(EXCLUDED):
        args += ["-g", "!**/" + name + "/**"]
    args += ["-g", "!src/generated/**", "--", "."]
    return sorted(p.replace("\\", "/").removeprefix("./") for p in run(args, root, timeout).split("\0") if p)


class IDE:
    def __init__(self, endpoint, root, timeout):
        u = urlsplit(endpoint)
        if u.scheme != "http" or u.hostname not in {"127.0.0.1", "localhost", "::1"} or u.username or u.password:
            raise Failure("INVALID_ENDPOINT", "Only a local HTTP IDEA endpoint is supported")
        self.connection = http.client.HTTPConnection(u.hostname, u.port or 80, timeout=timeout)
        self.path = u.path or "/stream"
        self.headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
        self.root, self.counter, self.catalog = str(root), 0, None
        try:
            self.rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {},
                                    "clientInfo": {"name": "dev-toolkit", "version": VERSION}})
            self.headers["MCP-Protocol-Version"] = "2025-03-26"
            self.rpc("notifications/initialized", {}, notification=True)
        except Exception:
            self.close()
            raise

    def close(self):
        self.connection.close()

    def rpc(self, method, params, notification=False):
        self.counter += 1
        body = {"jsonrpc": "2.0", "method": method, "params": params}
        if not notification:
            body["id"] = self.counter
        try:
            self.connection.request("POST", self.path, json.dumps(body), self.headers)
            response = self.connection.getresponse()
            session = response.getheader("Mcp-Session-Id")
            if session:
                self.headers["Mcp-Session-Id"] = session
            data = response.read().decode("utf-8")
            if response.status not in (200, 202, 204):
                raise Failure("IDE_HTTP", "HTTP " + str(response.status), data[:500])
            if notification:
                return {}
            if response.getheader("Content-Type", "").startswith("text/event-stream"):
                messages = []
                for block in data.replace("\r\n", "\n").split("\n\n"):
                    lines = [s[5:].lstrip() for s in block.splitlines() if s.startswith("data:")]
                    if lines:
                        messages.append(json.loads("\n".join(lines)))
                result = next((m for m in messages if m.get("id") == self.counter), None)
                if result is None:
                    raise Failure("IDE_PROTOCOL", "Missing matching SSE response")
            else:
                result = json.loads(data)
            if result.get("error"):
                raise Failure("IDE_RPC", str(result["error"])[:1500])
            if result.get("id") != self.counter:
                raise Failure("IDE_PROTOCOL", "Response id mismatch")
            return result.get("result", {})
        except (OSError, ValueError, http.client.HTTPException) as e:
            raise Failure("IDE_UNAVAILABLE", str(e)) from e

    def tools(self):
        if self.catalog is None:
            self.catalog = {}
            cursor = None
            while True:
                page = self.rpc("tools/list", {"cursor": cursor} if cursor else {})
                self.catalog.update({t["name"]: t for t in page.get("tools", [])})
                cursor = page.get("nextCursor")
                if not cursor:
                    break
        return self.catalog

    def tool(self, name, args):
        catalog = self.tools()
        if name not in catalog:
            raise Failure("CAPABILITY_MISSING", name)
        args = {**args, "projectPath": self.root}
        schema = catalog[name].get("inputSchema", {})
        props = schema.get("properties", {})
        missing = [k for k in schema.get("required", []) if k not in args]
        unknown = [k for k in args if k not in props]
        if missing or unknown:
            raise Failure("INVALID_TOOL_ARGS", "Arguments do not match live schema", {"missing": missing, "unknown": unknown})
        value = self.rpc("tools/call", {"name": name, "arguments": args})
        if value.get("isError"):
            raise Failure("IDE_TOOL_FAILED", text_result(value)[:2000])
        return value


def text_result(value):
    return "\n".join(c.get("text", "") for c in value.get("content", []) if c.get("type") == "text")


class Runner:
    def __init__(self, root, endpoint, timeout):
        self.root, self.endpoint, self.timeout, self.ide = root, endpoint, timeout, None

    def close(self):
        if self.ide:
            self.ide.close()

    def backend(self):
        if self.ide is None:
            self.ide = IDE(self.endpoint, self.root, self.timeout)
        return self.ide

    def execute(self, req):
        op = req.get("op", "")
        started = time.monotonic()
        try:
            offset, limit = req.get("offset", 0), req.get("limit", 20)
            if not isinstance(offset, int) or offset < 0 or not isinstance(limit, int) or not 1 <= limit <= 500:
                raise Failure("INVALID_PAGE", "offset >= 0 and limit 1..500 required")
            items, backend, notes = self.dispatch(op, req)
            total = len(items)
            page = items[offset:offset + limit]
            truncated = offset + limit < total
            result = {"ok": True, "operation": op, "backend": backend, "items": page, "total": total,
                      "truncated": truncated, "continuation": {"offset": offset + limit} if truncated else None,
                      "warnings": notes, "elapsed_ms": round((time.monotonic() - started) * 1000)}
            if op == "batch":
                result["ok"] = all(item.get("ok", False) for item in items)
                result["completed_requests"] = len(items)
            if backend == "idea":
                result["total_scope"] = "returned response lines; backend may have its own cap"
            return result
        except (Failure, OSError, ValueError, KeyError, TypeError, re.error) as e:
            return {"ok": False, "operation": op, "items": [], "total": None, "truncated": False,
                    "continuation": None, "warnings": [], "error": {"code": getattr(e, "code", "INVALID_REQUEST"),
                    "message": str(e), "details": getattr(e, "details", None)}}

    def dispatch(self, op, req):
        args = req.get("args", {})
        if not isinstance(args, dict):
            raise Failure("INVALID_REQUEST", "args must be an object")
        if op == "batch":
            requests = req.get("requests", [])
            if not isinstance(requests, list) or not 1 <= len(requests) <= 100:
                raise Failure("INVALID_BATCH", "1..100 requests required")
            if any(not isinstance(sub, dict) or sub.get("op") == "batch" for sub in requests):
                raise Failure("INVALID_BATCH", "Nested or non-object request")
            results = []
            for sub in requests:
                value = self.execute(sub)
                results.append(value)
                if not value["ok"]:
                    break
            return results, "batch", ["Sequential; stops on first failure; earlier writes are not rolled back"]
        if op == "help":
            return sorted(READ_OPS | set(TOOLS) | WRITES | {"batch", "reads", "writes"}), "local", []
        if op == "doctor":
            info = {"root": str(self.root), "python": sys.version.split()[0], "rg": shutil.which("rg"),
                    "git": shutil.which("git"), "version": VERSION, "ide": "not_checked"}
            if args.get("ide"):
                try:
                    info["ide"] = {"tool_count": len(self.backend().tools())}
                except Failure as e:
                    raise Failure(e.code, str(e), info) from e
            return [info], "local", []
        if op == "capabilities":
            if args.get("op") in WRITES:
                return [{"operation": args["op"], "available": True, "backend": "cli", "mode": "exact text preview/apply"}], "local", []
            catalog = self.backend().tools()
            rows = [{"operation": k, "tool": v, "available": v in catalog,
                     "mode": "native IDE preview/apply bound to previewHash; no atomic transaction" if k in REFACTOR else "read"}
                    for k, v in TOOLS.items()]
            rows += [{"operation": k, "available": True, "mode": "CLI exact text preview/apply; expectedHash and no-unsaved assertion for existing files"} for k in sorted(WRITES)]
            if args.get("op"):
                rows = [r for r in rows if r["operation"] == args["op"]]
            if args.get("schema"):
                for row in rows:
                    if row.get("tool") in catalog:
                        row["schema"] = catalog[row["tool"]]["inputSchema"]
            return rows, "local", ["Availability is not execution success"]
        if op == "files":
            return file_list(self.root, args.get("glob", "*"), self.timeout), "cli", []
        if op == "read":
            p = safe_path(self.root, args["path"])
            data = p.read_bytes()
            encoding = args.get("encoding", "utf-8-sig")
            text = data.decode(encoding)
            start, count = args.get("start", 1), args.get("count", 100)
            if not isinstance(start, int) or start < 1 or not isinstance(count, int) or not 1 <= count <= 5000:
                raise Failure("INVALID_RANGE", "start >= 1, count 1..5000 required")
            rows = text.splitlines()
            return [{"path": p.relative_to(self.root).as_posix(), "sha256": digest(data), "source": "disk",
                     "line_count": len(rows), "range_truncated": start - 1 + count < len(rows),
                     "lines": [{"line": i + 1, "text": rows[i]} for i in range(start - 1, min(len(rows), start - 1 + count))]}], "cli", ["Disk read; unsaved IDEA Document is not included"]
        if op == "search":
            pattern = args["q"]
            files = file_list(self.root, args.get("glob", "*"), self.timeout)
            cap = args.get("maxMatches", 1000)
            if not isinstance(cap, int) or not 1 <= cap <= 10000:
                raise Failure("INVALID_REQUEST", "maxMatches must be 1..10000")
            cmd = ["rg", "--json", "--no-config", "--max-count", str(cap)]
            if not args.get("regex"):
                cmd.append("--fixed-strings")
            if not args.get("caseSensitive", True):
                cmd.append("--ignore-case")
            # stdin supplies an explicit file list via separate chunks, never a shell command.
            rows = []
            for first in range(0, len(files), 100):
                data = run(cmd + ["--", pattern] + files[first:first + 100], self.root, self.timeout)
                for line in data.splitlines():
                    event = json.loads(line)
                    if event.get("type") == "match":
                        d = event["data"]
                        rows.append({"path": d["path"].get("text"), "line": d["line_number"],
                                     "text": d["lines"].get("text", "").rstrip("\r\n")})
                        if len(rows) >= cap:
                            return rows, "cli", ["Match cap reached; total is collected matches, narrow query for completeness"]
            return rows, "cli", []
        if op == "package":
            name = args["name"]
            rx = re.compile(r"^\s*package\s+" + re.escape(name) + (r"(?:\.[\w.]+)?" if args.get("recursive") else "") + r"\s*;", re.M)
            rows = []
            for f in file_list(self.root, "*.java", self.timeout):
                text = safe_path(self.root, f).read_text(encoding="utf-8-sig")
                if rx.search(text):
                    rows.append({"path": f, "primaryTypeCandidate": Path(f).stem})
            return rows, "cli", ["Package-declaration text candidates; nested/top-level type identity requires class/members"]
        if op == "diff":
            files = args.get("paths", [])
            if not files:
                raise Failure("INVALID_REQUEST", "Explicit paths required")
            paths = [str(safe_path(self.root, p).relative_to(self.root)) for p in files]
            return run(["git", "diff", "--no-ext-diff", "--no-color", "HEAD", "--"] + paths, self.root, self.timeout).splitlines(), "cli", ["Untracked files are not included by git diff"]
        if op == "links":
            rows = []
            for f in args.get("paths", []):
                p = safe_path(self.root, f)
                for n, line in enumerate(p.read_text(encoding="utf-8-sig").splitlines(), 1):
                    for target in re.findall(r"\]\(([^)]+)\)", line):
                        url = target.strip().strip("<>").split("#", 1)[0]
                        if not url or re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", url):
                            continue
                        dst = (p.parent / unquote(url)).resolve()
                        if not dst.is_relative_to(self.root):
                            rows.append({"path": f, "line": n, "target": target, "status": "outside_root_not_checked"})
                        elif not dst.exists():
                            rows.append({"path": f, "line": n, "target": target, "status": "missing"})
            return rows, "cli", ["Basic Markdown relative-path check; anchors, reference links and titles are not parsed"]
        if op in {"reads", "writes"}:
            raise Failure("CAPABILITY_MISSING", "No verified read/write-reference classifier; use refs (unclassified)")
        if op in WRITES:
            return self.write(op, req, args)
        if op in TOOLS:
            return self.semantic(op, req, args)
        raise Failure("UNKNOWN_OPERATION", op)

    def write(self, op, req, args):
        p = safe_path(self.root, args["path"], writing=True)
        exists = p.exists()
        before = p.read_bytes() if exists else b""
        if op == "write" and exists:
            raise Failure("ALREADY_EXISTS", "write is new-file only; use rewrite with an expectedHash")
        if op != "write" and (not exists or args.get("expectedHash") != digest(before)):
            raise Failure("VERSION_CONFLICT", "Existing file with matching expectedHash required")
        encoding = args.get("encoding", "utf-8-sig" if before.startswith(b"\xef\xbb\xbf") else "utf-8")
        old = before.decode(encoding)
        crlf = old.count("\r\n")
        lf = old.count("\n") - crlf
        cr = old.count("\r") - crlf
        if sum(bool(x) for x in (crlf, lf, cr)) > 1:
            raise Failure("MIXED_LINE_ENDINGS", "Mixed line endings require an IDE-preserving editor")
        eol = "\r\n" if crlf else ("\r" if cr else "\n")

        def normalize(text):
            if not isinstance(text, str):
                raise Failure("INVALID_REQUEST", "Replacement text must be a string")
            return text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", eol)

        if op in {"write", "rewrite"}:
            new = normalize(args["text"])
        else:
            edits = args.get("edits", []) if op == "patch" else [args]
            if not isinstance(edits, list) or not 1 <= len(edits) <= 100:
                raise Failure("INVALID_PATCH", "patch requires 1..100 ordered edits: old, text, expectedCount")
            new = old
            for edit in edits:
                needle, replacement = edit["old"], normalize(edit.get("text", ""))
                regex = edit.get("regex", False)
                if not needle:
                    raise Failure("INVALID_REQUEST", "Empty anchor is forbidden")
                if not regex:
                    needle = normalize(needle)
                count = len(list(re.finditer(needle, new))) if regex else new.count(needle)
                expected = edit.get("expectedCount", 1)
                if not isinstance(expected, int) or expected < 1 or count != expected:
                    raise Failure("MATCH_CONFLICT", "Unexpected match count", {"actual": count, "expected": expected})
                if op == "method-replace" and (regex or expected != 1):
                    raise Failure("INVALID_REQUEST", "method-replace requires one exact, complete old declaration")
                if op == "insert":
                    if regex:
                        raise Failure("INVALID_REQUEST", "insert uses a literal anchor")
                    replacement = replacement + needle if edit.get("before") else needle + replacement
                new = re.sub(needle, replacement, new) if regex else new.replace(needle, replacement)
        after = new.encode(encoding)
        preview = {"path": p.relative_to(self.root).as_posix(), "beforeSHA256": digest(before) if exists else "absent",
                   "plannedSHA256": digest(after), "encoding": encoding, "lineSeparator": repr(eol),
                   "diff": "".join(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
                              fromfile=args["path"] if exists else "/dev/null", tofile=args["path"]))}
        notes = ["Disk-based exact text edit; method-replace does not parse or resolve Java symbols"] if op == "method-replace" else []
        if not req.get("apply"):
            return [preview], "cli", notes
        if exists and args.get("documentState") != "no-unsaved":
            raise Failure("DOCUMENT_STATE_REQUIRED", "Confirm no unsaved editor version with documentState=no-unsaved, or use IDE Document editing")
        if after == before and exists:
            return [{**preview, "changed": False}], "cli", notes
        if exists and p.read_bytes() != before:
            raise Failure("VERSION_CONFLICT", "File changed during planning")
        p.parent.mkdir(parents=True, exist_ok=True)
        receipt = self.root / ".local" / "dev-script-writes" / uuid.uuid4().hex
        receipt.mkdir(parents=True, exist_ok=False)
        if exists:
            (receipt / "before.bin").write_bytes(before)
        (receipt / "plan.json").write_text(json.dumps({k: v for k, v in preview.items() if k != "diff"}, ensure_ascii=False), encoding="utf-8")
        fd, temp = tempfile.mkstemp(prefix=".dev-write-", suffix=".tmp", dir=p.parent)
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(after)
                stream.flush()
                os.fsync(stream.fileno())
            if exists:
                if p.read_bytes() != before:
                    raise Failure("VERSION_CONFLICT", "File changed before replace")
                import stat
                os.chmod(temp, stat.S_IMODE(p.stat().st_mode))
                os.replace(temp, p)
            else:
                # Exclusive creation works without Windows hard-link privileges and never overwrites.
                with p.open("xb") as stream:
                    stream.write(after)
                    stream.flush()
                    os.fsync(stream.fileno())
        finally:
            if os.path.exists(temp):
                os.unlink(temp)
        if p.read_bytes() != after:
            raise Failure("WRITE_VERIFY_FAILED", "Post-write bytes differ; inspect receipt, no automatic rollback", {"receipt": str(receipt)})
        return [{**preview, "changed": True, "receipt": str(receipt)}], "cli", notes

    def semantic(self, op, req, args):
        mapped = {ALIASES.get(k, k): v for k, v in args.items()}
        notes = []
        if op == "symbols":
            mapped.setdefault("scope", "project")
            mapped.setdefault("maxResults", 50)
        if op in {"class", "members", "method-read"}:
            mapped.setdefault("includeMembers", op != "class")
            mapped.setdefault("includeSource", op == "method-read")
        if op == "method-read":
            if not mapped.get("methodName"):
                raise Failure("INVALID_REQUEST", "method required")
            notes.append("Backend selects by method name; inspect overloads, no unique-body editing promise")
        if op in {"callers", "callees"}:
            mapped["direction"] = op
            mapped.setdefault("maxDepth", 1)
            mapped.setdefault("maxResults", 30)
        if op in {"refs", "overrides", "hierarchy", "implementations"}:
            mapped.setdefault("maxResults", 50)
        if op == "symbol":
            mapped["filePath"] = safe_path(self.root, mapped["filePath"]).relative_to(self.root).as_posix()
        if op == "problems":
            mapped["filePath"] = safe_path(self.root, mapped["filePath"]).relative_to(self.root).as_posix()
            mapped.setdefault("timeout", min(30000, int(self.timeout * 1000)))
        if op in REFACTOR:
            if mapped.get("ignoreConflicts"):
                raise Failure("INVALID_REQUEST", "ignoreConflicts=true is not permitted")
            mapped["ignoreConflicts"] = False
            mapped["dryRun"] = True
            if op == "extract":
                safe_path(self.root, mapped["filePath"], writing=True)
        value = self.backend().tool(TOOLS[op], mapped)
        text = text_result(value)
        if op in REFACTOR:
            paths = {}
            for line in text.splitlines():
                match = re.match(r"\s*(?:declared in:\s*)?((?:src|docs|tools|resources)/.+?\.(?:java|kt|kts|json|xml|md|properties|txt))(?=\s|:|$)", line)
                if match:
                    rel = match.group(1)
                    path = safe_path(self.root, rel, writing=True)
                    paths[rel] = digest(path.read_bytes()) if path.exists() else "absent"
            version = {"tool": TOOLS[op], "args": mapped, "preview": sorted(text.splitlines()), "files": paths}
            preview_hash = digest(json.dumps(version, sort_keys=True).encode())
            count_match = re.search(r"Found \d+ usage\(s\) in (\d+) file\(s\)", text)
            complete = bool(count_match) and len(paths) >= int(count_match.group(1))
            if op == "extract":
                complete = mapped.get("filePath") in paths and len(paths) == 1
            metadata = {"previewHash": preview_hash, "affectedDiskFiles": paths,
                        "applyAvailable": bool(paths) and complete and "truncated" not in text.casefold()}
            if not req.get("apply"):
                return [metadata] + text.splitlines(), "idea", ["Dry-run only. Hash binds this preview and reported disk files, not an IDE atomic transaction"]
            if req.get("documentState") != "no-unsaved":
                raise Failure("DOCUMENT_STATE_REQUIRED", "Confirm no unsaved versions in all affected files with top-level documentState=no-unsaved")
            if not metadata["applyAvailable"]:
                raise Failure("CAPABILITY_MISSING", "Preview did not expose a complete usable affected-file list")
            if req.get("expectedPreviewHash") != preview_hash:
                raise Failure("VERSION_CONFLICT", "Preview or affected disk files changed; obtain and review a fresh preview", metadata)
            mapped["dryRun"] = False
            value = self.backend().tool(TOOLS[op], mapped)
            return [{"appliedPreviewHash": preview_hash, "beforeDiskFiles": paths}] + text_result(value).splitlines(), "idea", ["Native IDE refactor; conflicts enforced, no atomic compare-and-swap or automatic rollback"]
        return text.splitlines(), "idea", notes


def main():
    parser = argparse.ArgumentParser(description="External terminal dev toolkit; JSON requests avoid shell quoting")
    parser.add_argument("op", nargs="?", default="help")
    parser.add_argument("query", nargs="?")
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--endpoint", default=os.environ.get("DEV_IDEA_ENDPOINT", DEFAULT_ENDPOINT))
    parser.add_argument("--timeout", type=float, default=30)
    parser.add_argument("--request", help="UTF-8 JSON request, or - for stdin")
    parser.add_argument("--args", default="{}", help="Small JSON args; prefer request file for code")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--format", choices=["summary", "json", "full"], default="summary")
    opts = parser.parse_args()
    runner = None
    try:
        root = Path(opts.root).resolve(strict=True)
        if not root.is_dir() or not 0 < opts.timeout <= 300:
            raise Failure("INVALID_REQUEST", "Project directory and timeout (0,300] required")
        if opts.request:
            raw = sys.stdin.read() if opts.request == "-" else Path(opts.request).read_text(encoding="utf-8-sig")
            req = json.loads(raw)
        else:
            args = json.loads(opts.args)
            if opts.query is not None:
                key = {"read": "path", "symbols": "query", "search": "q", "files": "glob", "package": "name",
                       "class": "class", "members": "class"}.get(opts.op)
                if not key:
                    raise Failure("INVALID_REQUEST", "Use --request or --args for this operation")
                args[key] = opts.query
            req = {"op": opts.op, "args": args}
        if not isinstance(req, dict):
            raise Failure("INVALID_REQUEST", "Top-level request must be an object")
        req.setdefault("limit", opts.limit if opts.limit is not None else (500 if opts.format == "full" else 20))
        req.setdefault("offset", opts.offset)
        if opts.apply:
            req["apply"] = True
        runner = Runner(root, opts.endpoint, opts.timeout)
        result = runner.execute(req)
        if result["ok"] and result["operation"] == "batch":
            if any(not item.get("ok", False) for item in result["items"]):
                result["ok"] = False
        if opts.format == "summary":
            # Bound console output while making omitted content visible and recoverable.
            budget = 10000
            encoded = json.dumps(result, ensure_ascii=False, separators=(",", ":"))
            if len(encoded) > budget:
                result["items"] = [{"preview": encoded[:budget], "note": "Use --format json/full and paging for complete values"}]
                result["output_truncated"] = True
        print(json.dumps(result, ensure_ascii=False, indent=2 if opts.format == "full" else None))
        return 0 if result["ok"] else 2
    except (Failure, OSError, ValueError, TypeError) as e:
        print(json.dumps({"ok": False, "error": {"code": getattr(e, "code", "INVALID_REQUEST"), "message": str(e)}}, ensure_ascii=False))
        return 2
    finally:
        if runner:
            runner.close()


if __name__ == "__main__":
    for stream in (sys.stdin, sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    sys.exit(main())
