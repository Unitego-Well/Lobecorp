import {
  AUTH,
  DEFAULT_FABRIC_LOADER_VERSION,
  GAME,
  JAVA,
  LAUNCHER,
  MCP,
  MOD,
  PATHS,
  PLAYER,
  SERVER,
  accountAccessToken,
  accountUserType,
  accountUsername,
  accountUuid,
  addAccount,
  assetsDir,
  classpathSeparator,
  classpathSeparator2,
  collectAllArgs,
  crossHomedir,
  defaults_exports,
  detectJavas,
  detectRuntime,
  downloadFile,
  downloadLoaderVersion,
  downloadVersion,
  downloadWithNativeFallback,
  ensureModJar,
  ensureVersionInstalled,
  fetchVersionJson,
  fetchVersionManifest,
  findJavaForVersion,
  gameDirPath,
  getNativeClassifier,
  getVersion,
  getVersionById,
  getVersionForLoader,
  getVersions,
  isMacos,
  isWindows,
  javaExec,
  javaExecPath,
  jdkHome,
  launcherDir,
  librariesDir,
  libraryMavenPath,
  libraryPath,
  listInstalledVersions,
  loadConfig,
  loadVersion,
  loadVersionJson,
  loadVersionMerged,
  loadVersionsData,
  loaders,
  mcDir,
  nativesDir,
  removeAccount,
  resolveClasspath,
  saveConfig,
  selectedAccount,
  shouldApply,
  versionsDir
} from "./chunk-4DNIJZ7D.js";

// src/api/modClient.ts
import { execSync } from "child_process";

// src/consts.ts
var configuredPort = process.env.MC_MCP_PORT;
var fixedPort = configuredPort === void 0 ? void 0 : Number(configuredPort);
if (fixedPort !== void 0 && (!Number.isInteger(fixedPort) || fixedPort < 1 || fixedPort > 65535)) {
  throw new Error("MC_MCP_PORT must be an integer in range 1..65535");
}
var PORT_START = fixedPort ?? MCP.portStart;
var PORT_END = fixedPort ?? MCP.portEnd;
var HEARTBEAT_TIMEOUT_MS = MCP.heartbeatTimeoutMs;
function isModStatus(obj) {
  if (typeof obj !== "object" || obj === null) return false;
  const o = obj;
  return o.ok === true && o.type === MOD.statusType && typeof o.port === "number";
}

// src/discovery/scanner.ts
async function probePort(port) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEARTBEAT_TIMEOUT_MS);
  try {
    const resp = await fetch(`http://${MCP.bindAddress}:${port}${MOD.statusEndpoint}`, {
      signal: controller.signal
    });
    clearTimeout(timer);
    if (!resp.ok) return null;
    const body = await resp.json();
    return isModStatus(body) ? body : null;
  } catch {
    clearTimeout(timer);
    return null;
  }
}
async function findMod(startPort = PORT_START, endPort = PORT_END) {
  for (let port = startPort; port >= endPort; port--) {
    const status = await probePort(port);
    if (status) return status;
  }
  return null;
}
async function waitForMod(startPort = PORT_START, endPort = PORT_END, timeoutMs = MCP.waitTimeoutMs, pollIntervalMs = MCP.pollIntervalMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const status = await findMod(startPort, endPort);
    if (status) return status;
    await sleep(Math.min(pollIntervalMs, deadline - Date.now()));
  }
  return null;
}
async function findFreePort(startPort = PORT_START, endPort = PORT_END) {
  const { createServer } = await import("net");
  for (let port = startPort; port >= endPort; port--) {
    const ok = await new Promise((resolve) => {
      const srv = createServer();
      srv.once("error", () => {
        resolve(false);
        srv.close();
      });
      srv.listen(port, MCP.bindAddress, () => {
        srv.close();
        resolve(true);
      });
    });
    if (ok) return port;
  }
  throw new Error(`No free port in range ${startPort}-${endPort}`);
}
function sleep(ms) {
  return new Promise((r) => setTimeout(r, Math.max(0, ms)));
}

// src/api/modClient.ts
var SCREENSHOT_ERRORS = {
  notConnected: "Mod not connected",
  requestFailed: "Screenshot failed",
  invalidRegion: "region must be game, editor or scene",
  invalidSize: "maxSize must be a positive 32-bit integer",
  missingProject: "projectId is required for editor and scene captures",
  unavailableView: "Project view is unavailable",
  hiddenView: "Capture requires the active project embedded in the game window with a visible region",
  invalidBounds: "Project capture bounds are invalid"
};
var ModClient = class {
  modPort = null;
  baseUrl = "";
  modAlive = false;
  mcProcess = null;
  get connected() {
    return this.modPort !== null;
  }
  getStatus() {
    return {
      connected: this.connected,
      port: this.modPort,
      processAlive: this.modAlive || this.mcProcess !== null && this.mcProcess.exitCode === null
    };
  }
  async discover(startPort = PORT_START, endPort = PORT_END) {
    const status = await findMod(startPort, endPort);
    this.modAlive = status !== null;
    if (status) {
      this.modPort = status.port;
      this.baseUrl = `http://${MCP.bindAddress}:${status.port}`;
    }
    return status;
  }
  async waitForConnection(timeoutMs = MCP.waitTimeoutMs) {
    const status = await waitForMod(PORT_START, PORT_END, timeoutMs);
    this.modAlive = status !== null;
    if (status) {
      this.modPort = status.port;
      this.baseUrl = `http://${MCP.bindAddress}:${status.port}`;
    }
    return status;
  }
  async checkAlive() {
    if (!this.modPort) {
      this.modAlive = false;
      return false;
    }
    const s = await probePort(this.modPort);
    this.modAlive = s !== null;
    if (!this.modAlive) this.disconnect();
    return this.modAlive;
  }
  disconnect() {
    this.modAlive = false;
    this.modPort = null;
    this.baseUrl = "";
  }
  async sendCommand(method, params) {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) throw new Error("Mod not connected");
    }
    const body = { cmd: method, ...params || {} };
    try {
      const resp = await fetch(`${this.baseUrl}${MOD.cmdEndpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      if (!resp.ok) {
        const text = await resp.text();
        if (resp.status === 502 || resp.status === 503) this.disconnect();
        throw new Error(`Mod returned ${resp.status}: ${text}`);
      }
      return resp.json();
    } catch (err) {
      if (err.cause?.code === "ECONNREFUSED") this.disconnect();
      throw err;
    }
  }
  async screenshot(params = {}) {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) {
        throw new Error(SCREENSHOT_ERRORS.notConnected);
      }
    }
    const capture = await this.resolveScreenshotParams(params);
    const query = new URLSearchParams(Object.entries(capture).map(([key, value]) => [key, String(value)]));
    try {
      const resp = await fetch(`${this.baseUrl}${MOD.screenshotEndpoint}?${query}`);
      if (!resp.ok) {
        throw new Error(`${SCREENSHOT_ERRORS.requestFailed}: ${resp.status}: ${await resp.text()}`);
      }
      return resp.json();
    } catch (err) {
      if (err.cause?.code === "ECONNREFUSED") {
        this.disconnect();
      }
      throw err;
    }
  }
  async screenshotToFile(params) {
    const capture = await this.resolveScreenshotParams(params);
    return this.sendCommand("screenshot_to_file", { ...capture, path: params.path });
  }
  async resolveScreenshotParams(params) {
    const region = params.region ?? "game";
    if (region !== "game" && region !== "editor" && region !== "scene") {
      throw new Error(SCREENSHOT_ERRORS.invalidRegion);
    }
    if (params.maxSize !== void 0 && (!Number.isInteger(params.maxSize) || Number(params.maxSize) <= 0 || Number(params.maxSize) > 2147483647)) {
      throw new Error(SCREENSHOT_ERRORS.invalidSize);
    }
    const capture = { region };
    if (params.maxSize !== void 0) {
      capture.maxSize = params.maxSize;
    }
    if (params.projectId !== void 0) {
      capture.projectId = params.projectId;
    }
    if (region === "game") {
      return capture;
    }
    if (typeof params.projectId !== "string" || !params.projectId.trim()) {
      throw new Error(SCREENSHOT_ERRORS.missingProject);
    }
    const response = await this.sendCommand("effect_view_state", { projectId: params.projectId });
    if (response.ok !== true) {
      throw new Error(response.error?.message ?? SCREENSHOT_ERRORS.unavailableView);
    }
    const data = response.data;
    const bounds = data?.captureRegions?.[region];
    if (data?.active !== true || data.embedded !== true || !bounds) {
      throw new Error(SCREENSHOT_ERRORS.hiddenView);
    }
    if (![bounds.x, bounds.y, bounds.width, bounds.height, data.guiScale].every((value) => typeof value === "number" && Number.isFinite(value)) || bounds.x < 0 || bounds.y < 0 || bounds.width <= 0 || bounds.height <= 0 || Number(data.guiScale) <= 0) {
      throw new Error(SCREENSHOT_ERRORS.invalidBounds);
    }
    return {
      ...capture,
      cropX: bounds.x,
      cropY: bounds.y,
      cropWidth: bounds.width,
      cropHeight: bounds.height,
      guiScale: data.guiScale
    };
  }
  setMcProcess(proc) {
    this.mcProcess = proc;
  }
  getMcProcess() {
    return this.mcProcess;
  }
  killMc() {
    if (this.mcProcess && this.mcProcess.exitCode === null) {
      if (process.platform === "win32") {
        try {
          execSync(`taskkill /PID ${this.mcProcess.pid} /T /F`, { stdio: "ignore" });
        } catch {
        }
      } else {
        this.mcProcess.kill("SIGTERM");
      }
      this.mcProcess = null;
    }
  }
};

// src/mcp/transport.ts
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

// src/mcp/tools.ts
import { z } from "zod";
function zOpt(schema) {
  return schema.optional();
}
var MouseButton = z.enum(["left", "right", "middle"]);
var ScreenshotOptions = {
  region: z.enum(["game", "editor", "scene"]).optional().describe("Capture the game window (default), Photon editor, or scene panel."),
  projectId: z.string().min(1).optional().describe("Required for editor or scene. The project must be active and embedded in the game window."),
  maxSize: z.number().int().positive().max(2147483647).optional().describe("Maximum image edge in pixels. Preserves aspect ratio; omitted means native size.")
};
var TOOLS = [
  {
    name: "ping",
    description: 'Ping the Minecraft mod. Always available. Returns "pong".',
    inputSchema: z.object({})
  },
  {
    name: "screenshot",
    description: "Capture a real Minecraft framebuffer, optionally cropped to the active Photon editor or scene. Returns base64 PNG, coordinate grid, output size and native source size.",
    inputSchema: z.object(ScreenshotOptions)
  },
  {
    name: "screenshot_to_file",
    description: "Capture a screenshot and save to disk. Returns file path and size.",
    inputSchema: z.object({
      ...ScreenshotOptions,
      path: z.string().optional().describe("File path to save to. Defaults to screenshots/vtty/ directory.")
    })
  },
  {
    name: "get_player_info",
    description: "Get player state: position, rotation, health, hunger, game mode, dimension, etc.",
    inputSchema: z.object({})
  },
  {
    name: "get_world_info",
    description: "Get world state: seed, time, weather, difficulty, loaded chunks, entities, etc.",
    inputSchema: z.object({})
  },
  {
    name: "debug_fields",
    description: "Get the debug overlay fields (F3 screen data): FPS, coordinates, biome, etc.",
    inputSchema: z.object({})
  },
  {
    name: "get_screen_buttons",
    description: "List all interactive buttons on the current GUI screen with IDs, labels, bounds.",
    inputSchema: z.object({})
  },
  {
    name: "enumerate_widgets",
    description: "Enumerate the full widget tree of the current GUI screen.",
    inputSchema: z.object({})
  },
  {
    name: "enter_control_mode",
    description: "Enter MCP control mode \u2014 releases mouse cursor, enables click/type/keyboard input. The project NeoForge development client activates once during startup; production clients retain manual activation via PauseScreen button or overlay click.",
    inputSchema: z.object({})
  },
  {
    name: "exit_control_mode",
    description: "Exit MCP control mode \u2014 returns to normal Minecraft input handling.",
    inputSchema: z.object({})
  },
  {
    name: "release_mouse",
    description: "Release the mouse cursor from Minecraft's grab.",
    inputSchema: z.object({})
  },
  {
    name: "pause_game",
    description: "Pause the game (open pause screen).",
    inputSchema: z.object({})
  },
  {
    name: "close_screen",
    description: "Close the current GUI screen.",
    inputSchema: z.object({}),
    requiresControl: true
  },
  {
    name: "open_chat",
    description: "Open the chat input box.",
    inputSchema: z.object({}),
    requiresControl: true
  },
  {
    name: "set_gamemode",
    description: "Change the player's game mode.",
    inputSchema: z.object({
      mode: z.enum(["survival", "creative", "adventure", "spectator"]).describe("Game mode to set.")
    })
  },
  {
    name: "click",
    description: "Click at the specified screen coordinates. Requires control mode.",
    inputSchema: z.object({
      x: z.number().int().describe("X coordinate in screen pixels."),
      y: z.number().int().describe("Y coordinate in screen pixels."),
      button: zOpt(MouseButton.describe('Mouse button. Defaults to "left".')).default("left")
    }),
    requiresControl: true
  },
  {
    name: "right_click",
    description: "Perform a right-click (use item / place block). Requires control mode.",
    inputSchema: z.object({}),
    requiresControl: true
  },
  {
    name: "mouse_drag",
    description: "Drag mouse from one point to another. Requires control mode.",
    inputSchema: z.object({
      x_start: z.number().int().describe("Start X."),
      y_start: z.number().int().describe("Start Y."),
      x_end: z.number().int().describe("End X."),
      y_end: z.number().int().describe("End Y."),
      button: zOpt(MouseButton).default("left")
    }),
    requiresControl: true
  },
  {
    name: "scroll",
    description: "Scroll the mouse wheel. Requires control mode.",
    inputSchema: z.object({
      clicks: z.number().int().describe("Number of scroll clicks. Positive = up, negative = down.")
    }),
    requiresControl: true
  },
  {
    name: "scroll_at",
    description: "Scroll at specific coordinates. Requires control mode.",
    inputSchema: z.object({
      x: z.number().int(),
      y: z.number().int(),
      clicks: z.number().int()
    }),
    requiresControl: true
  },
  {
    name: "press_key",
    description: "Press a keyboard key. Requires control mode.",
    inputSchema: z.object({
      key: z.string().describe('Key name (e.g. "key.keyboard.w", "key.keyboard.space", "A").'),
      hold_seconds: zOpt(z.number().describe("Duration to hold the key in seconds.")).default(0)
    }),
    requiresControl: true
  },
  {
    name: "type_text",
    description: "Type text into the focused text field. Requires control mode.",
    inputSchema: z.object({
      text: z.string().describe("Text to type."),
      press_enter: zOpt(z.boolean().describe("Whether to press Enter after typing.")).default(false)
    }),
    requiresControl: true
  },
  {
    name: "paste_text",
    description: "Paste text (uses clipboard injection). Requires control mode.",
    inputSchema: z.object({
      text: z.string().describe("Text to paste."),
      press_enter: zOpt(z.boolean()).default(false)
    }),
    requiresControl: true
  },
  {
    name: "hotkey",
    description: "Press a key combination (e.g. Ctrl+S). Requires control mode.",
    inputSchema: z.object({
      keys: z.string().describe('Comma-separated key names, e.g. "key.keyboard.left control,key.keyboard.s".')
    }),
    requiresControl: true
  },
  {
    name: "click_button_id",
    description: "Click a GUI button by its numeric ID. Requires control mode.",
    inputSchema: z.object({
      id: z.number().int().describe("Button ID from get_screen_buttons.")
    }),
    requiresControl: true
  },
  {
    name: "click_button_index",
    description: "Click a GUI button by its index (0-based). Requires control mode.",
    inputSchema: z.object({
      index: z.number().int().describe("Button index.")
    }),
    requiresControl: true
  },
  {
    name: "switch_tab",
    description: "Switch to a tab by index in a tabbed GUI. Requires control mode.",
    inputSchema: z.object({
      index: z.number().int().describe("Tab index.")
    }),
    requiresControl: true
  },
  {
    name: "execute_command",
    description: 'Execute a Minecraft slash command (e.g. "/gamemode creative"). Requires control mode.',
    inputSchema: z.object({
      command: z.string().describe("The command string, with or without leading /.")
    }),
    requiresControl: true
  },
  {
    name: "set_view_angle",
    description: "Set the player's view angles. Requires control mode.",
    inputSchema: z.object({
      yaw: z.number().describe("Yaw angle in degrees (-180 to 180)."),
      pitch: z.number().describe("Pitch angle in degrees (-90 to 90).")
    }),
    requiresControl: true
  },
  {
    name: "look_delta",
    description: "Rotate view by a delta. Requires control mode.",
    inputSchema: z.object({
      delta_yaw: z.number().describe("Yaw change in degrees."),
      delta_pitch: z.number().describe("Pitch change in degrees.")
    }),
    requiresControl: true
  },
  {
    name: "use_item",
    description: "Use the currently held item. Requires control mode.",
    inputSchema: z.object({}),
    requiresControl: true
  },
  {
    name: "place_block",
    description: "Place a block at the current target position. Requires control mode.",
    inputSchema: z.object({}),
    requiresControl: true
  },
  {
    name: "overlay_click",
    description: "Click on the MCP overlay (e.g. the resume/transfer button).",
    inputSchema: z.object({
      x: z.number().int(),
      y: z.number().int()
    })
  },
  {
    name: "wait",
    description: "Wait for a specified duration. Useful for sequencing actions.",
    inputSchema: z.object({
      seconds: z.number().positive().describe("Seconds to wait.")
    })
  },
  {
    name: "launch_minecraft",
    description: "Launch a Minecraft instance with the specified version and mod loader. Automatically downloads and installs the version if not already present.",
    inputSchema: z.object({
      version: z.string().describe('Minecraft version, e.g. "1.21.7", "26.1.2".'),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      memory: zOpt(z.number()).describe("Max memory pool in MB (default: 2048)"),
      min_memory: zOpt(z.number()).describe("Min memory pool in MB"),
      jvm_args: zOpt(z.string()).describe("Extra JVM arguments (space-separated)"),
      game_args: zOpt(z.string()).describe("Extra game arguments (space-separated)"),
      fullscreen: zOpt(z.boolean()).describe("Launch in fullscreen mode"),
      width: zOpt(z.number()).describe("Window width in pixels"),
      height: zOpt(z.number()).describe("Window height in pixels"),
      server: zOpt(z.string()).describe("Auto-connect to this server host on launch"),
      server_port: zOpt(z.number()).describe("Server port (default: 25565)")
    })
  },
  {
    name: "kill_minecraft",
    description: "Kill the running Minecraft process.",
    inputSchema: z.object({})
  },
  {
    name: "get_minecraft_status",
    description: "Get Minecraft process and mod connection status.",
    inputSchema: z.object({})
  },
  {
    name: "install_version",
    description: "Download and install a Minecraft version with the specified mod loader. Downloads base MC + loader (Forge/Fabric/NeoForge).",
    inputSchema: z.object({
      version: z.string().describe('Minecraft version, e.g. "1.21.7", "26.1.2". Use list_supported_versions to see available versions.'),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge")
    })
  },
  {
    name: "list_supported_versions",
    description: "List all supported Minecraft versions with their Java requirements, available loaders, and version IDs.",
    inputSchema: z.object({})
  },
  {
    name: "list_installed_versions",
    description: "List all locally installed Minecraft versions.",
    inputSchema: z.object({})
  },
  {
    name: "detect_java",
    description: "Detect all Java installations on the system. Returns version, vendor, and path for each.",
    inputSchema: z.object({})
  },
  {
    name: "create_offline_account",
    description: "Create an offline Minecraft account for launching without authentication.",
    inputSchema: z.object({
      username: z.string().describe("Player username for the offline account.")
    })
  },
  {
    name: "list_accounts",
    description: "List all configured Minecraft accounts (offline and Microsoft).",
    inputSchema: z.object({})
  },
  {
    name: "install_server",
    description: "Download and set up a Minecraft server. Supports vanilla, Paper, and Spigot frameworks. Generates server.properties with custom settings.",
    inputSchema: z.object({
      version: z.string().describe('Minecraft version, e.g. "1.21.11", "26.1.2".'),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      server_type: zOpt(z.enum(["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"])).describe("Server framework (default: vanilla)"),
      port: zOpt(z.number()).describe("Server port (default: 25565)"),
      motd: zOpt(z.string()).describe("Server MOTD message"),
      max_players: zOpt(z.number()).describe("Max players (default: 20)"),
      gamemode: zOpt(z.enum(["survival", "creative", "adventure", "spectator"])).describe("Default gamemode"),
      difficulty: zOpt(z.enum(["peaceful", "easy", "normal", "hard"])).describe("Difficulty"),
      online_mode: zOpt(z.boolean()).describe("Enable online authentication (default: false)"),
      level_seed: zOpt(z.string()).describe("World seed"),
      level_name: zOpt(z.string()).describe("World save name (default: world)"),
      level_type: zOpt(z.string()).describe("World type (default: flat)"),
      white_list: zOpt(z.boolean()).describe("Enable whitelist")
    })
  },
  {
    name: "launch_server",
    description: "Launch a Minecraft server. Supports vanilla, Paper, and Spigot frameworks. Auto-installs if not present.",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      server_type: zOpt(z.enum(["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"])).describe("Server framework (default: vanilla)"),
      memory: zOpt(z.number()).describe("Server max memory in MB (default: 1024)"),
      min_memory: zOpt(z.number()).describe("Server min memory in MB"),
      jvm_args: zOpt(z.string()).describe("Extra JVM arguments (space-separated)"),
      game_args: zOpt(z.string()).describe("Extra server arguments (space-separated)"),
      port: zOpt(z.number()).describe("Server port (default: 25565)"),
      motd: zOpt(z.string()).describe("Server MOTD message"),
      max_players: zOpt(z.number()).describe("Max players (default: 20)"),
      gamemode: zOpt(z.enum(["survival", "creative", "adventure", "spectator"])).describe("Default gamemode"),
      difficulty: zOpt(z.enum(["peaceful", "easy", "normal", "hard"])).describe("Difficulty")
    })
  },
  {
    name: "serve",
    description: "One-command: install server + launch server + launch client auto-connected. Supports vanilla, Paper, and Spigot server frameworks.",
    inputSchema: z.object({
      version: z.string().describe("Minecraft version."),
      loader: zOpt(z.enum(["forge", "fabric", "neoforge"])).default("forge"),
      server_type: zOpt(z.enum(["vanilla", "spigot", "craftbukkit", "paper", "forge", "fabric", "neoforge"])).describe("Server framework (default: vanilla)"),
      memory: zOpt(z.number()).describe("Client max memory in MB (default: 2048)"),
      server_memory: zOpt(z.number()).describe("Server max memory in MB (default: 1024)"),
      fullscreen: zOpt(z.boolean()).describe("Launch client in fullscreen"),
      width: zOpt(z.number()).describe("Client window width in pixels"),
      height: zOpt(z.number()).describe("Client window height in pixels"),
      port: zOpt(z.number()).describe("Server port (default: 25565)"),
      motd: zOpt(z.string()).describe("Server MOTD message"),
      max_players: zOpt(z.number()).describe("Max players (default: 20)"),
      gamemode: zOpt(z.enum(["survival", "creative", "adventure", "spectator"])).describe("Default gamemode"),
      difficulty: zOpt(z.enum(["peaceful", "easy", "normal", "hard"])).describe("Difficulty")
    })
  }
];

// src/mcp/handlers.ts
import { spawn } from "child_process";
import { join as join3 } from "path";

// src/mc/launch.ts
import { existsSync as existsSync2, mkdirSync as mkdirSync2, readFileSync, writeFileSync, readdirSync as readdirSync2, copyFileSync } from "fs";
import { join as join2, extname, basename as basename2 } from "path";
import { inflateRawSync } from "zlib";

// src/mc/javaDownload.ts
import { existsSync, mkdirSync, createWriteStream, readdirSync, rmSync } from "fs";
import { join, basename, dirname } from "path";
function javaCacheDir() {
  return join(launcherDir(), PATHS.javaDirName);
}
function getPlatform() {
  if (isWindows()) return "windows";
  if (isMacos()) return "mac";
  return "linux";
}
function getArch() {
  const arch = process.arch;
  if (arch === "arm64") return "aarch64";
  return "x64";
}
function installedJavaHome(javaVersion) {
  const dir = join(javaCacheDir(), `jdk-${javaVersion}`);
  if (!existsSync(dir)) return null;
  const entries = readdirSync(dir, { withFileTypes: true });
  const jdkDir = entries.find((e) => e.isDirectory() && !e.name.startsWith("."));
  if (!jdkDir) return null;
  const home = join(dir, jdkDir.name);
  const exe = isWindows() ? join(home, "bin", "java.exe") : join(home, "bin", "java");
  return existsSync(exe) ? home : null;
}
async function fetchJdkUrl(javaVersion) {
  const platform = getPlatform();
  const arch = getArch();
  const apiUrl = `${JAVA.adoptiumApiUrl}/${javaVersion}/hotspot?architecture=${arch}&image_type=jdk&os=${platform}&vendor=eclipse`;
  const res = await fetch(apiUrl);
  if (!res.ok) throw new Error(`Adoptium API error: ${res.status} ${res.statusText}`);
  const assets = await res.json();
  if (!assets || assets.length === 0) throw new Error(`No JDK ${javaVersion} found for ${platform}-${arch}`);
  const pkg = assets[0].binary.package;
  return { url: pkg.link, name: pkg.name };
}
async function downloadFile2(url, dest, expectedSize, onProgress) {
  onProgress?.(`Downloading ${basename(dest)}...`);
  mkdirSync(dirname(dest), { recursive: true });
  try {
    await streamDownload(url, dest, expectedSize, onProgress);
    return;
  } catch (err) {
    if (existsSync(dest)) try {
      rmSync(dest, { force: true });
    } catch {
    }
    onProgress?.(`  Node fetch failed (${err.cause?.code || err.message}), resuming via native download...`);
    await downloadWithNativeFallback(url, dest);
  }
  if (expectedSize > 0) {
    const { statSync } = await import("fs");
    try {
      const got = statSync(dest).size;
      if (got < expectedSize * 0.95) throw new Error(`Download incomplete: got ${got} bytes, expected ~${expectedSize}`);
    } catch (e) {
      throw e;
    }
  }
}
async function streamDownload(url, dest, expectedSize, onProgress) {
  const res = await fetch(url, { redirect: "follow" });
  if (!res.ok) throw new Error(`Download failed: ${res.status}`);
  if (!res.body) throw new Error("No response body");
  const total = parseInt(res.headers.get("content-length") ?? "0");
  let downloaded = 0;
  let lastPct = -1;
  const fileStream = createWriteStream(dest);
  const reader = res.body;
  for await (const chunk of reader) {
    fileStream.write(chunk);
    downloaded += chunk.length;
    if (total > 0) {
      const pct = Math.floor(downloaded / total * 100);
      if (pct !== lastPct && pct % 10 === 0) {
        lastPct = pct;
        onProgress?.(`Downloading ${basename(dest)}... ${pct}%`);
      }
    }
  }
  fileStream.end();
  await new Promise((resolve, reject) => {
    fileStream.on("finish", resolve);
    fileStream.on("error", reject);
  });
  if (expectedSize > 0 && downloaded < expectedSize * 0.95) {
    try {
      rmSync(dest, { force: true });
    } catch {
    }
    throw new Error(`Download incomplete: got ${downloaded} bytes, expected ~${expectedSize}`);
  }
}
async function extractArchive(archive, outDir) {
  mkdirSync(outDir, { recursive: true });
  if (isWindows()) {
    const { execFileSync } = await import("child_process");
    const escArchive = archive.replace(/'/g, "''");
    const escOutDir = outDir.replace(/'/g, "''");
    const psScript = `Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('${escArchive}', '${escOutDir}')`;
    execFileSync("powershell", ["-NoProfile", "-Command", psScript], {
      stdio: "pipe",
      timeout: JAVA.extractTimeoutMs
    });
  } else {
    const { execSync: execSync2 } = await import("child_process");
    execSync2(`tar -xzf "${archive}" -C "${outDir}"`, { stdio: "pipe", timeout: JAVA.extractTimeoutMs });
  }
}
async function ensureJavaInstalled(javaVersion, onProgress) {
  const existing = installedJavaHome(javaVersion);
  if (existing) return existing;
  const fromGradle = jdkHome(javaVersion);
  if (fromGradle) {
    onProgress?.(`Java ${javaVersion} found at ${fromGradle}`);
    return fromGradle;
  }
  const cacheDir = join(javaCacheDir(), `jdk-${javaVersion}`);
  const tmpDir = join(javaCacheDir(), "tmp");
  onProgress?.(`Java ${javaVersion} not found, downloading from Adoptium...`);
  const { url, name } = await fetchJdkUrl(javaVersion);
  const archivePath = join(tmpDir, name);
  if (existsSync(archivePath)) {
    const { statSync } = await import("fs");
    if (statSync(archivePath).size < 1e7) {
      const { unlinkSync } = await import("fs");
      try {
        unlinkSync(archivePath);
      } catch {
      }
    }
  }
  if (!existsSync(archivePath)) {
    mkdirSync(tmpDir, { recursive: true });
    await downloadFile2(url, archivePath, 0, onProgress);
  }
  onProgress?.(`Extracting JDK ${javaVersion}...`);
  rmSync(cacheDir, { recursive: true, force: true });
  await extractArchive(archivePath, cacheDir);
  const home = installedJavaHome(javaVersion);
  if (!home) throw new Error(`JDK ${javaVersion} installation failed: no valid home found in ${cacheDir}`);
  onProgress?.(`Java ${javaVersion} installed at ${home}`);
  rmSync(archivePath, { force: true });
  return home;
}

// src/mc/launch.ts
var NATIVE_EXTS = /* @__PURE__ */ new Set([".dll", ".so", ".dylib", ".jnilib"]);
function ensureOptionsTxt(gameDir) {
  const optionsPath = join2(gameDir, "options.txt");
  if (existsSync2(optionsPath)) return;
  if (!GAME.defaultOptionsTxt) return;
  try {
    writeFileSync(optionsPath, GAME.defaultOptionsTxt + "\n", "utf-8");
  } catch {
  }
}
function deployModToModsDir(gameDir, modJar) {
  const modsDir = join2(gameDir, SERVER.modsDirName);
  if (!existsSync2(modsDir)) mkdirSync2(modsDir, { recursive: true });
  const dest = join2(modsDir, modJar.split(/[/\\]/).pop());
  if (!existsSync2(dest)) {
    copyFileSync(modJar, dest);
  }
}
function getNativeOsKey() {
  if (isWindows()) return "windows";
  if (isMacos()) return "osx";
  return "linux";
}
function extractNatives(libraries, nDir) {
  if (existsSync2(nDir)) {
    const existing = readdirSync2(nDir).filter((f) => NATIVE_EXTS.has(extname(f).toLowerCase()));
    if (existing.length > 0) return;
  }
  mkdirSync2(nDir, { recursive: true });
  const osKey = getNativeOsKey();
  for (const lib of libraries) {
    if (lib.rules && !shouldApply(lib.rules)) continue;
    let nativePath = null;
    if (lib.natives && lib.natives[osKey]) {
      const nativeClass = lib.natives[osKey];
      if (lib.downloads?.classifiers?.[nativeClass]) {
        const relPath = lib.downloads.classifiers[nativeClass].path;
        if (relPath) nativePath = join2(librariesDir(), relPath);
      }
      if (!nativePath) {
        const constructed = libraryPath(`${lib.name}:${nativeClass}`);
        if (existsSync2(constructed)) nativePath = constructed;
      }
    }
    if (!nativePath && lib.name.includes("natives")) {
      nativePath = libraryPath(lib.name);
    }
    if (!nativePath || !existsSync2(nativePath)) continue;
    extractNativeJar(nativePath, nDir);
  }
}
function extractNativeJar(jarPath, outDir) {
  const buf = readFileSync(jarPath);
  let endPos = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf[i] === 80 && buf[i + 1] === 75 && buf[i + 2] === 5 && buf[i + 3] === 6) {
      endPos = i;
      break;
    }
  }
  if (endPos < 0) return;
  const cdOffset = buf.readUInt32LE(endPos + 16);
  const cdSize = buf.readUInt32LE(endPos + 12);
  const cdEnd = cdOffset + cdSize;
  let pos = cdOffset;
  while (pos + 46 <= cdEnd) {
    if (buf.readUInt32LE(pos) !== 33639248) break;
    const nameLen = buf.readUInt16LE(pos + 28);
    const extraLen = buf.readUInt16LE(pos + 30);
    const commentLen = buf.readUInt16LE(pos + 32);
    const localOffset = buf.readUInt32LE(pos + 42);
    const name = buf.subarray(pos + 46, pos + 46 + nameLen).toString("utf-8");
    pos += 46 + nameLen + extraLen + commentLen;
    if (name.endsWith("/")) continue;
    const ext = extname(name).toLowerCase();
    if (!NATIVE_EXTS.has(ext)) continue;
    const fileName = basename2(name);
    const outPath = join2(outDir, fileName);
    if (existsSync2(outPath)) continue;
    const lNameLen = buf.readUInt16LE(localOffset + 26);
    const lExtraLen = buf.readUInt16LE(localOffset + 28);
    const lMethod = buf.readUInt16LE(localOffset + 8);
    const lCSize = buf.readUInt32LE(localOffset + 18);
    const dataStart = localOffset + 30 + lNameLen + lExtraLen;
    if (lMethod === 0) {
      writeFileSync(outPath, buf.subarray(dataStart, dataStart + lCSize));
    } else if (lMethod === 8) {
      const compressed = buf.subarray(dataStart, dataStart + lCSize);
      const decompressed = inflateRawSync(compressed);
      writeFileSync(outPath, decompressed);
    }
  }
}
var LEGACY_JVM_ARGS = [
  "--add-opens",
  "java.base/java.lang=ALL-UNNAMED",
  "--add-opens",
  "java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens",
  "java.base/java.util.jar=ALL-UNNAMED",
  "--add-opens",
  "java.base/java.util.zip=ALL-UNNAMED",
  "--add-opens",
  "java.base/jdk.internal.loader=ALL-UNNAMED",
  "--add-opens",
  "java.base/java.io=ALL-UNNAMED",
  "--add-opens",
  "java.base/sun.nio.ch=ALL-UNNAMED"
];
function inferJavaFromVersion(vj) {
  const id = (vj.inheritsFrom ?? vj.id ?? "").replace(/-/g, ".");
  if (/^26\./.test(id)) return 25;
  const m = id.match(/1\.(\d+)/);
  if (!m) return GAME.javaVersionFallback;
  const minor = parseInt(m[1]);
  if (minor >= 20) return 21;
  if (minor >= 17) return 17;
  return 8;
}
function findExactJava(targetVersion) {
  const home = jdkHome(targetVersion);
  if (home) {
    const exe = isWindows() ? join2(home, "bin", "java.exe") : join2(home, "bin", "java");
    if (existsSync2(exe)) return exe;
  }
  const all = detectJavas();
  const match = all.find((j) => j.version === targetVersion);
  if (match) {
    const exe = isWindows() ? join2(match.path, "bin", "java.exe") : join2(match.path, "bin", "java");
    if (existsSync2(exe)) return exe;
  }
  return null;
}
async function ensureJavaForLaunch(config, vj, data, onProgress) {
  if (config.javaPath && existsSync2(config.javaPath)) return config.javaPath;
  const vd = data ?? loadVersionsData();
  let versionInfo = getVersionById(vd, config.versionId);
  if (!versionInfo) {
    const mcVer = vj.inheritsFrom ?? vj.id ?? config.versionId;
    versionInfo = getVersionById(vd, mcVer);
  }
  const targetJava = versionInfo?.java ?? inferJavaFromVersion(vj);
  const cached = installedJavaHome(targetJava);
  if (cached) {
    return isWindows() ? join2(cached, "bin", "java.exe") : join2(cached, "bin", "java");
  }
  const exact = findExactJava(targetJava);
  if (exact) return exact;
  const home = await ensureJavaInstalled(targetJava, onProgress);
  return isWindows() ? join2(home, "bin", "java.exe") : join2(home, "bin", "java");
}
function buildLaunchCommand(config, vj, data) {
  const vd = data ?? loadVersionsData();
  const mcDir2 = config.mcDir ?? join2(crossHomedir(), ".minecraft");
  ensureOptionsTxt(mcDir2);
  if (config.modJar && existsSync2(config.modJar)) {
    deployModToModsDir(mcDir2, config.modJar);
  }
  let versionInfo = getVersionById(vd, config.versionId);
  if (!versionInfo) {
    const mcVer = vj.inheritsFrom ?? vj.id ?? config.versionId;
    versionInfo = getVersionById(vd, mcVer);
  }
  const targetJavaVersion = versionInfo?.java ?? inferJavaFromVersion(vj);
  const java = config.javaPath || findJavaForVersion(targetJavaVersion);
  const classpathPaths = resolveClasspath(vj.libraries);
  const inheritsFrom = vj.inheritsFrom ?? versionInfo?.mc_version ?? config.versionId;
  const bootstrapLayout = (vj.libraries ?? []).some(
    (l) => typeof l.name === "string" && l.name.startsWith("cpw.mods:bootstraplauncher")
  );
  const baseJar = join2(versionsDir(), inheritsFrom, `${inheritsFrom}.jar`);
  if (existsSync2(baseJar) && !bootstrapLayout) classpathPaths.push(baseJar);
  const versionJar = join2(versionsDir(), config.versionId, `${config.versionId}.jar`);
  if (existsSync2(versionJar) && versionJar !== baseJar) classpathPaths.push(versionJar);
  const needsSortFix = needsLegacySortFix(config.versionId, targetJavaVersion);
  let sortFixApplied = false;
  if (needsSortFix) {
    const sortFixJar = join2(launcherDir(), "legacyfix", "sortfix-tweaker.jar");
    if (existsSync2(sortFixJar)) {
      classpathPaths.unshift(sortFixJar);
      sortFixApplied = true;
    }
  }
  const sep = classpathSeparator2();
  const classpath = classpathPaths.join(sep);
  const { jvmArgs, gameArgs } = collectAllArgs(vj);
  const ndir = nativesDir(config.versionId);
  extractNatives(vj.libraries, ndir);
  const aDir = assetsDir();
  const assetsIndex = vj.assets ?? inheritsFrom;
  const libDir = librariesDir();
  const allArgs = [];
  if (config.maxMemoryMb) allArgs.push(`-Xmx${config.maxMemoryMb}m`);
  if (config.minMemoryMb) allArgs.push(`-Xms${config.minMemoryMb}m`);
  if (existsSync2(ndir)) {
    allArgs.push(`-Djava.library.path=${ndir}`);
  }
  if (config.mcpPort) {
    allArgs.push(`-Dmcp.port=${config.mcpPort}`);
  }
  allArgs.push("-Dmcp.mod.version=0.2.1");
  allArgs.push(`-Dmcp.mod.loader=${config.loader ?? "forge"}`);
  if (targetJavaVersion >= 9) {
    allArgs.push(...LEGACY_JVM_ARGS);
  }
  if (versionInfo && isLegacyForge(config.versionId, versionInfo)) {
    allArgs.push("-Dfml.ignoreInvalidMinecraftCertificates=true");
  }
  if (config.extraJvmArgs) {
    for (const arg of config.extraJvmArgs.split(/\s+/)) {
      if (arg) allArgs.push(arg);
    }
  }
  const hasCpInJvm = jvmArgs.some((a) => a.includes("${classpath}"));
  const resolvedJvm = jvmArgs.map(
    (arg) => arg.replace(/\$\{natives_directory\}/g, ndir).replace(/\$\{launcher_name\}/g, LAUNCHER.name).replace(/\$\{launcher_version\}/g, LAUNCHER.version).replace(/\$\{classpath\}/g, classpath).replace(/\$\{classpath_separator\}/g, sep).replace(/\$\{library_directory\}/g, libDir).replace(/\$\{version_name\}/g, config.versionId).replace(/\$\{game_directory\}/g, mcDir2).replace(/\$\{assets_root\}/g, aDir).replace(/\$\{game_assets\}/g, aDir).replace(/\$\{assets_index_name\}/g, assetsIndex)
  );
  allArgs.push(...resolvedJvm);
  if (!hasCpInJvm && classpath) {
    allArgs.push("-cp", classpath);
  }
  allArgs.push(vj.mainClass);
  const playerName = config.playerName ?? PLAYER.defaultName;
  const uuid = config.uuid ?? PLAYER.defaultUuid;
  const accessToken = config.accessToken ?? PLAYER.defaultAccessToken;
  const userType = config.userType ?? PLAYER.defaultUserType;
  const resolvedGame = gameArgs.map(
    (arg) => arg.replace(/\$\{auth_player_name\}/g, playerName).replace(/\$\{version_name\}/g, config.versionId).replace(/\$\{game_directory\}/g, mcDir2).replace(/\$\{assets_root\}/g, aDir).replace(/\$\{game_assets\}/g, aDir).replace(/\$\{assets_index_name\}/g, assetsIndex).replace(/\$\{auth_uuid\}/g, uuid).replace(/\$\{auth_access_token\}/g, accessToken).replace(/\$\{user_type\}/g, userType).replace(/\$\{version_type\}/g, LAUNCHER.versionType).replace(/\$\{natives_directory\}/g, ndir).replace(/\$\{launcher_name\}/g, LAUNCHER.nameGame).replace(/\$\{launcher_version\}/g, LAUNCHER.version).replace(/\$\{classpath\}/g, classpath).replace(/\$\{classpath_separator\}/g, sep).replace(/\$\{library_directory\}/g, libDir).replace(/\$\{resolution_width\}/g, String(config.width ?? GAME.defaultWidth)).replace(/\$\{resolution_height\}/g, String(config.height ?? GAME.defaultHeight)).replace(/\$\{clientid\}/g, "").replace(/\$\{auth_xuid\}/g, "").replace(/\$\{user_properties\}/g, "{}")
  );
  allArgs.push(...resolvedGame);
  if (sortFixApplied) {
    allArgs.push("--tweakClass", "mcp.fix.SortFixTweaker");
  }
  if (config.fullscreen) {
    allArgs.push("--fullscreen");
  }
  if (config.extraGameArgs) {
    for (const arg of config.extraGameArgs.split(/\s+/)) {
      if (arg) allArgs.push(arg);
    }
  }
  return { java, args: allArgs, classpath, mainClass: vj.mainClass, javaVersion: targetJavaVersion };
}
var SORT_FIX_PREFIXES = ["1.7.2", "1.7.10", "1.6", "1.5"];
function needsLegacySortFix(versionId, javaVersion) {
  if (javaVersion !== 8) return false;
  return SORT_FIX_PREFIXES.some((p) => versionId.startsWith(p));
}
function isLegacyForge(versionId, info) {
  if (!info.forge) return false;
  const mc = info.mc_version;
  const parts = mc.split(".").map(Number);
  if (parts.length < 2) return false;
  return parts[0] === 1 && parts[1] <= 12;
}

// src/mc/auth.ts
async function startDeviceAuth() {
  const resp = await fetch(AUTH.deviceCodeUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: AUTH.microsoftClientId,
      scope: AUTH.oauthScope
    })
  });
  if (!resp.ok) {
    const body = await resp.text();
    throw new Error(`Device code request failed (${resp.status}): ${body}`);
  }
  return resp.json();
}
async function pollDeviceAuth(deviceCode) {
  const oauthTokens = await pollForTokens(deviceCode);
  const accessToken = oauthTokens.access_token;
  const refreshToken2 = oauthTokens.refresh_token;
  const expiresIn = oauthTokens.expires_in || AUTH.defaultExpiresIn;
  const expiresAt = nowTimestamp() + expiresIn;
  return authChain(accessToken, refreshToken2, expiresAt);
}
async function refreshToken(refreshToken2) {
  const resp = await fetch(AUTH.tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      client_id: AUTH.microsoftClientId,
      refresh_token: refreshToken2,
      scope: AUTH.oauthScope
    })
  });
  if (!resp.ok) {
    const body2 = await resp.text();
    throw new Error(`Token refresh failed (${resp.status}): ${body2}`);
  }
  const body = await resp.json();
  const accessToken = body.access_token;
  const newRefreshToken = body.refresh_token;
  const expiresIn = body.expires_in || AUTH.defaultExpiresIn;
  const expiresAt = nowTimestamp() + expiresIn;
  return authChain(accessToken, newRefreshToken, expiresAt);
}
async function pollForTokens(deviceCode) {
  while (true) {
    const resp = await fetch(AUTH.tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:device_code",
        client_id: AUTH.microsoftClientId,
        device_code: deviceCode
      })
    });
    const body = await resp.json();
    const error = body.error;
    if (error) {
      switch (error) {
        case "authorization_pending":
          await sleep2(AUTH.pollIntervalMs);
          continue;
        case "slow_down":
          await sleep2(AUTH.slowDownIntervalMs);
          continue;
        case "expired_token":
          throw new Error("Device code expired. Please try again.");
        case "cancelled":
          throw new Error("Authentication was cancelled.");
        default:
          throw new Error(`OAuth error: ${error} - ${body.error_description ?? "Unknown"}`);
      }
    }
    return body;
  }
}
async function authChain(liveAccessToken, refreshToken2, expiresAt) {
  const [uhs, xstsToken] = await xboxAuth(liveAccessToken);
  const mcToken = await minecraftLogin(uhs, xstsToken);
  const [uuid, username] = await minecraftProfile(mcToken);
  return { uuid, username, access_token: mcToken, refresh_token: refreshToken2, expires_at: expiresAt };
}
async function xboxAuth(liveAccessToken) {
  const xblResp = await postJson(AUTH.xblAuthUrl, {
    Properties: {
      AuthMethod: "RPS",
      SiteName: AUTH.xblSiteName,
      RpsTicket: `d=${liveAccessToken}`
    },
    RelyingParty: AUTH.xblRelyingParty,
    TokenType: "JWT"
  });
  const xblToken = xblResp.Token;
  const displayClaims = xblResp.DisplayClaims;
  const uhsValue = displayClaims.xui[0].uhs;
  const xstsResp = await postJson(AUTH.xstsAuthUrl, {
    Properties: { SandboxId: AUTH.xstsSandboxId, UserTokens: [xblToken] },
    RelyingParty: AUTH.xstsRelyingParty,
    TokenType: "JWT"
  });
  const xstsToken = xstsResp.Token;
  return [uhsValue, xstsToken];
}
async function minecraftLogin(uhs, xstsToken) {
  const resp = await postJson(AUTH.mcLoginUrl, {
    identityToken: `XBL3.0 x=${uhs};${xstsToken}`
  });
  return resp.access_token;
}
async function minecraftProfile(mcAccessToken) {
  const resp = await fetch(AUTH.mcProfileUrl, {
    headers: { Authorization: `Bearer ${mcAccessToken}` }
  });
  const body = await resp.json();
  return [body.id, body.name];
}
async function postJson(url, body) {
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  return await resp.json();
}
function nowTimestamp() {
  return Math.floor(Date.now() / 1e3);
}
function sleep2(ms) {
  return new Promise((r) => setTimeout(r, ms));
}
function createOfflineUuid(username) {
  let h = 0;
  for (let i = 0; i < username.length; i++) {
    h = Math.imul(31, h) + username.charCodeAt(i) | 0;
  }
  const hash = h >>> 0;
  const t = Date.now();
  return `${hash.toString(16).padStart(8, "0")}-${(hash >> 16 & 65535).toString(16).padStart(4, "0")}-${(hash & 65535 ^ 16384).toString(16).padStart(4, "0")}-${((t >> 16 ^ 32768) & 65535).toString(16).padStart(4, "0")}-${(hash ^ t >> 32).toString(16).padStart(8, "0")}${(t & 65535).toString(16).padStart(4, "0")}`;
}

// src/mcp/handlers.ts
import { existsSync as existsSync3, copyFileSync as copyFileSync2, mkdirSync as mkdirSync3 } from "fs";
function mcVersionGte(version, target) {
  const parse = (v) => v.split(".").map(Number);
  const a = parse(version), b = parse(target);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] ?? 0, y = b[i] ?? 0;
    if (x > y) return true;
    if (x < y) return false;
  }
  return true;
}
function buildServerConnectArgs(host, port, mcVersion) {
  if (mcVersionGte(mcVersion, "1.20.5")) {
    return `--quickPlayMultiplayer ${host}:${port}`;
  }
  return `--server ${host} --port ${port}`;
}
var MANAGEMENT_TOOLS = /* @__PURE__ */ new Set([
  "launch_minecraft",
  "kill_minecraft",
  "get_minecraft_status",
  "install_version",
  "list_supported_versions",
  "list_installed_versions",
  "detect_java",
  "create_offline_account",
  "list_accounts",
  "wait",
  "install_server",
  "launch_server",
  "serve"
]);
function registerHandlers(server, mod) {
  for (const tool of TOOLS) {
    server.registerTool(tool.name, {
      description: tool.description,
      inputSchema: tool.inputSchema
    }, async (params) => {
      try {
        if (!mod.connected && !MANAGEMENT_TOOLS.has(tool.name)) {
          await mod.discover();
        }
        const result = await handleTool(tool.name, params, mod);
        return { content: [{ type: "text", text: formatResult(result) }] };
      } catch (err) {
        return {
          content: [{ type: "text", text: `Error: ${err.message}` }],
          isError: true
        };
      }
    });
  }
}
async function handleTool(name, params, mod) {
  switch (name) {
    case "ping":
      return mod.sendCommand("ping");
    case "screenshot":
      return mod.screenshot(params);
    case "screenshot_to_file":
      return mod.screenshotToFile(params);
    case "get_player_info":
      return mod.sendCommand("get_player_info");
    case "get_world_info":
      return mod.sendCommand("get_world_info");
    case "debug_fields":
      return mod.sendCommand("debug_fields");
    case "get_screen_buttons":
      return mod.sendCommand("get_screen_buttons");
    case "enumerate_widgets":
      return mod.sendCommand("enumerate_widgets");
    case "enter_control_mode":
      return mod.sendCommand("enter_control_mode");
    case "exit_control_mode":
      return mod.sendCommand("exit_control_mode");
    case "release_mouse":
      return mod.sendCommand("release_mouse");
    case "pause_game":
      return mod.sendCommand("pause_game");
    case "close_screen":
      return mod.sendCommand("close_screen");
    case "open_chat":
      return mod.sendCommand("open_chat");
    case "set_gamemode":
      return mod.sendCommand("set_gamemode", params);
    case "click":
      return mod.sendCommand("click", params);
    case "right_click":
      return mod.sendCommand("right_click");
    case "mouse_drag":
      return mod.sendCommand("mouse_drag", mapDragParams(params));
    case "scroll":
      return mod.sendCommand("scroll", params);
    case "scroll_at":
      return mod.sendCommand("scroll_at", params);
    case "press_key":
      return mod.sendCommand("press_key", params);
    case "type_text":
      return mod.sendCommand("type_text", params);
    case "paste_text":
      return mod.sendCommand("paste_text", params);
    case "hotkey":
      return mod.sendCommand("hotkey", params);
    case "click_button_id":
      return mod.sendCommand("click_button_id", params);
    case "click_button_index":
      return mod.sendCommand("click_button_index", params);
    case "switch_tab":
      return mod.sendCommand("switch_tab", params);
    case "execute_command":
      return mod.sendCommand("execute_command", params);
    case "set_view_angle":
      return mod.sendCommand("set_view_angle", params);
    case "look_delta":
      return mod.sendCommand("look_delta", params);
    case "use_item":
      return mod.sendCommand("use_item");
    case "place_block":
      return mod.sendCommand("place_block");
    case "overlay_click":
      return mod.sendCommand("overlay_click", params);
    case "wait": {
      const seconds = Number(params.seconds) || 1;
      await new Promise((r) => setTimeout(r, seconds * 1e3));
      return { waited: seconds };
    }
    case "launch_minecraft": {
      return await launchMinecraft(params, mod);
    }
    case "kill_minecraft": {
      mod.killMc();
      return { killed: true };
    }
    case "get_minecraft_status": {
      await mod.checkAlive();
      return mod.getStatus();
    }
    case "install_version": {
      return await installVersion(params);
    }
    case "list_supported_versions": {
      return listSupportedVersions();
    }
    case "list_installed_versions": {
      const installed = listInstalledVersions();
      return { versions: installed, count: installed.length };
    }
    case "detect_java": {
      const javas = detectJavas();
      return javas.map((j) => ({
        version: j.version,
        vendor: j.vendor,
        isJdk: j.isJdk,
        path: j.path
      }));
    }
    case "create_offline_account": {
      const username = String(params.username || "");
      if (!username) throw new Error("Parameter 'username' is required.");
      const uuid = createOfflineUuid(username);
      const account = { type: "offline", uuid, username };
      const config = loadConfig();
      addAccount(config, account);
      if (!config.selected_account) config.selected_account = uuid;
      saveConfig(config);
      return { created: true, username, uuid };
    }
    case "list_accounts": {
      const config = loadConfig();
      return config.accounts.map((a) => ({
        username: accountUsername(a),
        uuid: accountUuid(a),
        type: a.type,
        selected: accountUuid(a) === config.selected_account
      }));
    }
    case "install_server": {
      return await installServerTool(params);
    }
    case "launch_server": {
      return await launchServerTool(params, mod);
    }
    case "serve": {
      return await serveTool(params, mod);
    }
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
}
async function launchMinecraft(params, mod) {
  const version = String(params.version || "");
  const loader = String(params.loader || "forge");
  if (!version) throw new Error("Parameter 'version' is required. Use list_supported_versions to see available versions.");
  const logs = [];
  const versionId = await ensureVersionInstalled(version, loader, (msg) => logs.push(msg));
  const vj = loadVersionMerged(versionId);
  const config = loadConfig();
  const account = selectedAccount(config);
  const mcpPort = config.mcp_port ?? await findFreePort();
  const extraJvmParts = [];
  if (config.java_args) extraJvmParts.push(config.java_args);
  if (params.jvm_args) extraJvmParts.push(String(params.jvm_args));
  const extraGameParts = [];
  if (config.game_args) extraGameParts.push(config.game_args);
  if (params.game_args) extraGameParts.push(String(params.game_args));
  if (params.server) {
    extraGameParts.push(`--server`, String(params.server));
    extraGameParts.push(`--port`, String(params.server_port ?? GAME.defaultServerPort));
  }
  const modJar = await ensureModJar(version, loader);
  const cmd = buildLaunchCommand({
    versionId,
    loader,
    mcpPort,
    modJar: modJar ?? void 0,
    maxMemoryMb: Number(params.memory) || config.max_memory_mb,
    minMemoryMb: Number(params.min_memory) || config.min_memory_mb,
    extraJvmArgs: extraJvmParts.length > 0 ? extraJvmParts.join(" ") : void 0,
    extraGameArgs: extraGameParts.length > 0 ? extraGameParts.join(" ") : void 0,
    javaPath: javaExecPath(config) ?? void 0,
    playerName: account ? accountUsername(account) : PLAYER.defaultName,
    uuid: account ? accountUuid(account) : PLAYER.defaultUuid,
    accessToken: account ? accountAccessToken(account) : PLAYER.defaultAccessToken,
    userType: account ? accountUserType(account) : PLAYER.defaultUserType,
    width: Number(params.width) || config.width,
    height: Number(params.height) || config.height,
    fullscreen: params.fullscreen === true || config.fullscreen
  }, vj, loadVersionsData());
  const mcDir_ = gameDirPath(config);
  if (!existsSync3(mcDir_)) mkdirSync3(mcDir_, { recursive: true });
  const child = spawn(cmd.java, cmd.args, {
    cwd: mcDir_,
    stdio: "ignore",
    detached: true
  });
  child.unref();
  child.on("error", (err) => {
    console.error(`[minecraft-mod-mcp] Launch failed: ${err.message}`);
  });
  mod.setMcProcess(child);
  return { launched: true, version: versionId, loader, pid: child.pid, mcpPort, javaVersion: cmd.javaVersion, logs };
}
async function installVersion(params) {
  const version = String(params.version || "");
  const loader = String(params.loader || "forge");
  if (!version) throw new Error("Parameter 'version' is required. Use list_supported_versions to see available versions.");
  const data = loadVersionsData();
  const vi = getVersion(data, version);
  if (!vi) {
    const available = getVersions(data).map((v) => v.mc_version).join(", ");
    throw new Error(`Unknown version: "${version}". Available: ${available}`);
  }
  const mcVersion = vi.mc_version;
  const baseVersionDir = join3(versionsDir(), mcVersion);
  const baseJsonPath = join3(baseVersionDir, `${mcVersion}.json`);
  const logs = [];
  if (!existsSync3(baseJsonPath)) {
    logs.push(`Downloading base MC ${mcVersion}...`);
    const manifest = await fetchVersionManifest();
    const mv = manifest.versions.find((v) => v.id === mcVersion);
    if (!mv) throw new Error(`Base MC version ${mcVersion} not found in manifest.`);
    const baseVj = await fetchVersionJson(mv.url);
    await downloadVersion(baseVj, (msg) => logs.push(msg));
  } else {
    logs.push(`Base MC ${mcVersion} already installed.`);
  }
  let loaderVersion;
  if (loader === "forge" && vi.forge) loaderVersion = vi.forge;
  else if (loader === "neoforge" && vi.neoforge) loaderVersion = vi.neoforge;
  else if (loader === "fabric") loaderVersion = DEFAULT_FABRIC_LOADER_VERSION;
  if (loaderVersion) {
    logs.push(`Installing ${loader} ${loaderVersion}...`);
    await downloadLoaderVersion(mcVersion, loader, loaderVersion, (msg) => logs.push(msg));
  }
  const versionId = getVersionForLoader(data, version, loader) ?? vi.version_id;
  return { installed: true, versionId, loader, loaderVersion, logs };
}
function listSupportedVersions() {
  const data = loadVersionsData();
  const versions = getVersions(data);
  return versions.map((v) => ({
    mc_version: v.mc_version,
    java: v.java,
    loaders: loaders(v),
    version_id: v.version_id
  }));
}
function mapDragParams(params) {
  return {
    x1: params.x_start,
    y1: params.y_start,
    x2: params.x_end,
    y2: params.y_end,
    button: params.button
  };
}
function formatResult(result) {
  if (result === null || result === void 0) return "null";
  if (typeof result === "string") return result;
  return JSON.stringify(result, null, 2);
}
async function installServerTool(params) {
  const { installServer } = await import("./server-CT5BA47F.js");
  const version = String(params.version || "");
  const loader = String(params.loader || "forge");
  const serverType = params.server_type || "vanilla";
  if (!version) throw new Error("Parameter 'version' is required.");
  const serverProps = {};
  if (params.port) serverProps.serverPort = Number(params.port);
  if (params.motd) serverProps.motd = String(params.motd);
  if (params.max_players) serverProps.maxPlayers = Number(params.max_players);
  if (params.gamemode) serverProps.gamemode = String(params.gamemode);
  if (params.difficulty) serverProps.difficulty = String(params.difficulty);
  if (params.online_mode != null) serverProps.onlineMode = params.online_mode === true;
  if (params.level_seed) serverProps.levelSeed = String(params.level_seed);
  if (params.level_name) serverProps.levelName = String(params.level_name);
  if (params.level_type) serverProps.levelType = String(params.level_type);
  if (params.white_list != null) serverProps.whiteList = params.white_list === true;
  const logs = [];
  const setup = await installServer(version, loader, (msg) => logs.push(msg), serverType, serverProps);
  return { installed: true, serverDir: setup.serverDir, jarPath: setup.jarPath, versionId: setup.versionId, serverType: setup.serverType, logs };
}
async function launchServerTool(params, _mod) {
  const { installServer, launchServer } = await import("./server-CT5BA47F.js");
  const version = String(params.version || "");
  const loader = String(params.loader || "forge");
  const serverType = params.server_type || "vanilla";
  const memory = Number(params.memory) || GAME.defaultServerMemoryMb;
  if (!version) throw new Error("Parameter 'version' is required.");
  const serverProps = {};
  if (params.port) serverProps.serverPort = Number(params.port);
  if (params.motd) serverProps.motd = String(params.motd);
  if (params.max_players) serverProps.maxPlayers = Number(params.max_players);
  if (params.gamemode) serverProps.gamemode = String(params.gamemode);
  if (params.difficulty) serverProps.difficulty = String(params.difficulty);
  const setup = await installServer(version, loader, void 0, serverType, serverProps);
  const srv = launchServer(setup, {
    maxMemoryMb: memory,
    minMemoryMb: params.min_memory ? Number(params.min_memory) : void 0,
    extraJvmArgs: params.jvm_args ? String(params.jvm_args) : void 0,
    extraGameArgs: params.game_args ? String(params.game_args) : void 0,
    port: params.port ? Number(params.port) : void 0,
    javaVersion: setup.javaVersion
  });
  return { launched: true, pid: srv.process.pid, port: srv.port, dir: srv.dir, serverType: setup.serverType };
}
async function serveTool(params, mod) {
  const { installServer, launchServer } = await import("./server-CT5BA47F.js");
  const version = String(params.version || "");
  const loader = String(params.loader || "forge");
  const serverType = params.server_type || "vanilla";
  const clientMem = Number(params.memory) || GAME.defaultMaxMemoryMb;
  const serverMem = Number(params.server_memory) || GAME.defaultServerMemoryMb;
  if (!version) throw new Error("Parameter 'version' is required.");
  const serverProps = {};
  if (params.port) serverProps.serverPort = Number(params.port);
  if (params.motd) serverProps.motd = String(params.motd);
  if (params.max_players) serverProps.maxPlayers = Number(params.max_players);
  if (params.gamemode) serverProps.gamemode = String(params.gamemode);
  if (params.difficulty) serverProps.difficulty = String(params.difficulty);
  const setup = await installServer(version, loader, void 0, serverType, serverProps);
  const modJar = await ensureModJar(version, loader);
  if (modJar && existsSync3(modJar)) {
    const serverModsDir = join3(setup.serverDir, SERVER.modsDirName);
    if (!existsSync3(serverModsDir)) mkdirSync3(serverModsDir, { recursive: true });
    const dest = join3(serverModsDir, modJar.split(/[/\\]/).pop());
    copyFileSync2(modJar, dest);
  }
  const serverPort = Number(params.port) || GAME.defaultServerPort;
  const srv = launchServer(setup, { maxMemoryMb: serverMem, port: serverPort, javaVersion: setup.javaVersion });
  const { waitForServer } = await import("./server-CT5BA47F.js");
  await waitForServer(setup.serverDir);
  const versionId = await ensureVersionInstalled(version, loader);
  const vj = loadVersionMerged(versionId);
  const config = loadConfig();
  const account = selectedAccount(config);
  const mcpPort = config.mcp_port ?? await findFreePort();
  const cmd = buildLaunchCommand({
    versionId,
    loader,
    mcpPort,
    modJar: await ensureModJar(version, loader) ?? void 0,
    maxMemoryMb: clientMem,
    minMemoryMb: config.min_memory_mb,
    extraJvmArgs: config.java_args,
    extraGameArgs: buildServerConnectArgs(SERVER.connectHost, srv.port, version),
    javaPath: javaExecPath(config) ?? void 0,
    playerName: account ? accountUsername(account) : PLAYER.defaultName,
    uuid: account ? accountUuid(account) : PLAYER.defaultUuid,
    accessToken: account ? accountAccessToken(account) : PLAYER.defaultAccessToken,
    userType: account ? accountUserType(account) : PLAYER.defaultUserType,
    width: Number(params.width) || config.width,
    height: Number(params.height) || config.height,
    fullscreen: params.fullscreen === true || config.fullscreen
  }, vj, loadVersionsData());
  const mcDir_ = gameDirPath(config);
  if (!existsSync3(mcDir_)) mkdirSync3(mcDir_, { recursive: true });
  const child = spawn(cmd.java, cmd.args, {
    cwd: mcDir_,
    stdio: "ignore",
    detached: true
  });
  child.unref();
  mod.setMcProcess(child);
  return {
    server: { pid: srv.process.pid, port: srv.port, dir: srv.dir, serverType: setup.serverType },
    client: { pid: child.pid, version: versionId, mcpPort, connectingTo: `${SERVER.connectHost}:${srv.port}` }
  };
}

// src/mcp/effectTools.ts
import { z as z2 } from "zod";
var projectId = z2.string().min(1).describe("Opaque project ID returned by effect_state or effect_create.");
var path = z2.string().min(1).describe("Output path within the project's permitted effect workspace.");
var overwrite = z2.boolean().default(false).describe("Allow replacing an existing output file.");
var tick = z2.number().finite().nonnegative().describe("Timeline position in game ticks.");
var EFFECT_RESPONSE_SCHEMA = z2.object({
  ok: z2.boolean(),
  data: z2.record(z2.unknown()).nullable(),
  error: z2.object({
    code: z2.string(),
    message: z2.string()
  }).nullable()
});
var EFFECT_TOOLS = [
  {
    name: "effect_resource_audit",
    description: "Read only explicitly declared permitted files/packs and report source versions, hashes, recursive reference candidates, missing/ambiguous dependencies, and cycles. Sources are ordered, first source wins. No undeclared development directory fallback, package mounting, writing, or runtime acceptance is performed. Unknown and builtin references remain unverified.",
    inputSchema: {
      sources: z2.array(z2.object({ path: z2.string().min(1), logicalPath: z2.string().min(1) })).min(1).max(64),
      roots: z2.array(z2.string().min(1)).min(1).optional()
    },
    readOnly: true
  },
  {
    name: "effect_animation_state",
    description: "Read named libraries, parameter/binding/marker summaries, complete library and project-test SNBT, runtime generation, playback handle IDs, clocks, and parameters. Does not switch the edited animation or change the project.",
    inputSchema: { projectId, controllerId: z2.string().uuid().optional() },
    readOnly: true
  },
  {
    name: "effect_animation_edit",
    description: "Edit one named controller and its project-test references through the shared authoring API. Read animationOperations from effect_capabilities. edit_list inserts, patches, removes, or moves parameters, bindings, markers, tracks, and nested track/clip lists using a definition UUID and a revision-scoped path. Commit the complete batch as one undo step, or dryRun without changing data/history. Existing handles retain their definition snapshots; later plays use the new library. Requires control mode.",
    inputSchema: {
      projectId,
      controllerId: z2.string().uuid(),
      expectedRevision: z2.string().min(1),
      operations: z2.array(z2.record(z2.unknown())).min(1).max(256),
      dryRun: z2.boolean().default(false)
    },
    readOnly: false
  },
  {
    name: "effect_animation_control",
    description: "Control named playback, the project-test cursor, or independent authoring previews without editing definitions/history. Read expectedRuntimeId and handleId from effect_animation_state. Authoring play requires animationId, definitionTarget, and expectedRevision; returns createdHandleId and leaves other tests running. Authoring seek/stop require that handleId. Non-play named actions require handleId; restarting an existing project test requires its current handleId. Seek moves authored curves only and does not resimulate particle/world history. Requires control mode.",
    inputSchema: {
      projectId,
      controllerId: z2.string().uuid(),
      expectedRuntimeId: z2.string().uuid(),
      target: z2.enum(["named", "project_test", "authoring"]).default("named"),
      animationId: z2.string().uuid().optional(),
      definitionTarget: z2.enum(["named", "project_test"]).optional(),
      expectedRevision: z2.string().optional(),
      action: z2.enum(["play", "pause", "resume", "stop", "seek", "parameters", "duration", "trigger", "signal"]),
      handleId: z2.string().uuid().optional(),
      name: z2.string().optional(),
      duration: z2.number().finite().optional(),
      restart: z2.boolean().optional(),
      inputs: z2.record(z2.array(z2.number().finite())).optional(),
      time: tick.optional(),
      channel: z2.string().optional(),
      dataSnbt: z2.string().optional()
    },
    readOnly: false
  },
  {
    name: "effect_capabilities",
    description: "Describe effect project kinds, editable operations, field types, units, ranges, enums, file formats, and permitted workspace. Read this before creating or editing an effect.",
    inputSchema: {},
    readOnly: true
  },
  {
    name: "effect_state",
    description: "Read the current effect project, stable project/object IDs, revision, configuration, animation tracks, preview state, and history. Without projectId, list all open effect projects. Before editing a particle FX project, pause its preview, then read this state: native FX playback changes persisted parameters and therefore revisions. Model projects do not have this restriction.",
    inputSchema: { projectId: projectId.optional() },
    readOnly: true
  },
  {
    name: "effect_create",
    description: "Create and open an editable particle FX or model effect project. Returns projectId and revision. Enter a Minecraft world before creating a project; the native editor requires world registries. Requires Minecraft MCP control mode.",
    inputSchema: {
      kind: z2.enum(["fx", "model"]).describe("Particle FX project or model effect project."),
      name: z2.string().min(1).optional().describe("Optional project display name.")
    },
    readOnly: false
  },
  {
    name: "effect_edit_batch",
    description: "Validate structured effect operations against the specified project revision, then commit them as one undoable edit. dryRun validates without committing. Read operation schemas from effect_capabilities. For particle FX projects, call effect_preview with action=pause, then effect_state, then submit that revision; playing FX timelines change persisted parameters and invalidate earlier revisions. Model projects do not require pausing. The edit preserves the preview pause state. Requires control mode.",
    inputSchema: {
      projectId,
      expectedRevision: z2.string().min(1).describe("Exact revision from the latest effect_state or mutation response."),
      operations: z2.array(z2.record(z2.unknown())).min(1).describe("Ordered operation objects described by effect_capabilities; nested configuration values are preserved."),
      dryRun: z2.boolean().default(false).describe("Validate the entire batch without changing the project.")
    },
    readOnly: false
  },
  {
    name: "effect_preview",
    description: "Control the project's native preview clock. Seek requires a time in ticks. Use the existing screenshot tool to inspect the rendered result. Requires control mode.",
    inputSchema: {
      projectId,
      action: z2.enum(["play", "pause", "stop", "seek"]),
      time: tick.optional()
    },
    readOnly: false
  },
  {
    name: "effect_save",
    description: "Save an editable effect project to its permitted workspace and return the actual output path. Existing files require overwrite=true. Requires control mode.",
    inputSchema: { projectId, path, overwrite },
    readOnly: false
  },
  {
    name: "effect_export",
    description: "Export an effect definition or resource pack through the project's native exporter and return the actual output path. Existing files require overwrite=true. Requires control mode.",
    inputSchema: {
      projectId,
      path,
      kind: z2.enum(["definition", "pack", "obj", "json"]).describe("Runtime definition, bundled resource pack, or geometry/JSON exchange from a model project. Exchange formats are not complete project backups."),
      flipV: z2.boolean().optional(),
      overwrite
    },
    readOnly: false
  },
  {
    name: "effect_history",
    description: "Undo or redo one native effect edit and return the resulting project revision and state. Requires control mode.",
    inputSchema: {
      projectId,
      action: z2.enum(["undo", "redo"])
    },
    readOnly: false
  },
  {
    name: "effect_open",
    description: "Open an existing .fxproj or .mfxproj from the permitted effect workspace in a new editor tab. Preserve every existing project and return the new projectId and revision. Requires a world and control mode.",
    inputSchema: { path },
    readOnly: false
  },
  {
    name: "effect_close",
    description: "Close only the specified effect project and its editor tab, release its preview runtime, and immediately invalidate its project ID. Requires the latest revision; dirty projects require explicit discardUnsaved=true. Other projects and windows remain open. Requires control mode.",
    inputSchema: {
      projectId,
      expectedRevision: z2.string().min(1),
      discardUnsaved: z2.boolean().default(false)
    },
    readOnly: false
  },
  {
    name: "effect_resources",
    description: "List native resource categories, provider entries, workspace files, and resource limits. Optionally restrict to a project, category, or workspace directory. Native resource paths returned here can be passed to effect_resource_read or used as a copy source.",
    inputSchema: {
      projectId: projectId.optional(),
      category: z2.string().min(1).optional(),
      directory: z2.string().min(1).optional()
    },
    readOnly: true
  },
  {
    name: "effect_resource_read",
    description: "Read a native resource or a permitted workspace resource file with its actual NBT/SNBT representation. Use a category and resource path from effect_resources. Does not change the project or files.",
    inputSchema: {
      projectId: projectId.optional(),
      category: z2.string().min(1),
      path: z2.string().min(1).describe("Native resource path or file path within the permitted effect workspace.")
    },
    readOnly: true
  },
  {
    name: "effect_resource_write",
    description: "Validate and write a native resource to the permitted effect workspace. Supply exactly one of source (a native resource path) or snbt (the complete native resource value). Existing files require overwrite=true. File writes are outside FX undo history. Requires control mode.",
    inputSchema: {
      projectId: projectId.optional(),
      category: z2.string().min(1),
      path,
      source: z2.string().min(1).optional(),
      snbt: z2.string().min(1).optional(),
      overwrite
    },
    readOnly: false
  },
  {
    name: "effect_texture_import",
    description: "Import a base64 PNG into assets/<namespace>/textures/...png and register the native texture without a global reload. Read size limits from effect_resources. Existing files require overwrite=true. File writes are outside FX undo history. Requires control mode.",
    inputSchema: {
      path,
      base64: z2.string().min(1).describe("Raw PNG bytes encoded as base64, without a data URL prefix."),
      overwrite
    },
    readOnly: false
  },
  {
    name: "effect_view_state",
    description: "Read the specified project's preview camera, display settings, and game-embedded capture regions. Does not activate an editor tab or change the project. The existing screenshot tools use this state for scene/editor capture.",
    inputSchema: { projectId },
    readOnly: true
  },
  {
    name: "effect_view",
    description: "Set the specified project's native preview camera and display options, then return its view state. Settings do not create FX undo steps or edit the asset. Read supported fields from effect_view_state. Requires control mode.",
    inputSchema: {
      projectId,
      center: z2.tuple([z2.number().finite(), z2.number().finite(), z2.number().finite()]).optional(),
      zoom: z2.number().finite().positive().optional(),
      yaw: z2.number().finite().optional().describe("Camera yaw in degrees."),
      pitch: z2.number().finite().optional().describe("Camera pitch in degrees."),
      orthographic: z2.boolean().optional(),
      orthoRange: z2.number().finite().positive().optional(),
      bloomEnabled: z2.boolean().optional(),
      effectsEnabled: z2.boolean().optional(),
      shapeVisible: z2.boolean().optional(),
      cullBoxVisible: z2.boolean().optional()
    },
    readOnly: false
  }
];

// src/mcp/effectHandlers.ts
var EFFECT_ERRORS = {
  invalidResponse: { code: "invalid_response", message: "The effect service returned an invalid response." },
  missingSeekTime: { code: "invalid_request", message: "Seeking an effect preview requires time in ticks." },
  requestFailed: { code: "transport_error", message: "The effect request failed." }
};
function registerEffectHandlers(server, mod) {
  for (const tool of EFFECT_TOOLS) {
    server.registerTool(tool.name, {
      description: tool.description,
      inputSchema: tool.inputSchema,
      outputSchema: EFFECT_RESPONSE_SCHEMA.shape,
      annotations: {
        readOnlyHint: tool.readOnly,
        destructiveHint: !tool.readOnly,
        idempotentHint: tool.readOnly,
        openWorldHint: false
      }
    }, async (params) => {
      if (tool.name === "effect_preview" && params.action === "seek" && params.time === void 0) {
        return formatResponse(failure(EFFECT_ERRORS.missingSeekTime));
      }
      try {
        const result = await mod.sendCommand(tool.name, params);
        return formatResponse(normalizeResponse(result));
      } catch (err) {
        const message = err instanceof Error ? err.message : EFFECT_ERRORS.requestFailed.message;
        return formatResponse(failure({ code: EFFECT_ERRORS.requestFailed.code, message }));
      }
    });
  }
}
function normalizeResponse(result) {
  if (typeof result !== "object" || result === null || Array.isArray(result)) {
    return failure(EFFECT_ERRORS.invalidResponse);
  }
  const body = result;
  const parsed = EFFECT_RESPONSE_SCHEMA.safeParse({
    ok: body.ok,
    data: body.data ?? null,
    error: body.error ?? null
  });
  if (!parsed.success || parsed.data.ok === (parsed.data.error !== null)) {
    return failure(EFFECT_ERRORS.invalidResponse);
  }
  return parsed.data;
}
function failure(error) {
  return { ok: false, data: null, error };
}
function formatResponse(response) {
  return {
    content: [{ type: "text", text: JSON.stringify(response, null, 2) }],
    structuredContent: response,
    isError: !response.ok
  };
}

// src/mcp/controlTools.ts
import { z as z3 } from "zod";
var mode = z3.enum(["direct", "simulate"]).default("simulate");
var position = z3.object({ x: z3.number().finite(), y: z3.number().finite(), z: z3.number().finite() });
var blockPosition = z3.object({ x: z3.number().int(), y: z3.number().int(), z: z3.number().int() });
var hand = z3.enum(["main", "off"]).default("main");
var inputKeys = z3.object({
  forward: z3.boolean().optional(),
  backward: z3.boolean().optional(),
  left: z3.boolean().optional(),
  right: z3.boolean().optional(),
  jump: z3.boolean().optional(),
  sneak: z3.boolean().optional(),
  sprint: z3.boolean().optional(),
  attack: z3.boolean().optional(),
  use: z3.boolean().optional()
});
var CONTROL_TOOLS = [
  {
    name: "gui_control",
    description: "Inspect GUI widgets with screenId/widgetId. Direct mode invokes visible enabled buttons, focuses widgets or sets EditBox text. Simulate mode sends click/drag/scroll/type/key/close through the current screen. Mutations require the latest screenId and MCP control mode. Coordinates explicitly use GUI units or framebuffer pixels; no OS input is used. accepted means the GUI handled the event, not that a workflow completed.",
    inputSchema: {
      action: z3.enum(["list", "click", "drag", "scroll", "type", "key", "close", "set_text", "focus"]),
      mode,
      screenId: z3.string().min(1).optional(),
      widgetId: z3.string().min(1).optional(),
      coordinateSpace: z3.enum(["gui", "framebuffer"]).default("gui"),
      x: z3.number().finite().nonnegative().optional(),
      y: z3.number().finite().nonnegative().optional(),
      endX: z3.number().finite().nonnegative().optional(),
      endY: z3.number().finite().nonnegative().optional(),
      button: z3.number().int().min(0).max(2).default(0),
      scrollX: z3.number().finite().min(-100).max(100).default(0),
      scrollY: z3.number().finite().min(-100).max(100).optional(),
      text: z3.string().max(4096).optional(),
      key: z3.number().int().min(0).max(348).optional(),
      modifiers: z3.number().int().min(0).max(15).default(0)
    }
  },
  {
    name: "player_control",
    description: "Read local player/inventory. Direct integrated-server actions: teleport, set_state(health/food/saturation), gamemode, set_slot(item/count), select_slot. Simulate actions: input(keys held for 1..200 client ticks), release, look(yaw/pitch), select_slot, attack current aimed entity, use held item. Input is released on expiry, GUI opening, player change or control exit. Multiplayer simulation uses normal game networking; direct mutations require the integrated server. Mutations require MCP control mode.",
    inputSchema: {
      action: z3.enum(["get", "inventory", "teleport", "set_state", "gamemode", "set_slot", "select_slot", "input", "release", "look", "attack", "use"]),
      mode,
      position: position.optional(),
      state: z3.object({
        health: z3.number().finite().nonnegative().optional(),
        food: z3.number().int().min(0).max(20).optional(),
        saturation: z3.number().finite().min(0).max(20).optional()
      }).optional(),
      gamemode: z3.enum(["survival", "creative", "adventure", "spectator"]).optional(),
      slot: z3.number().int().nonnegative().optional(),
      item: z3.string().min(1).optional(),
      count: z3.number().int().nonnegative().optional(),
      keys: inputKeys.optional(),
      ticks: z3.number().int().min(1).max(200).optional(),
      yaw: z3.number().finite().min(-180).max(180).optional(),
      pitch: z3.number().finite().min(-90).max(90).optional(),
      hand
    }
  },
  {
    name: "entity_control",
    description: "List/get client-loaded entities by UUID, radius<=128 and optional type; list is capped at 256. Direct integrated-server actions: spawn, teleport, set_state(name/health/noAi/invulnerable/glowing), navigate(position/speed), stop navigation, remove. Direct mutation of players is rejected. Simulate attack/interact requires the named UUID to be the current crosshair target, in reach and visible; normal server rules apply. Mutations require MCP control mode.",
    inputSchema: {
      action: z3.enum(["list", "get", "spawn", "teleport", "set_state", "navigate", "stop", "remove", "attack", "interact"]),
      mode,
      uuid: z3.string().uuid().optional(),
      center: position.optional(),
      radius: z3.number().finite().min(0).max(128).default(32),
      limit: z3.number().int().min(1).max(256).default(64),
      type: z3.string().min(1).optional(),
      position: position.optional(),
      speed: z3.number().finite().min(0.01).max(4).optional(),
      state: z3.object({
        name: z3.string().max(256).optional(),
        health: z3.number().finite().nonnegative().optional(),
        noAi: z3.boolean().optional(),
        invulnerable: z3.boolean().optional(),
        glowing: z3.boolean().optional()
      }).optional(),
      hand
    }
  },
  {
    name: "block_control",
    description: "Read a loaded client block and state properties. Direct integrated-server set/fill/break uses normal world updates; fill is inclusive and capped at 4096 loaded blocks with full bounds preflight. Supply registry block ID and optional string properties; block-entity NBT is not modified. Simulate start_break/continue_break/stop_break/use/place uses the held item and current crosshair BlockHitResult within player reach. accepted is not proof of a completed break or placement. Mutations require MCP control mode.",
    inputSchema: {
      action: z3.enum(["get", "set", "fill", "break", "start_break", "continue_break", "stop_break", "use", "place"]),
      mode,
      position: blockPosition.optional(),
      to: blockPosition.optional(),
      block: z3.string().min(1).optional(),
      properties: z3.record(z3.string()).optional(),
      drops: z3.boolean().default(false),
      hand
    }
  }
];

// src/mcp/controlHandlers.ts
function registerControlHandlers(server, mod) {
  for (const tool of CONTROL_TOOLS) {
    server.registerTool(tool.name, {
      description: tool.description,
      inputSchema: tool.inputSchema,
      outputSchema: EFFECT_RESPONSE_SCHEMA.shape,
      annotations: {
        readOnlyHint: false,
        destructiveHint: true,
        idempotentHint: false,
        openWorldHint: false
      }
    }, async (params) => {
      try {
        const raw = await mod.sendCommand(tool.name, params);
        const parsed = EFFECT_RESPONSE_SCHEMA.safeParse(
          typeof raw === "object" && raw !== null && !Array.isArray(raw) ? {
            ok: raw.ok,
            data: raw.data ?? null,
            error: raw.error ?? null
          } : null
        );
        if (!parsed.success || parsed.data.ok === (parsed.data.error !== null)) {
          return failure2("INVALID_RESPONSE", "Game control returned an invalid structured response.");
        }
        const result = parsed.data;
        return {
          content: [{ type: "text", text: JSON.stringify(result) }],
          structuredContent: result,
          isError: !result.ok
        };
      } catch (error) {
        return failure2("TRANSPORT_ERROR", error instanceof Error ? error.message : "Game control request failed.");
      }
    });
  }
}
function failure2(code, message) {
  const result = { ok: false, data: null, error: { code, message } };
  return {
    content: [{ type: "text", text: JSON.stringify(result) }],
    structuredContent: result,
    isError: true
  };
}

// src/mcp/transport.ts
function createMcpServer(mod) {
  const mcpServer = new McpServer({
    name: LAUNCHER.mcpServerName,
    version: LAUNCHER.version
  });
  registerHandlers(mcpServer, mod);
  registerEffectHandlers(mcpServer, mod);
  registerControlHandlers(mcpServer, mod);
  return mcpServer;
}
async function connectStdio(mcpServer) {
  const transport = new StdioServerTransport();
  await mcpServer.connect(transport);
}

// src/server.ts
async function startServer(opts = {}) {
  const mod = new ModClient();
  const mcpServer = createMcpServer(mod);
  await connectStdio(mcpServer);
  if (opts.autoDiscover !== false) {
    discoverInBackground(mod, opts.discoverTimeout);
  }
}
function discoverInBackground(mod, timeout) {
  (async () => {
    log(`Scanning for Minecraft mod (${PORT_START}-${PORT_END})...`);
    const status = await findMod(PORT_START, PORT_END);
    if (status) {
      await mod.discover();
      log(`Found mod: ${status.version}-${status.loader} (pid ${status.pid}) on port ${status.port}`);
    } else {
      log("No mod found. Background scanning every 5s...");
      const t = timeout || MCP.discoverTimeoutMs;
      const result = await mod.waitForConnection(t);
      if (result) {
        log(`Mod connected: ${result.version}-${result.loader} on port ${result.port}`);
      } else {
        log("Discover timeout. Will keep retrying on tool calls.");
      }
    }
  })().catch((err) => {
    log(`Discovery error: ${err.message}`);
  });
}
function log(msg) {
  console.error(`[${SERVER.userAgent}] ${msg}`);
}
export {
  defaults_exports as DEFAULTS,
  ModClient,
  PORT_END,
  PORT_START,
  TOOLS,
  accountAccessToken,
  accountUserType,
  accountUsername,
  accountUuid,
  addAccount,
  assetsDir,
  buildLaunchCommand,
  classpathSeparator2 as classpathSeparator,
  collectAllArgs,
  createOfflineUuid,
  crossHomedir,
  detectJavas,
  detectRuntime,
  downloadFile,
  downloadVersion,
  ensureJavaForLaunch,
  ensureJavaInstalled,
  ensureVersionInstalled,
  fetchVersionJson,
  fetchVersionManifest,
  findFreePort,
  findMod,
  gameDirPath,
  getNativeClassifier,
  getVersion,
  getVersionById,
  getVersionForLoader,
  getVersions,
  installedJavaHome,
  isMacos,
  isWindows,
  javaExec,
  javaExecPath,
  librariesDir,
  libraryMavenPath,
  listInstalledVersions,
  loadConfig,
  loadVersion,
  loadVersionJson,
  loadVersionMerged,
  loadVersionsData,
  loaders,
  mcDir,
  pollDeviceAuth,
  refreshToken,
  removeAccount,
  resolveClasspath,
  classpathSeparator as runtimeCpSep,
  saveConfig,
  selectedAccount,
  shouldApply,
  startDeviceAuth,
  startServer,
  versionsDir,
  waitForMod
};
