#!/usr/bin/env node
import {
  DEFAULT_FABRIC_LOADER_VERSION,
  downloadFabricLoader,
  downloadFile,
  downloadForgeInstaller,
  downloadNeoforgeInstaller,
  fetchWithFallback,
  getVersion,
  getVersionForLoader,
  gradleProxyEnv,
  hasFabric,
  javaProxyArgs,
  loadVersion,
  loadVersionsData
} from "./chunk-V6WRGNQD.js";
import {
  findJavaForVersion,
  isMacos,
  isWindows,
  serverDir
} from "./chunk-HRWYZQKF.js";
import {
  BUILD,
  DOWNLOAD,
  FABRIC,
  GAME,
  SERVER,
  init_esm_shims
} from "./chunk-GFHVXK2C.js";

// src/mc/server.ts
init_esm_shims();
import { existsSync, mkdirSync, readFileSync, writeFileSync, openSync, copyFileSync, readdirSync, symlinkSync, rmSync } from "node:fs";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { execSync } from "node:child_process";
import { spawn } from "node:child_process";
var MANIFEST_URL = DOWNLOAD.versionManifestV2Url;
function generateServerProperties(overrides) {
  const props = {
    "enable-jmx-monitoring": "false",
    "rcon.port": String(SERVER.rconPort),
    "level-seed": overrides.levelSeed ?? "",
    "gamemode": overrides.gamemode ?? "survival",
    "enable-command-block": "true",
    "enable-query": "false",
    "generator-settings": `{"layers":[{"block":"minecraft:stone","height":1}],"biome":"minecraft:the_void"}`,
    "level-name": overrides.levelName ?? "world",
    "motd": overrides.motd ?? "MCP Server",
    "query.port": String(overrides.serverPort ?? GAME.defaultServerPort),
    "pvp": String(overrides.pvp ?? true),
    "generate-structures": String(overrides.generateStructures ?? false),
    "max-chained-neighbor-updates": "1000000",
    "difficulty": overrides.difficulty ?? "easy",
    "network-compression-threshold": String(SERVER.networkCompressionThreshold),
    "max-tick-time": String(SERVER.maxTickTime),
    "require-resource-pack": "false",
    "use-native-transport": "true",
    "max-players": String(overrides.maxPlayers ?? SERVER.maxPlayers),
    "online-mode": String(overrides.onlineMode ?? false),
    "enable-status": "true",
    "allow-flight": "true",
    "initial-disabled-packs": "",
    "broadcast-rcon-to-ops": "true",
    "view-distance": String(overrides.viewDistance ?? SERVER.viewDistance),
    "resource-pack": "",
    "server-ip": overrides.bindAddress ?? SERVER.bindAddress,
    "resource-pack-prompt": "",
    "allow-nether": "true",
    "server-port": String(overrides.serverPort ?? GAME.defaultServerPort),
    "enable-rcon": "false",
    "sync-chunk-writes": "true",
    "op-permission-level": String(overrides.opPermissionLevel ?? 4),
    "prevent-proxy-connections": "false",
    "hide-online-players": "false",
    "resource-pack-sha1": "",
    "entity-broadcast-range-percentage": "100",
    "simulation-distance": String(overrides.simulationDistance ?? SERVER.simulationDistance),
    "rcon.password": "",
    "player-idle-timeout": "0",
    "force-gamemode": "false",
    "rate-limit": "0",
    "hardcore": "false",
    "white-list": String(overrides.whiteList ?? false),
    "broadcast-console-to-ops": "true",
    "spawn-npcs": "true",
    "spawn-animals": "true",
    "function-permission-level": "2",
    "initial-enabled-packs": "vanilla",
    "level-type": overrides.levelType ?? "flat",
    "text-filtering-config": "",
    "spawn-monsters": "true",
    "enforce-whitelist": String(overrides.enforceWhitelist ?? false),
    "max-world-size": String(SERVER.maxWorldSize)
  };
  return Object.entries(props).map(([k, v]) => `${k}=${v}`).join("\n") + "\n";
}
async function fetchJson(url) {
  const mod = await import("https");
  return new Promise((resolve, reject) => {
    mod.get(url, { headers: { "User-Agent": SERVER.userAgent } }, (res) => {
      if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        fetchJson(res.headers.location).then(resolve, reject);
        return;
      }
      let data = "";
      res.on("data", (c) => data += c);
      res.on("end", () => {
        try {
          resolve(JSON.parse(data));
        } catch {
          reject(new Error(`Invalid JSON from ${url}`));
        }
      });
      res.on("error", reject);
    }).on("error", reject);
  });
}
async function fetchVanillaServerUrl(mcVersion) {
  const manifest = await fetchJson(MANIFEST_URL);
  const entry = manifest.versions?.find((v) => v.id === mcVersion);
  if (!entry?.url) throw new Error(`MC ${mcVersion} not found in version manifest`);
  const vj = await fetchJson(entry.url);
  const server = vj?.downloads?.server;
  if (!server?.url) throw new Error(`No server download for ${mcVersion}`);
  return { url: server.url, sha1: server.sha1 };
}
async function ensureVanillaServerJar(mcVersion, sDir, onProgress) {
  let serverUrl;
  let serverSha;
  try {
    const baseVj = loadVersion(mcVersion);
    serverUrl = baseVj.downloads?.server?.url;
    serverSha = baseVj.downloads?.server?.sha1;
  } catch {
  }
  if (!serverUrl) {
    onProgress?.(`Fetching server info for ${mcVersion} from Mojang...`);
    const info = await fetchVanillaServerUrl(mcVersion);
    serverUrl = info.url;
    serverSha = info.sha1;
  }
  const jarPath = join(sDir, `server-${mcVersion}.jar`);
  if (!existsSync(jarPath)) {
    if (!serverUrl) throw new Error(`No server download available for ${mcVersion}`);
    onProgress?.(`Downloading server JAR for ${mcVersion}...`);
    await downloadFile(serverUrl, jarPath, serverSha);
  } else {
    onProgress?.(`Server JAR already exists.`);
  }
  return jarPath;
}
async function downloadPaperServer(mcVersion, sDir, onProgress) {
  onProgress?.(`Querying Paper API for ${mcVersion}...`);
  const projectData = await fetchJson(SERVER.paperApiUrl);
  const versionEntry = projectData.versions?.find((v) => v === mcVersion);
  if (!versionEntry) throw new Error(`Paper does not support MC ${mcVersion}`);
  const buildsData = await fetchJson(`${SERVER.paperApiUrl}/versions/${mcVersion}/builds`);
  const builds = buildsData.builds;
  if (!builds || builds.length === 0) throw new Error(`No Paper builds found for ${mcVersion}`);
  const latestBuild = builds[builds.length - 1];
  const appName = Object.keys(latestBuild.downloads)[0];
  if (!appName) throw new Error(`No download found in Paper build ${latestBuild.build}`);
  const download = latestBuild.downloads[appName];
  const url = `${SERVER.paperApiUrl}/versions/${mcVersion}/builds/${latestBuild.build}/downloads/${download.name}`;
  const jarPath = join(sDir, `paper-${mcVersion}-${latestBuild.build}.jar`);
  if (!existsSync(jarPath)) {
    onProgress?.(`Downloading Paper ${mcVersion} build ${latestBuild.build}...`);
    await downloadFile(url, jarPath);
  } else {
    onProgress?.(`Paper server JAR already exists.`);
  }
  return jarPath;
}
function buildCacheDir() {
  const d = join(tmpdir(), "minecraft-mcp-build");
  if (!existsSync(d)) mkdirSync(d, { recursive: true });
  return d;
}
var PORTABLE_GIT_DIR = BUILD.portableGitDirName;
function findSystemGitDir() {
  const gitExe = isWindows() ? "git.exe" : "git";
  const candidates = [];
  try {
    const which = execSync(`${isWindows() ? "where" : "which"} ${gitExe}`, { encoding: "utf-8", timeout: 5e3 }).trim();
    const first = which.split(/\r?\n/)[0].trim();
    if (first) {
      const gitDir = dirname(dirname(first));
      if (existsSync(join(gitDir, "bin", gitExe))) return gitDir;
    }
  } catch {
  }
  if (isWindows()) {
    const pf = process.env.ProgramFiles;
    const pf86 = process.env["ProgramFiles(x86)"];
    const localApp = process.env.LocalAppData;
    if (pf) candidates.push(join(pf, "Git"));
    if (pf86) candidates.push(join(pf86, "Git"));
    if (localApp) candidates.push(join(localApp, "Programs", "Git"));
    candidates.push(join(process.env.SystemDrive || "C:", "Git"));
  } else if (isMacos()) {
    candidates.push(
      "/usr/local",
      "/opt/homebrew",
      "/usr"
    );
    try {
      const brew = execSync("brew --prefix git 2>/dev/null", { encoding: "utf-8", timeout: 5e3 }).trim();
      if (brew) candidates.push(brew);
    } catch {
    }
  } else {
    candidates.push(
      "/usr",
      "/usr/local"
    );
  }
  for (const dir of candidates) {
    if (existsSync(join(dir, "bin", gitExe))) return dir;
  }
  return null;
}
function linkSystemGit(workDir) {
  const wrapper = join(workDir, PORTABLE_GIT_DIR);
  const gitLink = join(wrapper, "PortableGit");
  const gitExe = isWindows() ? "git.exe" : "git";
  if (existsSync(join(gitLink, "bin", gitExe))) return;
  const sysGit = findSystemGitDir();
  if (!sysGit) return;
  if (!existsSync(wrapper)) mkdirSync(wrapper, { recursive: true });
  if (existsSync(gitLink)) {
    try {
      rmSync(gitLink, { recursive: true, force: true });
    } catch {
      return;
    }
  }
  try {
    symlinkSync(sysGit, gitLink, isWindows() ? "junction" : "dir");
  } catch {
  }
}
async function runBuildTools(mcVersion, sDir, javaPath, targets, onProgress) {
  const jarName = targets[0] === "craftbukkit" ? `craftbukkit-${mcVersion}.jar` : `spigot-${mcVersion}.jar`;
  const jarPath = join(sDir, jarName);
  if (existsSync(jarPath)) {
    onProgress?.(`${jarName} already exists.`);
    return jarPath;
  }
  const workDir = join(buildCacheDir(), `${mcVersion}-${targets[0]}`);
  if (!existsSync(workDir)) mkdirSync(workDir, { recursive: true });
  const buildToolsJar = join(workDir, "BuildTools.jar");
  if (!existsSync(buildToolsJar)) {
    onProgress?.(`Downloading BuildTools...`);
    await downloadFile(SERVER.buildToolsUrl, buildToolsJar);
  }
  linkSystemGit(workDir);
  onProgress?.(`Compiling ${targets.join(" + ")} for ${mcVersion} via BuildTools (may take a few minutes)...`);
  const proxyArgs = javaProxyArgs();
  const sslArgs = proxyArgs.length > 0 && isWindows() ? ["-Djavax.net.ssl.trustStoreType=Windows-ROOT"] : [];
  const args = [...proxyArgs, ...sslArgs, "-jar", buildToolsJar, "--rev", mcVersion, ...targets.map((t) => `--compile ${t}`).flatMap((s) => s.split(" "))];
  return new Promise((resolve, reject) => {
    const child = spawn(javaPath, args, { cwd: workDir, stdio: ["ignore", "pipe", "pipe"], env: { ...process.env, ...gradleProxyEnv() } });
    let stderr = "";
    child.stdout?.on("data", (c) => {
      const msg = c.toString().trim();
      if (msg) onProgress?.(msg);
    });
    child.stderr?.on("data", (c) => {
      stderr += c.toString();
    });
    child.on("close", (code) => {
      let found = null;
      for (const sub of [workDir, join(workDir, "Spigot"), join(workDir, "CraftBukkit"), "."]) {
        const candidate = join(sub, jarName);
        if (existsSync(candidate)) {
          found = candidate;
          break;
        }
      }
      if (found) {
        mkdirSync(sDir, { recursive: true });
        copyFileSync(found, jarPath);
        onProgress?.(`${jarName} compiled successfully.`);
        resolve(jarPath);
        return;
      }
      reject(new Error(`BuildTools failed (exit ${code}): ${stderr.slice(-500)}`));
    });
    child.on("error", reject);
  });
}
async function installForgeServer(mcVersion, forgeVersion, sDir, javaPath, onProgress) {
  const vanillaJar = await ensureVanillaServerJar(mcVersion, sDir, onProgress);
  const serverJarName = `forge-${mcVersion}-${forgeVersion}-server.jar`;
  const serverJarPath = join(sDir, serverJarName);
  if (existsSync(serverJarPath)) {
    onProgress?.(`Forge server JAR already exists.`);
    return serverJarPath;
  }
  const forgeMavenBase = `${DOWNLOAD.forgeMavenUrl}net/minecraftforge/forge/${forgeVersion}`;
  const installerUrl = `${forgeMavenBase}/forge-${forgeVersion}-installer.jar`;
  const installerPath = join(sDir, `forge-${forgeVersion}-installer.jar`);
  if (!existsSync(installerPath)) {
    onProgress?.(`Downloading Forge installer ${forgeVersion}...`);
    await downloadFile(installerUrl, installerPath);
  }
  onProgress?.(`Running Forge server installer...`);
  await new Promise((resolve, reject) => {
    const proxyArgs = javaProxyArgs();
    const child = spawn(javaPath, [...proxyArgs, "-jar", installerPath, "--installServer", sDir], {
      cwd: sDir,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stderr = "";
    child.stdout?.on("data", (c) => {
      const msg = c.toString().trim();
      if (msg) onProgress?.(msg);
    });
    child.stderr?.on("data", (c) => {
      stderr += c.toString();
    });
    child.on("close", (code) => {
      if (code !== 0 && !existsSync(serverJarPath)) {
        const altNames = [
          `forge-${forgeVersion}.jar`,
          `${mcVersion}-${forgeVersion}-server.jar`
        ];
        for (const alt of altNames) {
          const altPath = join(sDir, alt);
          if (existsSync(altPath)) {
            resolve();
            return;
          }
        }
      }
      if (existsSync(serverJarPath)) resolve();
      else {
        for (const entry of readdirSync(sDir)) {
          if (entry.includes("forge") && entry.endsWith(".jar") && !entry.includes("installer")) {
            resolve();
            return;
          }
        }
        reject(new Error(`Forge server install failed (exit ${code}): ${stderr.slice(-500)}`));
      }
    });
    child.on("error", reject);
  });
  for (const entry of readdirSync(sDir)) {
    if (entry.includes("forge") && entry.endsWith(".jar") && !entry.includes("installer")) {
      return join(sDir, entry);
    }
  }
  return serverJarPath;
}
async function installFabricServer(mcVersion, sDir, javaPath, onProgress) {
  const vanillaJar = await ensureVanillaServerJar(mcVersion, sDir, onProgress);
  for (const name of ["fabric-server-launch.jar", `fabric-server-launch-${mcVersion}.jar`]) {
    if (existsSync(join(sDir, name))) {
      onProgress?.(`Fabric server JAR already exists.`);
      return join(sDir, name);
    }
  }
  const metaResp = await fetchWithFallback(`${DOWNLOAD.fabricMetaUrl}/${mcVersion}`);
  if (!metaResp.ok) throw new Error(`Failed to fetch Fabric loaders for ${mcVersion}: HTTP ${metaResp.status}`);
  const loaders = await metaResp.json();
  const stableLoader = loaders.find((l) => l.loader.stable);
  const loaderVer = stableLoader?.loader.version ?? loaders[0]?.loader.version;
  if (!loaderVer) throw new Error(`No Fabric loader found for ${mcVersion}`);
  const instVer = FABRIC.defaultInstallerVersion;
  const installerUrl = `${FABRIC.mavenBaseUrl}/net/fabricmc/fabric-installer/${instVer}/fabric-installer-${instVer}.jar`;
  const installerPath = join(sDir, "fabric-installer.jar");
  if (!existsSync(installerPath)) {
    onProgress?.(`Downloading Fabric installer...`);
    await downloadFile(installerUrl, installerPath);
  }
  onProgress?.(`Running Fabric server installer (loader ${loaderVer})...`);
  await new Promise((resolve, reject) => {
    const child = spawn(javaPath, [
      "-jar",
      installerPath,
      "server",
      "-mcversion",
      mcVersion,
      "-loader",
      loaderVer,
      "-dir",
      sDir,
      "-downloadMinecraft"
    ], { cwd: sDir, stdio: ["ignore", "pipe", "pipe"] });
    let stderr = "";
    child.stdout?.on("data", (c) => {
      const msg = c.toString().trim();
      if (msg) onProgress?.(msg);
    });
    child.stderr?.on("data", (c) => {
      stderr += c.toString();
    });
    child.on("close", (code) => {
      if (existsSync(join(sDir, "fabric-server-launch.jar"))) resolve();
      else reject(new Error(`Fabric installer failed (exit ${code}): ${stderr.slice(-500)}`));
    });
    child.on("error", reject);
  });
  return join(sDir, "fabric-server-launch.jar");
}
async function installNeoForgeServer(neoforgeVersion, mcVersion, sDir, javaPath, onProgress) {
  const vanillaJar = await ensureVanillaServerJar(mcVersion, sDir, onProgress);
  const serverJarName = `neoforge-${neoforgeVersion}-server.jar`;
  const serverJarPath = join(sDir, serverJarName);
  if (existsSync(serverJarPath)) {
    onProgress?.(`NeoForge server JAR already exists.`);
    return serverJarPath;
  }
  const installerUrl = `${DOWNLOAD.neoforgeMavenUrl}net/neoforged/neoforge/${neoforgeVersion}/neoforge-${neoforgeVersion}-installer.jar`;
  const installerPath = join(sDir, `neoforge-${neoforgeVersion}-installer.jar`);
  if (!existsSync(installerPath)) {
    onProgress?.(`Downloading NeoForge installer ${neoforgeVersion}...`);
    await downloadFile(installerUrl, installerPath);
  }
  onProgress?.(`Running NeoForge server installer...`);
  const proxyArgs = javaProxyArgs();
  await new Promise((resolve, reject) => {
    const child = spawn(javaPath, [...proxyArgs, "-jar", installerPath, "--installServer", sDir], {
      cwd: sDir,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stderr = "";
    child.stdout?.on("data", (c) => {
      const msg = c.toString().trim();
      if (msg) onProgress?.(msg);
    });
    child.stderr?.on("data", (c) => {
      stderr += c.toString();
    });
    child.on("close", (code) => {
      for (const entry of readdirSync(sDir)) {
        if ((entry.includes("neoforge") || entry.includes("server")) && entry.endsWith(".jar") && !entry.includes("installer")) {
          resolve();
          return;
        }
      }
      reject(new Error(`NeoForge server install failed (exit ${code}): ${stderr.slice(-500)}`));
    });
    child.on("error", reject);
  });
  const argsFile = isWindows() ? "win_args.txt" : "unix_args.txt";
  const argsPath = join(sDir, "libraries", "net", "neoforged", "neoforge", neoforgeVersion, argsFile);
  if (existsSync(argsPath)) return argsPath;
  for (const entry of readdirSync(sDir)) {
    if (entry.includes("neoforge") && entry.endsWith(".jar") && !entry.includes("installer")) {
      return join(sDir, entry);
    }
  }
  return serverJarPath;
}
async function installServer(version, loader, onProgress, serverType, properties) {
  const data = loadVersionsData();
  const vi = getVersion(data, version);
  let versionId;
  let mcVersion;
  let javaVersion = SERVER.defaultJavaVersion;
  let forgeVersion;
  let neoforgeVersion;
  if (vi) {
    const resolved = getVersionForLoader(data, version, loader);
    if (!resolved) throw new Error(`${loader} not available for ${version}`);
    versionId = resolved;
    mcVersion = vi.mc_version;
    javaVersion = vi.java ?? SERVER.defaultJavaVersion;
    forgeVersion = vi.forge || void 0;
    neoforgeVersion = vi.neoforge || void 0;
  } else {
    versionId = version;
    mcVersion = version;
  }
  const effectiveType = serverType ?? SERVER.defaultType;
  const sDir = serverDir(`${versionId}-${effectiveType}`);
  if (!existsSync(sDir)) mkdirSync(sDir, { recursive: true });
  const javaPath = findJavaForVersion(javaVersion);
  let jarPath;
  switch (effectiveType) {
    case "paper": {
      jarPath = await downloadPaperServer(mcVersion, sDir, onProgress);
      break;
    }
    case "spigot": {
      jarPath = await runBuildTools(mcVersion, sDir, javaPath, ["spigot"], onProgress);
      break;
    }
    case "craftbukkit": {
      jarPath = await runBuildTools(mcVersion, sDir, javaPath, ["craftbukkit"], onProgress);
      break;
    }
    case "forge": {
      if (!forgeVersion) throw new Error(`Forge not available for ${version}`);
      await downloadForgeInstaller(forgeVersion, onProgress);
      jarPath = await installForgeServer(mcVersion, forgeVersion, sDir, javaPath, onProgress);
      break;
    }
    case "fabric": {
      const loaderVer = DEFAULT_FABRIC_LOADER_VERSION;
      await downloadFabricLoader(mcVersion, loaderVer, onProgress);
      jarPath = await installFabricServer(mcVersion, sDir, javaPath, onProgress);
      break;
    }
    case "neoforge": {
      if (!neoforgeVersion) throw new Error(`NeoForge not available for ${version}`);
      await downloadNeoforgeInstaller(neoforgeVersion, onProgress);
      jarPath = await installNeoForgeServer(neoforgeVersion, mcVersion, sDir, javaPath, onProgress);
      break;
    }
    case "vanilla":
    default: {
      jarPath = await ensureVanillaServerJar(mcVersion, sDir, onProgress);
      break;
    }
  }
  const eulaPath = join(sDir, SERVER.eulaFileName);
  if (!existsSync(eulaPath)) writeFileSync(eulaPath, "eula=true\n", "utf-8");
  const propsPath = join(sDir, SERVER.propertiesFileName);
  const mergedProps = { ...properties };
  if (mergedProps.serverPort == null) mergedProps.serverPort = GAME.defaultServerPort;
  writeFileSync(propsPath, generateServerProperties(mergedProps), "utf-8");
  const modsDir = join(sDir, SERVER.modsDirName);
  if (!existsSync(modsDir)) mkdirSync(modsDir, { recursive: true });
  onProgress?.(`Server installed at ${sDir}`);
  return { serverDir: sDir, jarPath, versionId, mcVersion, serverType: effectiveType, javaVersion };
}
function launchServer(setup, opts) {
  const port = opts?.port ?? GAME.defaultServerPort;
  const javaVersion = opts?.javaVersion ?? setup.javaVersion;
  const java = opts?.javaPath || findJavaForVersion(javaVersion);
  const maxMem = opts?.maxMemoryMb ?? GAME.defaultServerMemoryMb;
  const isNeoForgeScript = setup.jarPath.endsWith(".txt");
  const isRunScript = isWindows() ? setup.jarPath.endsWith("run.bat") : setup.jarPath.endsWith("run.sh");
  const args = [];
  if (isNeoForgeScript) {
    args.push(`-Xmx${maxMem}m`);
    if (opts?.minMemoryMb) args.push(`-Xms${opts.minMemoryMb}m`);
    args.push(`@${setup.jarPath}`, "--nogui");
  } else if (isRunScript) {
    args.push(setup.jarPath);
  } else {
    args.push(`-Xmx${maxMem}m`);
    if (opts?.minMemoryMb) args.push(`-Xms${opts.minMemoryMb}m`);
    args.push(`-Dmcp.port=0`);
    if (opts?.extraJvmArgs) {
      for (const arg of opts.extraJvmArgs.split(/\s+/)) {
        if (arg) args.push(arg);
      }
    }
    args.push(`-jar`, setup.jarPath, "--nogui");
  }
  if (opts?.extraGameArgs && !isNeoForgeScript) {
    for (const arg of opts.extraGameArgs.split(/\s+/)) {
      if (arg) args.push(arg);
    }
  }
  const logPath = join(setup.serverDir, "server-launch.log");
  const logFd = openSync(logPath, "a");
  const launchCmd = isRunScript ? setup.jarPath : java;
  const prefix = `
[${(/* @__PURE__ */ new Date()).toISOString()}] Launching: ${launchCmd} ${args.join(" ")}
`;
  writeFileSync(logFd, prefix);
  const child = spawn(launchCmd, isRunScript ? [] : args, {
    cwd: setup.serverDir,
    stdio: ["ignore", logFd, logFd],
    detached: true,
    shell: isRunScript
  });
  child.unref();
  child.on("error", (err) => {
    console.error(`[${SERVER.userAgent}] Server launch failed: ${err.message}`);
  });
  return { process: child, port, dir: setup.serverDir };
}
async function waitForServer(sDir, timeoutMs = 12e4) {
  const logPath = join(sDir, "logs", "latest.log");
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (existsSync(logPath)) {
      try {
        const content = readFileSync(logPath, "utf-8");
        if (content.includes("Done (") && content.includes('For help, type "help"')) {
          return true;
        }
      } catch {
      }
    }
    await new Promise((r) => setTimeout(r, 2e3));
  }
  return false;
}
function getCompatPairs(version) {
  const data = loadVersionsData();
  const vi = getVersion(data, version);
  if (!vi) return [];
  const clientLoaders = [];
  if (vi.forge) clientLoaders.push("forge");
  if (hasFabric(vi)) clientLoaders.push("fabric");
  if (vi.neoforge) clientLoaders.push("neoforge");
  const parts = version.split(".").map(Number);
  const major = parts[0] ?? 0;
  const minor = parts[1] ?? 0;
  const patch = parts[2] ?? 0;
  const mcAtLeast = (maj, mn, pat) => major > maj || major === maj && minor > mn || major === maj && minor === mn && patch >= pat;
  const serverTypes = ["vanilla"];
  if (mcAtLeast(1, 8, 0)) serverTypes.push("spigot", "craftbukkit");
  if (mcAtLeast(1, 8, 0)) serverTypes.push("paper");
  if (vi.forge) serverTypes.push("forge");
  if (hasFabric(vi)) serverTypes.push("fabric");
  if (vi.neoforge) serverTypes.push("neoforge");
  const pairs = [];
  for (const cl of clientLoaders) {
    for (const st of serverTypes) {
      const isModLoader = cl === "forge" || cl === "fabric" || cl === "neoforge";
      const isModServer = st === "forge" || st === "fabric" || st === "neoforge";
      const isPluginServer = st === "spigot" || st === "craftbukkit" || st === "paper";
      let valid = false;
      let reason = "";
      if (cl === st) {
        valid = true;
        reason = "Same framework, full compatibility";
      } else if (isModLoader && st === "vanilla") {
        valid = true;
        reason = "Vanilla server accepts any client (mods inactive)";
      } else if (isModLoader && isPluginServer) {
        valid = true;
        reason = "Plugin server accepts vanilla protocol clients";
      } else if (isModLoader && isModServer && cl !== st) {
        valid = false;
        reason = `${cl} client incompatible with ${st} server`;
      } else {
        valid = true;
        reason = "Compatible";
      }
      pairs.push({ clientLoader: cl, serverType: st, valid, reason });
    }
  }
  return pairs;
}
export {
  getCompatPairs,
  installServer,
  launchServer,
  waitForServer
};
