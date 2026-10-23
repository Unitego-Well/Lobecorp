#!/usr/bin/env node
import {
  MCP,
  MOD,
  init_esm_shims
} from "./chunk-GFHVXK2C.js";

// src/consts.ts
init_esm_shims();
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

export {
  PORT_START,
  PORT_END,
  HEARTBEAT_TIMEOUT_MS,
  isModStatus
};
