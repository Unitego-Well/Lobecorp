#!/usr/bin/env node
import {
  GAME,
  JAVA,
  PATHS,
  init_esm_shims
} from "./chunk-GFHVXK2C.js";

// src/mc/settings.ts
init_esm_shims();
import { existsSync as existsSync3, readFileSync as readFileSync2, writeFileSync as writeFileSync2, mkdirSync as mkdirSync2 } from "node:fs";
import { join as join3, dirname as dirname2 } from "node:path";

// src/runtime/detector.ts
init_esm_shims();
function crossHomedir() {
  const env = process.env;
  if (env.HOME) return env.HOME;
  if (env.USERPROFILE) return env.USERPROFILE;
  if (typeof globalThis.Deno !== "undefined") {
    return globalThis.Deno.env.get("HOME") || globalThis.Deno.env.get("USERPROFILE") || "/";
  }
  return "/";
}
function isWindows() {
  return process.platform === "win32";
}
function isMacos() {
  return process.platform === "darwin";
}
function classpathSeparator() {
  return process.platform === "win32" ? ";" : ":";
}

// src/mc/platform.ts
init_esm_shims();
import { join as join2, resolve } from "node:path";
import { existsSync as existsSync2, readdirSync as readdirSync2, mkdirSync, writeFileSync } from "node:fs";

// src/mc/javaDetect.ts
init_esm_shims();
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
function detectJavas() {
  const results = [];
  const seen = /* @__PURE__ */ new Set();
  const candidates = [
    ...envCandidates(),
    ...gradleCandidates(),
    ...platformCandidates()
  ].filter((p) => existsSync(p));
  for (const home of candidates) {
    try {
      const canonical = home;
      if (seen.has(canonical)) continue;
      seen.add(canonical);
    } catch {
      continue;
    }
    const javaBin = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
    if (!existsSync(javaBin)) continue;
    const parsed = parseReleaseFile(home);
    if (parsed) {
      const isJdk = existsSync(join(home, "lib", "tools.jar")) || existsSync(join(home, "include"));
      results.push({ path: home, version: parsed.version, vendor: parsed.vendor, isJdk });
    }
  }
  results.sort((a, b) => b.version - a.version);
  return results;
}
function envCandidates() {
  const paths = [];
  for (const var_ of ["JAVA_HOME", "JDK_8_HOME", "JDK_16_HOME", "JDK_17_HOME", "JDK_21_HOME", "JDK_25_HOME"]) {
    const val = process.env[var_];
    if (val && existsSync(val)) paths.push(val);
  }
  return paths;
}
function gradleCandidates() {
  const paths = [];
  const jdksDir = join(crossHomedir(), ".gradle", "jdks");
  if (!existsSync(jdksDir)) return paths;
  try {
    for (const entry of readdirSync(jdksDir, { withFileTypes: true })) {
      if (entry.isDirectory() && !entry.name.includes("lock")) {
        const candidate = join(jdksDir, entry.name);
        if (existsSync(join(candidate, "release"))) paths.push(candidate);
      }
    }
  } catch {
  }
  return paths;
}
function platformCandidates() {
  if (isWindows()) return windowsCandidates();
  if (isMacos()) return macosCandidates();
  return linuxCandidates();
}
function windowsCandidates() {
  const paths = [];
  const sd = process.env.SystemDrive || "C:";
  const pf = process.env.ProgramFiles || join(sd, "Program Files");
  const pf86 = process.env["ProgramFiles(x86)"] || join(sd, "Program Files (x86)");
  const roots = [
    join(pf, "Java"),
    join(pf, "Eclipse Adoptium"),
    join(pf, "Zulu"),
    join(pf, "Microsoft"),
    join(pf, "AdoptOpenJDK"),
    join(pf86, "Java")
  ];
  for (const root of roots) {
    scanSubdirs(root, paths);
  }
  const userProfile = process.env.USERPROFILE;
  if (userProfile) {
    for (const sub of [join("scoop", "apps", "java"), join("scoop", "apps", "openjdk")]) {
      const dir = join(userProfile, sub);
      if (existsSync(dir)) scanSubdirsDirect(dir, paths);
    }
  }
  return paths;
}
function macosCandidates() {
  const paths = [];
  const jvms = "/Library/Java/JavaVirtualMachines";
  if (!existsSync(jvms)) return paths;
  try {
    for (const entry of readdirSync(jvms, { withFileTypes: true })) {
      const home = join(jvms, entry.name, "Contents", "Home");
      if (existsSync(home)) paths.push(home);
    }
  } catch {
  }
  return paths;
}
function linuxCandidates() {
  const paths = [];
  for (const root of ["/usr/lib/jvm", "/usr/java", "/usr/local/java", "/opt/java", "/opt/jdk"]) {
    if (!existsSync(root)) continue;
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        const p = join(root, entry.name);
        if (entry.isDirectory() && (existsSync(join(p, "release")) || existsSync(join(p, "bin", "java")))) {
          paths.push(p);
        }
      }
    } catch {
    }
  }
  return paths;
}
function scanSubdirs(root, paths) {
  if (!existsSync(root)) return;
  try {
    for (const entry of readdirSync(root, { withFileTypes: true })) {
      const p = join(root, entry.name);
      if (!entry.isDirectory()) continue;
      if (existsSync(join(p, "release"))) paths.push(p);
      else scanSubdirsDirect(p, paths);
    }
  } catch {
  }
}
function scanSubdirsDirect(dir, paths) {
  try {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const p = join(dir, entry.name);
      if (!entry.isDirectory()) continue;
      if (existsSync(join(p, "release"))) paths.push(p);
      else scanSubdirsDirect(p, paths);
    }
  } catch {
  }
}
function parseReleaseFile(home) {
  const releasePath = join(home, "release");
  if (!existsSync(releasePath)) return null;
  try {
    const content = readFileSync(releasePath, "utf-8");
    let javaVersion = null;
    let implementor = "Unknown";
    for (const line of content.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed.startsWith("JAVA_VERSION=")) {
        javaVersion = stripQuotes(trimmed.slice("JAVA_VERSION=".length));
      } else if (trimmed.startsWith("IMPLEMENTOR=")) {
        implementor = stripQuotes(trimmed.slice("IMPLEMENTOR=".length));
      }
    }
    if (!javaVersion) return null;
    const major = parseJavaMajorVersion(javaVersion);
    if (major === null) return null;
    return { version: major, vendor: implementor };
  } catch {
    return null;
  }
}
function stripQuotes(s) {
  const t = s.trim();
  if (t.startsWith('"') && t.endsWith('"') || t.startsWith("'") && t.endsWith("'")) {
    return t.slice(1, -1);
  }
  return t;
}
function parseJavaMajorVersion(version) {
  const parts = version.split(".");
  if (parts.length === 0) return null;
  if (parts[0] === "1" && parts.length >= 2) return parseInt(parts[1], 10) || null;
  return parseInt(parts[0], 10) || null;
}

// src/mc/platform.ts
function homedir() {
  return crossHomedir();
}
var _mcDir = null;
function mcDir() {
  if (_mcDir) return _mcDir;
  const candidates = [];
  if (isWindows() && process.env.APPDATA) {
    candidates.push(join2(process.env.APPDATA, PATHS.mcDirName));
  }
  candidates.push(join2(homedir(), PATHS.mcDirName));
  for (const dir of candidates) {
    if (existsSync2(dir) && existsSync2(join2(dir, PATHS.versionsDirName))) {
      _mcDir = dir;
      return dir;
    }
  }
  const fallback = candidates[0] || join2(homedir(), PATHS.mcDirName);
  _mcDir = fallback;
  return fallback;
}
function versionsDir() {
  return join2(mcDir(), PATHS.versionsDirName);
}
function librariesDir() {
  return join2(mcDir(), PATHS.librariesDirName);
}
function assetsDir() {
  return join2(mcDir(), PATHS.assetsDirName);
}
function nativesDir(versionId) {
  return join2(mcDir(), PATHS.versionsDirName, versionId, PATHS.nativesDirName);
}
function launcherDir() {
  return join2(mcDir(), PATHS.launcherDirName);
}
function modJarPath(mcVersion, loader) {
  const projectDir = resolve("packages", "mods", mcVersion, loader);
  const libsDir = join2(projectDir, "build", "libs");
  if (!existsSync2(libsDir)) return null;
  try {
    const jars = readdirSync2(libsDir).filter((f) => f.endsWith(".jar") && !f.endsWith("-sources.jar") && !f.endsWith("-javadoc.jar"));
    if (jars.length > 0) {
      jars.sort((a, b) => b.length - a.length);
      return join2(libsDir, jars[0]);
    }
  } catch {
  }
  return null;
}
async function ensureModJar(mcVersion, loader) {
  const local = modJarPath(mcVersion, loader);
  if (local) return local;
  const cacheDir = join2(launcherDir(), "modjars");
  const cachePath = join2(cacheDir, `minecraft-mcp-${mcVersion}-${loader}.jar`);
  if (existsSync2(cachePath)) return cachePath;
  try {
    const apiResp = await fetch("https://api.github.com/repos/langyo/minecraft-mod-mcp/releases/latest", {
      headers: { "user-agent": "minecraft-mod-mcp", accept: "application/json" }
    });
    if (!apiResp.ok) return null;
    const rel = await apiResp.json();
    const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const pattern = new RegExp(`^minecraft-mcp-${esc(mcVersion)}-${esc(loader)}(-v[\\d.]+)?\\.jar$`);
    const asset = rel.assets?.find((a) => pattern.test(a.name));
    if (!asset) return null;
    const dlResp = await fetch(asset.browser_download_url);
    if (!dlResp.ok) return null;
    if (!existsSync2(cacheDir)) mkdirSync(cacheDir, { recursive: true });
    writeFileSync(cachePath, Buffer.from(await dlResp.arrayBuffer()));
    return cachePath;
  } catch {
    return null;
  }
}
function jdkHome(javaVersion) {
  const envVar = `JDK_${javaVersion}_HOME`;
  const envVal = process.env[envVar];
  if (envVal && existsSync2(envVal)) return envVal;
  const mcJavaDir = join2(launcherDir(), PATHS.javaDirName, `jdk-${javaVersion}`);
  if (existsSync2(mcJavaDir)) {
    try {
      const jdk = readdirSync2(mcJavaDir, { withFileTypes: true }).find((e) => e.isDirectory() && !e.name.startsWith("."));
      if (jdk) {
        const home = join2(mcJavaDir, jdk.name);
        const exe = isWindows() ? join2(home, "bin", "java.exe") : join2(home, "bin", "java");
        if (existsSync2(exe)) return home;
      }
    } catch {
    }
  }
  const jdksDir = join2(homedir(), PATHS.gradleJdksSubdir);
  if (!existsSync2(jdksDir)) return null;
  const prefixes = JAVA.jdkDirPrefixes;
  const prefix = prefixes[javaVersion];
  if (!prefix) return null;
  try {
    for (const entry of readdirSync2(jdksDir, { withFileTypes: true })) {
      if (entry.isDirectory() && entry.name.startsWith(prefix) && !entry.name.includes("lock")) {
        return join2(jdksDir, entry.name);
      }
    }
  } catch {
  }
  return null;
}
function findJavaForVersion(targetVersion) {
  const home = jdkHome(targetVersion);
  if (home) {
    const exe = isWindows() ? join2(home, "bin", "java.exe") : join2(home, "bin", "java");
    if (existsSync2(exe)) return exe;
  }
  const all = detectJavas();
  const sorted = [...all].sort((a, b) => a.version - b.version);
  const pick = sorted.find((j) => j.version === targetVersion) ?? sorted.find((j) => j.version >= targetVersion) ?? (sorted.length > 0 ? sorted[sorted.length - 1] : null);
  if (pick) {
    const exe = isWindows() ? join2(pick.path, "bin", "java.exe") : join2(pick.path, "bin", "java");
    if (existsSync2(exe)) return exe;
  }
  return isWindows() ? "java.exe" : "java";
}
function classpathSeparator2() {
  return classpathSeparator();
}

// src/mc/settings.ts
function defaultConfig() {
  return {
    max_memory_mb: GAME.defaultMaxMemoryMb,
    min_memory_mb: GAME.defaultMinMemoryMb,
    width: GAME.defaultWidth,
    height: GAME.defaultHeight,
    fullscreen: false,
    accounts: [],
    download_source: GAME.defaultDownloadSource,
    language: GAME.defaultLanguage
  };
}
function configPath() {
  return join3(launcherDir(), "config.json");
}
function loadConfig() {
  const path = configPath();
  if (!existsSync3(path)) {
    const cfg = defaultConfig();
    saveConfig(cfg);
    return cfg;
  }
  try {
    const content = readFileSync2(path, "utf-8");
    return { ...defaultConfig(), ...JSON.parse(content) };
  } catch {
    return defaultConfig();
  }
}
function saveConfig(config) {
  const path = configPath();
  const dir = dirname2(path);
  if (!existsSync3(dir)) mkdirSync2(dir, { recursive: true });
  writeFileSync2(path, JSON.stringify(config, null, 2), "utf-8");
}
function selectedAccount(config) {
  if (!config.selected_account) return void 0;
  return config.accounts.find((a) => accountUuid(a) === config.selected_account);
}
function addAccount(config, account) {
  const uuid = accountUuid(account);
  const idx = config.accounts.findIndex((a) => accountUuid(a) === uuid);
  if (idx >= 0) config.accounts[idx] = account;
  else config.accounts.push(account);
}
function removeAccount(config, uuid) {
  config.accounts = config.accounts.filter((a) => accountUuid(a) !== uuid);
  if (config.selected_account === uuid) {
    config.selected_account = config.accounts[0] ? accountUuid(config.accounts[0]) : void 0;
  }
}
function accountUuid(a) {
  return a.uuid;
}
function accountUsername(a) {
  return a.username;
}
function accountAccessToken(a) {
  return a.type === "microsoft" ? a.access_token : "";
}
function accountUserType(a) {
  return a.type === "microsoft" ? "msa" : "legacy";
}
function gameDirPath(config) {
  if (config.game_dir) return config.game_dir;
  return join3(launcherDir(), PATHS.gameDirName);
}
function serverDir(versionId) {
  const base = join3(launcherDir(), PATHS.serverDirName);
  return versionId ? join3(base, versionId) : base;
}
function javaExecPath(config) {
  if (!config.java_dir) return void 0;
  const base = config.java_dir;
  return isWindows() ? join3(base, "bin", "java.exe") : join3(base, "bin", "java");
}

export {
  crossHomedir,
  isWindows,
  isMacos,
  detectJavas,
  mcDir,
  versionsDir,
  librariesDir,
  assetsDir,
  nativesDir,
  launcherDir,
  ensureModJar,
  jdkHome,
  findJavaForVersion,
  classpathSeparator2 as classpathSeparator,
  defaultConfig,
  configPath,
  loadConfig,
  saveConfig,
  selectedAccount,
  addAccount,
  removeAccount,
  accountUuid,
  accountUsername,
  accountAccessToken,
  accountUserType,
  gameDirPath,
  serverDir,
  javaExecPath
};
