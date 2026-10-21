import { MCP, MOD } from "./mc/defaults.js";

const configuredPort = process.env.MC_MCP_PORT;
const fixedPort = configuredPort === undefined ? undefined : Number(configuredPort);
if (fixedPort !== undefined && (!Number.isInteger(fixedPort) || fixedPort < 1 || fixedPort > 65535)) {
    throw new Error("MC_MCP_PORT must be an integer between 1 and 65535");
}

export const PORT_START = fixedPort ?? MCP.portStart;
export const PORT_END = fixedPort ?? MCP.portEnd;
export const HEARTBEAT_TIMEOUT_MS = MCP.heartbeatTimeoutMs;

export interface ModStatus {
  ok: boolean;
  type: string;
  version: string;
  loader: string;
  forgeVersion?: string;
  pid: number;
  port: number;
  uptime: number;
}

export function isModStatus(obj: unknown): obj is ModStatus {
  if (typeof obj !== "object" || obj === null) return false;
  const o = obj as Record<string, unknown>;
  return o.ok === true && o.type === MOD.statusType && typeof o.port === "number";
}

export interface ServerPorts {
  mcpPort: number;
  modPort: number | null;
}
