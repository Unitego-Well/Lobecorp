import { createRequire } from "node:module"; const require = createRequire(import.meta.url);

// scripts/report-resource-acceptance.mjs
import { readFile, writeFile, readdir } from "fs/promises";
import { resolve, dirname, join } from "node:path";
var [rawPath, projectPath, runtimeDirectory, prefix] = process.argv.slice(2).map((value, index) => index < 3 ? resolve(value) : value);
if (!prefix || !/^[a-z0-9_]+$/.test(prefix)) {
  throw new Error("Explicit runtime report prefix required");
}
var raw = JSON.parse(await readFile(rawPath, "utf8"));
var projects = JSON.parse(await readFile(projectPath, "utf8"));
var runtime = [];
for (const file of (await readdir(runtimeDirectory)).filter((name) => name.startsWith(prefix + "_") && name.endsWith(".json")).sort()) {
  const path = join(runtimeDirectory, file);
  runtime.push({ path, data: JSON.parse(await readFile(path, "utf8")) });
}
var normalize = (value) => value?.replaceAll("\\", "/").replaceAll(/\/+/g, "/").toLowerCase();
function executionPassed(data) {
  return !data.beginFailure && data.developmentSourceDisabled === true && data.canaryAbsent === true && data.closedOwnedInstances === true && data.finishedAtGameTime > data.beganAtGameTime && data.runtime?.length > 0 && data.runtime.every((item) => item.started === true && !item.childFailures?.length) && data.entries?.length > 0 && data.entries.filter((item) => item.entry.startsWith("assets/")).every((item) => item.sourcePackId === data.expectedPackId && item.actualSha256 === item.sha256);
}
var rows = raw.map((original) => {
  const row = structuredClone(original);
  row.visual = "human_review";
  const project = projects.rows.find((item) => item.root === row.root && item.source === row.source && item.sha256 === row.sha256);
  if (project) {
    row.nativeRoundTrip = project.status;
    row.nativeChecks = project.checks;
    row.nativeReceipt = projectPath;
    row.nativeError = project.error;
  }
  const direct = runtime.find((item) => normalize(item.data.package) === normalize(row.runtimePackPath));
  const exported = project?.pack?.path && runtime.find((item) => normalize(item.data.package) === normalize(project.pack.path));
  const evidence = direct || exported;
  if (evidence) {
    const packRow = row.entry ? raw.find((item) => item.root === row.root && item.source === row.source && !item.entry) : row;
    const correctPackage = !direct || evidence.data.packageSha256 === packRow.sha256;
    row.independentRuntime = correctPackage && executionPassed(evidence.data) ? "passed_execution_source_checks" : "failed";
    row.runtimeReceipt = evidence.path;
    row.dependencyClosure = evidence.data.dependencyReport?.closed === true ? "closed" : "needs_review";
    row.runtimeError = evidence.data.beginFailure;
  }
  return row;
});
var counts = (key) => rows.reduce((result2, row) => {
  const value = row[key] || "not_run";
  result2[value] = (result2[value] || 0) + 1;
  return result2;
}, {});
var result = {
  generatedAt: (/* @__PURE__ */ new Date()).toISOString(),
  g1Passed: false,
  evidence: { rawPath, projectPath, runtimeDirectory, prefix },
  counts: { raw: counts("rawRoundTrip"), native: counts("nativeRoundTrip"), runtime: counts("independentRuntime") },
  visual: "human_review",
  rows
};
var target = join(dirname(projectPath), "resource_acceptance_matrix.json");
await writeFile(target, JSON.stringify(result, null, 2), "utf8");
var escape = (value) => String(value || "").replaceAll("|", "\\|").replaceAll(/\r?\n/g, " ");
var markdown = [
  "# G1 resource acceptance matrix",
  "",
  "G1 is not automatically passed. Raw round trips, native editing, package execution and visual review are separate.",
  "",
  "| Root | Source | Package entry | SHA256 | Raw | Native edit | Independent execution | Dependency closure |",
  "| --- | --- | --- | --- | --- | --- | --- | --- |",
  ...rows.map((row) => "| " + [
    row.root,
    row.source,
    row.entry,
    row.sha256,
    row.rawRoundTrip,
    row.nativeRoundTrip,
    row.independentRuntime,
    row.dependencyClosure
  ].map(escape).join(" | ") + " |"),
  ""
].join("\n");
await writeFile(join(dirname(projectPath), "resource_acceptance_matrix.md"), markdown, "utf8");
console.log(JSON.stringify({ path: target, rows: rows.length, counts: result.counts, g1Passed: false }));
