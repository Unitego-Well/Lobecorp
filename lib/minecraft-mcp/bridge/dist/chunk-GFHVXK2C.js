#!/usr/bin/env node
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __require = /* @__PURE__ */ ((x) => typeof require !== "undefined" ? require : typeof Proxy !== "undefined" ? new Proxy(x, {
  get: (a, b) => (typeof require !== "undefined" ? require : a)[b]
}) : x)(function(x) {
  if (typeof require !== "undefined") return require.apply(this, arguments);
  throw Error('Dynamic require of "' + x + '" is not supported');
});
var __esm = (fn, res) => function __init() {
  return fn && (res = (0, fn[__getOwnPropNames(fn)[0]])(fn = 0)), res;
};
var __commonJS = (cb, mod) => function __require2() {
  return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
};
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// node_modules/tsup/assets/esm_shims.js
import path from "node:path";
import { fileURLToPath } from "node:url";
var init_esm_shims = __esm({
  "node_modules/tsup/assets/esm_shims.js"() {
    "use strict";
  }
});

// src/mc/defaults.ts
init_esm_shims();
var LAUNCHER = {
  name: "MMML",
  nameGame: "MCP-Launcher",
  version: "0.2.1",
  versionType: "release",
  mcpServerName: "minecraft-mod-mcp"
};
var PLAYER = {
  defaultName: "Player",
  defaultUuid: "00000000-0000-0000-0000-000000000000",
  defaultAccessToken: "0",
  defaultUserType: "legacy"
};
var GAME = {
  defaultWidth: 854,
  defaultHeight: 480,
  defaultMaxMemoryMb: 2048,
  defaultMinMemoryMb: 512,
  defaultServerMemoryMb: 1024,
  defaultServerPort: 25565,
  serverStartupWaitMs: 15e3,
  defaultVersion: "1.21.7",
  defaultLoader: "forge",
  defaultLanguage: "en-US",
  defaultDownloadSource: "mojang",
  javaVersionFallback: 17,
  javaVersionThresholds: { mc117: 17, mc116: 16, mc113: 13 },
  defaultOptionsTxt: [
    "version:29",
    "autoJump:false",
    "chatOpacity:1.0",
    "enableVsync:false",
    "forceUnicodeFont:false",
    "fov:70.0",
    "gamma:1.0",
    "guiScale:2",
    "lang:en_US",
    "maxFps:260",
    "musicVolume:0.0",
    "renderDistance:8",
    "soundCategory_master:0.0",
    "soundCategory_music:0.0",
    "soundCategory_record:0.0",
    "soundCategory_weather:0.0",
    "soundCategory_block:0.0",
    "soundCategory_hostile:0.0",
    "soundCategory_neutral:0.0",
    "soundCategory_player:0.0",
    "soundCategory_ambient:0.0",
    "soundCategory_voice:0.0",
    "showAccessibilityOnboardingScreen:false"
  ].join("\n")
};
var MCP = {
  portStart: 9876,
  portEnd: 9e3,
  heartbeatTimeoutMs: 2e3,
  discoverTimeoutMs: 3e5,
  bindAddress: "127.0.0.1",
  waitTimeoutMs: 12e4,
  pollIntervalMs: 3e3
};
var PATHS = {
  mcDirName: ".minecraft",
  launcherDirName: "mcp_launcher",
  gradleJdksSubdir: ".gradle/jdks",
  versionsDirName: "versions",
  librariesDirName: "libraries",
  assetsDirName: "assets",
  nativesDirName: "natives",
  assetIndexesDirName: "indexes",
  assetObjectsDirName: "objects",
  tmpSuffix: ".tmp",
  javaDirName: "java",
  gameDirName: "game",
  serverDirName: "server"
};
var JAVA = {
  jdkDirPrefixes: {
    8: "eclipse_adoptium-8",
    16: "eclipse_foundation-16",
    17: "eclipse_adoptium-17",
    21: "eclipse_adoptium-21",
    25: "eclipse_adoptium-25"
  },
  adoptiumApiUrl: "https://api.adoptium.net/v3/assets/latest",
  extractTimeoutMs: 12e4
};
var DOWNLOAD = {
  versionManifestUrl: "https://piston-meta.mojang.com/mc/game/version_manifest.json",
  versionManifestV2Url: "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
  assetBaseUrl: "https://resources.download.minecraft.net/",
  assetBatchSize: 50,
  mavenLibrariesUrl: "https://libraries.minecraft.net/",
  forgeMavenUrl: "https://maven.minecraftforge.net/",
  neoforgeMavenUrl: "https://maven.neoforged.net/releases/",
  fabricMetaUrl: "https://meta.fabricmc.net/v2/versions/loader",
  jitpackUrl: "https://jitpack.io",
  fallbackRepoUrls: [
    "https://libraries.minecraft.net/",
    "https://maven.minecraftforge.net/",
    "https://maven.neoforged.net/releases/"
  ]
};
var AUTH = {
  microsoftClientId: "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb",
  deviceCodeUrl: "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
  tokenUrl: "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
  xblAuthUrl: "https://user.auth.xboxlive.com/user/authenticate",
  xstsAuthUrl: "https://xsts.auth.xboxlive.com/xsts/authorize",
  xblRelyingParty: "http://auth.xboxlive.com",
  xblSiteName: "user.auth.xboxlive.com",
  xstsSandboxId: "RETAIL",
  xstsRelyingParty: "rp://api.minecraftservices.com/",
  mcLoginUrl: "https://api.minecraftservices.com/authentication/login_with_xbox",
  mcProfileUrl: "https://api.minecraftservices.com/minecraft/profile",
  oauthScope: "XboxLive.signin offline_access",
  defaultExpiresIn: 3600,
  pollIntervalMs: 5e3,
  slowDownIntervalMs: 1e4
};
var SERVER_TYPES = ["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"];
var SERVER = {
  rconPort: 25575,
  networkCompressionThreshold: 256,
  maxTickTime: 6e4,
  maxPlayers: 20,
  viewDistance: 10,
  simulationDistance: 10,
  maxWorldSize: 29999984,
  connectHost: "localhost",
  defaultJavaVersion: 21,
  userAgent: "minecraft-mcp",
  bindAddress: "0.0.0.0",
  eulaFileName: "eula.txt",
  propertiesFileName: "server.properties",
  modsDirName: "mods",
  defaultType: "vanilla",
  buildToolsUrl: "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar",
  paperApiUrl: "https://api.papermc.io/v2/projects/paper"
};
var MOD = {
  cmdEndpoint: "/api/cmd",
  screenshotEndpoint: "/api/screenshot",
  statusEndpoint: "/api/status",
  statusType: "minecraft-mod",
  httpServerName: "minecraft-mod-mcp-server"
};
var BUILD = {
  portableGitDirName: "PortableGit-2.45.2-64-bit"
};
var FABRIC = {
  defaultLoaderVersion: "0.16.14",
  defaultInstallerVersion: "0.11.2",
  mavenBaseUrl: "https://maven.fabricmc.net"
};

export {
  __require,
  __commonJS,
  __export,
  __toESM,
  init_esm_shims,
  LAUNCHER,
  PLAYER,
  GAME,
  MCP,
  PATHS,
  JAVA,
  DOWNLOAD,
  AUTH,
  SERVER_TYPES,
  SERVER,
  MOD,
  BUILD,
  FABRIC
};
