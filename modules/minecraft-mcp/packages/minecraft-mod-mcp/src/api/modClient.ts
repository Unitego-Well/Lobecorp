import { execSync } from "node:child_process";
import { PORT_START, PORT_END, type ModStatus } from "../consts.js";
import { findMod, waitForMod, probePort } from "../discovery/scanner.js";
import { MCP, MOD } from "../mc/defaults.js";

const SCREENSHOT_ERRORS = {
    notConnected: "Mod not connected",
    requestFailed: "Screenshot failed",
    invalidRegion: "region must be game, editor or scene",
    invalidSize: "maxSize must be a positive 32-bit integer",
    missingProject: "projectId is required for editor and scene captures",
    unavailableView: "Project view is unavailable",
    hiddenView: "Capture requires the active project embedded in the game window with a visible region",
    invalidBounds: "Project capture bounds are invalid",
} as const;

export class ModClient {
  private modPort: number | null = null;
  private baseUrl = "";
  private modAlive = false;
  private mcProcess: ReturnType<typeof import("node:child_process").spawn> | null = null;

  get connected(): boolean {
    return this.modPort !== null;
  }

  getStatus(): { connected: boolean; port: number | null; processAlive: boolean } {
    return {
      connected: this.connected,
      port: this.modPort,
      processAlive: this.modAlive || (this.mcProcess !== null && this.mcProcess.exitCode === null),
    };
  }

  async discover(startPort = PORT_START, endPort = PORT_END): Promise<ModStatus | null> {
    const status = await findMod(startPort, endPort);
    this.modAlive = status !== null;
    if (status) {
      this.modPort = status.port;
      this.baseUrl = `http://${MCP.bindAddress}:${status.port}`;
    }
    return status;
  }

  async waitForConnection(timeoutMs: number = MCP.waitTimeoutMs): Promise<ModStatus | null> {
    const status = await waitForMod(PORT_START, PORT_END, timeoutMs);
    this.modAlive = status !== null;
    if (status) {
      this.modPort = status.port;
      this.baseUrl = `http://${MCP.bindAddress}:${status.port}`;
    }
    return status;
  }

  async checkAlive(): Promise<boolean> {
    if (!this.modPort) {
      this.modAlive = false;
      return false;
    }
    const s = await probePort(this.modPort);
    this.modAlive = s !== null;
    if (!this.modAlive) this.disconnect();
    return this.modAlive;
  }

  private disconnect(): void {
    this.modAlive = false;
    this.modPort = null;
    this.baseUrl = "";
  }

  async sendCommand(method: string, params?: Record<string, unknown>): Promise<unknown> {
    if (!this.baseUrl) {
      await this.discover();
      if (!this.baseUrl) throw new Error("Mod not connected");
    }
    const body: Record<string, unknown> = { cmd: method, ...(params || {}) };
    try {
      const resp = await fetch(`${this.baseUrl}${MOD.cmdEndpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!resp.ok) {
        const text = await resp.text();
        if (resp.status === 502 || resp.status === 503) this.disconnect();
        throw new Error(`Mod returned ${resp.status}: ${text}`);
      }
      return resp.json();
    } catch (err: any) {
      if (err.cause?.code === "ECONNREFUSED") this.disconnect();
      throw err;
    }
  }

    async screenshot(params: Record<string, unknown> = {}): Promise<unknown> {
        if (!this.baseUrl) {
            await this.discover();
            if (!this.baseUrl) {
                throw new Error(SCREENSHOT_ERRORS.notConnected);
            }
        }
        const capture = await this.resolveScreenshotParams(params);
        const query = new URLSearchParams(Object.entries(capture).map(([key, value]): [string, string] => [key, String(value)]));
        try {
            const resp = await fetch(`${this.baseUrl}${MOD.screenshotEndpoint}?${query}`);
            if (!resp.ok) {
                throw new Error(`${SCREENSHOT_ERRORS.requestFailed}: ${resp.status}: ${await resp.text()}`);
            }
            return resp.json();
        } catch (err: any) {
            if (err.cause?.code === "ECONNREFUSED") {
                this.disconnect();
            }
            throw err;
        }
    }

    async screenshotToFile(params: Record<string, unknown>): Promise<unknown> {
        const capture = await this.resolveScreenshotParams(params);
        return this.sendCommand("screenshot_to_file", {...capture, path: params.path});
    }

    private async resolveScreenshotParams(params: Record<string, unknown>): Promise<Record<string, unknown>> {
        const region = params.region ?? "game";
        if (region !== "game" && region !== "editor" && region !== "scene") {
            throw new Error(SCREENSHOT_ERRORS.invalidRegion);
        }
        if (params.maxSize !== undefined && (!Number.isInteger(params.maxSize) || Number(params.maxSize) <= 0 || Number(params.maxSize) > 2147483647)) {
            throw new Error(SCREENSHOT_ERRORS.invalidSize);
        }
        const capture: Record<string, unknown> = {region};
        if (params.maxSize !== undefined) {
            capture.maxSize = params.maxSize;
        }
        if (params.projectId !== undefined) {
            capture.projectId = params.projectId;
        }
        if (region === "game") {
            return capture;
        }
        if (typeof params.projectId !== "string" || !params.projectId.trim()) {
            throw new Error(SCREENSHOT_ERRORS.missingProject);
        }

        const response = await this.sendCommand("effect_view_state", {projectId: params.projectId}) as {
            ok?: boolean;
            error?: { message?: string };
            data?: {
                active?: boolean;
                embedded?: boolean;
                guiScale?: number;
                captureRegions?: Record<string, { x: number; y: number; width: number; height: number } | null>;
            };
        };
        if (response.ok !== true) {
            throw new Error(response.error?.message ?? SCREENSHOT_ERRORS.unavailableView);
        }
        const data = response.data;
        const bounds = data?.captureRegions?.[region];
        if (data?.active !== true || data.embedded !== true || !bounds) {
            throw new Error(SCREENSHOT_ERRORS.hiddenView);
        }
        if (![bounds.x, bounds.y, bounds.width, bounds.height, data.guiScale].every(value => typeof value === "number" && Number.isFinite(value))
            || bounds.x < 0 || bounds.y < 0 || bounds.width <= 0 || bounds.height <= 0 || Number(data.guiScale) <= 0) {
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

  setMcProcess(proc: ReturnType<typeof import("node:child_process").spawn>) {
    this.mcProcess = proc;
  }

  getMcProcess() {
    return this.mcProcess;
  }

  killMc() {
    if (this.mcProcess && this.mcProcess.exitCode === null) {
      if (process.platform === "win32") {
        try { execSync(`taskkill /PID ${this.mcProcess.pid} /T /F`, { stdio: "ignore" }); } catch {}
      } else {
        this.mcProcess.kill("SIGTERM");
      }
      this.mcProcess = null;
    }
  }
}
