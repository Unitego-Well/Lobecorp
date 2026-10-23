#!/usr/bin/env node
import {
  HEARTBEAT_TIMEOUT_MS,
  PORT_END,
  PORT_START,
  isModStatus
} from "./chunk-TPCBWG4X.js";
import {
  MCP,
  MOD,
  init_esm_shims
} from "./chunk-GFHVXK2C.js";

// src/discovery/scanner.ts
init_esm_shims();
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

export {
  probePort,
  findMod,
  waitForMod,
  findFreePort
};
