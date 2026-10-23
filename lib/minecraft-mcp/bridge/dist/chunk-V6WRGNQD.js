#!/usr/bin/env node
import {
  assetsDir,
  detectJavas,
  findJavaForVersion,
  isMacos,
  isWindows,
  librariesDir,
  mcDir,
  versionsDir
} from "./chunk-HRWYZQKF.js";
import {
  DOWNLOAD,
  FABRIC,
  PATHS,
  __require,
  init_esm_shims
} from "./chunk-GFHVXK2C.js";

// src/mc/versionsData.ts
init_esm_shims();
import { readFileSync, existsSync } from "node:fs";
import { resolve, join } from "node:path";

// src/mc/versions-embedded.json
var versions_embedded_default = {
  fg_eras: {
    fg12_gtnh: {
      key: "fg12_gtnh",
      fg_version: "1.2.11",
      gradle: "4.4.1",
      plugin_id: "forge",
      java: 8,
      min_mc: "1.7.2",
      max_mc: "1.7.10",
      extra_repo: "https://jitpack.io"
    },
    fg21: {
      key: "fg21",
      fg_version: "2.1-SNAPSHOT",
      gradle: "2.14",
      plugin_id: "net.minecraftforge.gradle.forge",
      java: 8,
      min_mc: "1.8",
      max_mc: "1.8.9"
    },
    fg22: {
      key: "fg22",
      fg_version: "2.2-SNAPSHOT",
      gradle: "2.14",
      plugin_id: "net.minecraftforge.gradle.forge",
      java: 8,
      min_mc: "1.9",
      max_mc: "1.11.2"
    },
    fg23: {
      key: "fg23",
      fg_version: "2.3-SNAPSHOT",
      gradle: "4.10.3",
      plugin_id: "net.minecraftforge.gradle.forge",
      java: 8,
      min_mc: "1.12",
      max_mc: "1.12.2"
    },
    fg3: {
      key: "fg3",
      fg_version: "3.+",
      gradle: "4.10.3",
      plugin_id: "net.minecraftforge.gradle",
      java: 8,
      min_mc: "1.13.2",
      max_mc: "1.14.4"
    },
    fg41: {
      key: "fg41",
      fg_version: "[4.1,4.2)",
      gradle: "6.9.4",
      plugin_id: "net.minecraftforge.gradle",
      java: 8,
      min_mc: "1.15",
      max_mc: "1.16.5"
    },
    fg51: {
      key: "fg51",
      fg_version: "5.1.+",
      gradle: "7.6.4",
      plugin_id: "net.minecraftforge.gradle",
      java: 17,
      min_mc: "1.17.1",
      max_mc: "1.19.2"
    },
    fg6: {
      key: "fg6",
      fg_version: "[6.0,6.2)",
      gradle: "8.5",
      plugin_id: "net.minecraftforge.gradle",
      java: 17,
      min_mc: "1.19.3",
      max_mc: "1.21.3"
    },
    fg7: {
      key: "fg7",
      fg_version: "7.0.23",
      gradle: "9.6.1",
      plugin_id: "net.minecraftforge.gradle",
      java: 21,
      min_mc: "1.21.4",
      max_mc: "99.99.99"
    }
  },
  versions: {
    "1_7_2": {
      forge: "1.7.2-10.12.1.1109",
      fg_era: "fg12_gtnh",
      java: 8,
      version_id: "1.7.2-Forge10.12.1.1109"
    },
    "1_7_10": {
      forge: "1.7.10-10.13.4.1614-1.7.10",
      fg_era: "fg12_gtnh",
      java: 8,
      version_id: "1.7.10-Forge10.13.4.1614-1.7.10"
    },
    "1_8": {
      forge: "1.8-11.14.4.1563",
      fg_era: "fg21",
      java: 8,
      version_id: "1.8-forge-11.14.4.1563",
      mappings: "snapshot_20141111"
    },
    "1_8_8": {
      forge: "1.8.8-11.15.0.1655",
      fg_era: "fg21",
      java: 8,
      version_id: "1.8.8-forge-11.15.0.1655",
      mappings: "snapshot_20141111"
    },
    "1_8_9": {
      forge: "1.8.9-11.15.1.2318-1.8.9",
      fg_era: "fg21",
      java: 8,
      version_id: "1.8.9-forge1.8.9-11.15.1.2318-1.8.9",
      mappings: "snapshot_20160113"
    },
    "1_9_4": {
      forge: "1.9.4-12.17.0.2317-1.9.4",
      fg_era: "fg22",
      java: 8,
      version_id: "1.9.4-forge1.9.4-12.17.0.2317-1.9.4",
      mappings: "snapshot_20160518"
    },
    "1_10_2": {
      forge: "1.10.2-12.18.3.2511",
      fg_era: "fg22",
      java: 8,
      version_id: "1.10.2-forge1.10.2-12.18.3.2511",
      mappings: "snapshot_20160518"
    },
    "1_11": {
      forge: "1.11-13.19.1.2189",
      fg_era: "fg22",
      java: 8,
      version_id: "1.11-Forge13.19.1.2189",
      mappings: "snapshot_20170111"
    },
    "1_11_2": {
      forge: "1.11.2-13.20.1.2588",
      fg_era: "fg22",
      java: 8,
      version_id: "1.11.2-forge1.11.2-13.20.1.2588",
      mappings: "snapshot_20170111"
    },
    "1_12": {
      forge: "1.12-14.21.1.2387",
      fg_era: "fg23",
      java: 8,
      version_id: "1.12-Forge14.21.1.2387",
      mappings: "snapshot_20171003"
    },
    "1_12_1": {
      forge: "1.12.1-14.22.1.2478",
      fg_era: "fg23",
      java: 8,
      version_id: "1.12.1-Forge14.22.1.2478",
      mappings: "snapshot_20171003"
    },
    "1_12_2": {
      forge: "1.12.2-14.23.5.2847",
      fg_era: "fg23",
      java: 8,
      version_id: "1.12.2-forge1.12.2-14.23.5.2847",
      mappings: "snapshot_20171003"
    },
    "1_13_2": {
      forge: "1.13.2-25.0.223",
      fg_era: "fg3",
      java: 8,
      version_id: "1.13.2-forge-25.0.223",
      mappings: "snapshot_20190314"
    },
    "1_14_2": {
      forge: "1.14.2-26.0.63",
      fg_era: "fg3",
      java: 8,
      version_id: "1.14.2-Forge26.0.63",
      mappings: "snapshot_20190616",
      fabric_yarn: "1.14.2+build.7"
    },
    "1_14_3": {
      forge: "1.14.3-27.0.60",
      fg_era: "fg3",
      java: 8,
      version_id: "1.14.3-Forge27.0.60",
      mappings: "snapshot_20190716",
      fabric_yarn: "1.14.3+build.9"
    },
    "1_14_4": {
      forge: "1.14.4-28.2.28",
      fg_era: "fg3",
      java: 8,
      version_id: "1.14.4-forge-28.2.28",
      mappings: "snapshot_20200119",
      fabric_yarn: "1.14.4+build.18"
    },
    "1_15": {
      forge: "1.15-29.0.4",
      fg_era: "fg41",
      java: 8,
      version_id: "1.15-Forge29.0.4",
      mappings: "snapshot_20200119",
      fabric_yarn: "1.15+build.2"
    },
    "1_15_1": {
      forge: "1.15.1-30.0.51",
      fg_era: "fg41",
      java: 8,
      version_id: "1.15.1-Forge30.0.51",
      mappings: "snapshot_20200224",
      fabric_yarn: "1.15.1+build.10"
    },
    "1_15_2": {
      forge: "1.15.2-31.2.60",
      fg_era: "fg41",
      java: 8,
      version_id: "1.15.2-forge-31.2.60",
      mappings: "snapshot_20200224-1.15.1",
      fabric_yarn: "1.15.2+build.17"
    },
    "1_16_1": {
      forge: "1.16.1-32.0.108",
      fg_era: "fg41",
      java: 8,
      version_id: "1.16.1-Forge32.0.108",
      mappings: "snapshot_20200723",
      fabric_yarn: "1.16.1+build.10"
    },
    "1_16_2": {
      forge: "1.16.2-33.0.61",
      fg_era: "fg41",
      java: 8,
      version_id: "1.16.2-Forge33.0.61",
      mappings: "snapshot_20200916",
      fabric_yarn: "1.16.2+build.1"
    },
    "1_16_3": {
      forge: "1.16.3-34.1.0",
      fg_era: "fg41",
      java: 8,
      version_id: "1.16.3-Forge34.1.0",
      mappings: "snapshot_20200514",
      fabric_yarn: "1.16.3+build.7"
    },
    "1_16_4": {
      forge: "1.16.4-35.1.4",
      fg_era: "fg41",
      java: 8,
      version_id: "1.16.4-Forge35.1.4",
      mappings: "snapshot_20210309",
      fabric_yarn: "1.16.4+build.1"
    },
    "1_16_5": {
      forge: "1.16.5-36.2.42",
      fg_era: "fg41",
      java: 16,
      version_id: "1.16.5-forge-36.2.42",
      mappings: "snapshot_20210309",
      fabric_yarn: "1.16.5+build.10"
    },
    "1_17_1": {
      forge: "1.17.1-37.1.1",
      fg_era: "fg51",
      java: 17,
      version_id: "1.17.1-forge-37.1.1",
      mappings: "official_1.17.1",
      fabric_yarn: "1.17.1+build.65"
    },
    "1_18": {
      forge: "1.18-38.0.17",
      fg_era: "fg51",
      java: 17,
      version_id: "1.18-Forge38.0.17",
      mappings: "official_1.18",
      fabric_yarn: "1.18+build.1"
    },
    "1_18_1": {
      forge: "1.18.1-39.1.0",
      fg_era: "fg51",
      java: 17,
      version_id: "1.18.1-Forge39.1.0",
      mappings: "official_1.18.1",
      fabric_yarn: "1.18.1+build.8"
    },
    "1_18_2": {
      forge: "1.18.2-40.3.12",
      fg_era: "fg51",
      java: 17,
      version_id: "1.18.2-forge-40.3.12",
      mappings: "official_1.18.2",
      fabric_yarn: "1.18.2+build.4"
    },
    "1_19": {
      forge: "1.19-41.1.0",
      fg_era: "fg51",
      java: 17,
      version_id: "1.19-Forge41.1.0",
      mappings: "official_1.19",
      fabric_yarn: "1.19+build.4"
    },
    "1_19_1": {
      forge: "1.19.1-42.0.9",
      fg_era: "fg51",
      java: 17,
      version_id: "1.19.1-Forge42.0.9",
      mappings: "official_1.19.1",
      fabric_yarn: "1.19.1+build.4"
    },
    "1_19_2": {
      forge: "1.19.2-43.5.0",
      fg_era: "fg51",
      java: 17,
      version_id: "1.19.2-Forge43.5.0",
      mappings: "official_1.19.2",
      fabric_yarn: "1.19.2+build.28"
    },
    "1_19_3": {
      forge: "1.19.3-44.1.0",
      fg_era: "fg6",
      java: 17,
      version_id: "1.19.3-Forge44.1.0",
      mappings: "official_1.19.3",
      fabric_yarn: "1.19.3+build.2"
    },
    "1_19_4": {
      forge: "1.19.4-45.4.3",
      fg_era: "fg6",
      java: 17,
      version_id: "1.19.4-forge-45.4.3",
      mappings: "official_1.19.4",
      fabric_yarn: "1.19.4+build.1"
    },
    "1_20": {
      forge: "1.20-46.0.14",
      fg_era: "fg6",
      java: 17,
      version_id: "1.20-Forge46.0.14",
      mappings: "official_1.20",
      fabric_yarn: "1.20+build.1"
    },
    "1_20_1": {
      forge: "1.20.1-47.4.10",
      fg_era: "fg6",
      java: 17,
      version_id: "1.20.1-forge-47.4.10",
      mappings: "official_1.20.1",
      fabric_yarn: "1.20.1+build.10"
    },
    "1_20_2": {
      forge: "1.20.2-48.1.0",
      fg_era: "fg6",
      java: 17,
      version_id: "1.20.2-Forge48.1.0",
      mappings: "official_1.20.2",
      fabric_yarn: "1.20.2+build.1"
    },
    "1_20_3": {
      forge: "1.20.3-49.0.2",
      fg_era: "fg6",
      java: 17,
      version_id: "1.20.3-Forge49.0.2",
      mappings: "official_1.20.3",
      fabric_yarn: "1.20.3+build.1"
    },
    "1_20_4": {
      forge: "1.20.4-49.2.0",
      fg_era: "fg6",
      java: 17,
      version_id: "1.20.4-Forge49.2.0",
      mappings: "official_1.20.4",
      fabric_yarn: "1.20.4+build.3",
      neoforge: "49.0.51"
    },
    "1_20_6": {
      forge: "1.20.6-50.2.8",
      fg_era: "fg6",
      java: 21,
      version_id: "1.20.6-forge-50.2.8",
      mappings: "official_1.20.6",
      fabric_yarn: "1.20.6+build.1",
      neoforge: "20.6.134",
      mdg: "2.0.141"
    },
    "1_21": {
      forge: "1.21-51.0.33",
      fg_era: "fg6",
      java: 21,
      version_id: "1.21-Forge51.0.33",
      mappings: "official_1.21",
      fabric_yarn: "1.21+build.1"
    },
    "1_21_1": {
      forge: "1.21.1-52.1.0",
      fg_era: "fg6",
      java: 21,
      version_id: "1.21.1-Forge52.1.0",
      mappings: "official_1.21.1",
      fabric_yarn: "1.21.1+build.3",
      neoforge: "21.1.172",
      mdg: "2.0.141"
    },
    "1_21_3": {
      forge: "1.21.3-53.1.0",
      fg_era: "fg6",
      java: 21,
      version_id: "1.21.3-Forge53.1.0",
      mappings: "official_1.21.3",
      fabric_yarn: "1.21.3+build.1",
      neoforge: "21.3.63",
      mdg: "2.0.141"
    },
    "1_21_4": {
      forge: "1.21.4-54.1.14",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.4-Forge54.1.14",
      mappings: "official_1.21.4",
      fabric_yarn: "1.21.4+build.1",
      neoforge: "21.4.0-beta",
      mdg: "2.0.141"
    },
    "1_21_5": {
      forge: "1.21.5-55.1.0",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.5-Forge55.1.0",
      mappings: "official_1.21.5",
      fabric_yarn: "1.21.5+build.1"
    },
    "1_21_6": {
      forge: "1.21.6-56.0.9",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.6-Forge56.0.9",
      mappings: "official_1.21.6",
      fabric_yarn: "1.21.6+build.1"
    },
    "1_21_7": {
      forge: "1.21.7-57.0.3",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.7-Forge57.0.3",
      mappings: "official_1.21.7",
      fabric_yarn: "1.21.7+build.1"
    },
    "1_21_8": {
      forge: "1.21.8-58.1.0",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.8-Forge58.1.0",
      mappings: "official_1.21.8",
      fabric_yarn: "1.21.8+build.1"
    },
    "1_21_9": {
      forge: "1.21.9-59.0.5",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.9-Forge59.0.5",
      mappings: "official_1.21.9",
      fabric_yarn: "1.21.9+build.1"
    },
    "1_21_10": {
      forge: "1.21.10-60.1.0",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.10-Forge60.1.0",
      mappings: "official_1.21.10",
      fabric_yarn: "1.21.10+build.1"
    },
    "1_21_11": {
      forge: "1.21.11-61.1.5",
      fg_era: "fg7",
      java: 21,
      version_id: "1.21.11-forge-61.1.5",
      mappings: "official_1.21.11",
      fabric_yarn: "1.21.11+build.6",
      neoforge: "21.11.42",
      mdg: "2.0.141"
    },
    "26_1_2": {
      forge: "26.1.2-64.0.8",
      fg_era: "fg7",
      java: 25,
      version_id: "26.1.2-forge-64.0.8",
      mappings: "official_26.1.2",
      neoforge: "26.1.2.36-beta",
      mdg: "2.0.141"
    },
    "26_2": {
      forge: "26.2-65.1.3",
      fg_era: "fg7",
      java: 25,
      version_id: "26.2-forge-65.1.3",
      mappings: "official_26.2",
      fabric_loader: "0.19.5",
      neoforge: "26.2.0.75",
      mdg: "2.0.141"
    }
  },
  api_groups: {
    "1_7_2": "legacy17",
    "1_7_10": "legacy17",
    "1_8": "legacy",
    "1_8_8": "legacy",
    "1_8_9": "legacy",
    "1_9_4": "legacy",
    "1_10_2": "legacy",
    "1_11": "legacy",
    "1_11_2": "legacy",
    "1_12": "legacy",
    "1_12_1": "legacy",
    "1_12_2": "legacy",
    "1_13_2": "fg3",
    "1_14_2": "fg3",
    "1_14_3": "fg3",
    "1_14_4": "fg3",
    "1_15": "fg4",
    "1_15_1": "fg4",
    "1_15_2": "fg4",
    "1_16_1": "fg4",
    "1_16_2": "fg4",
    "1_16_3": "fg4",
    "1_16_4": "fg4",
    "1_16_5": "fg4",
    "1_17_1": "fg5",
    "1_18": "fg5",
    "1_18_1": "fg5",
    "1_18_2": "fg5",
    "1_19": "fg5",
    "1_19_1": "fg5",
    "1_19_2": "fg5",
    "1_19_3": "fg6",
    "1_19_4": "fg6",
    "1_20": "fg6",
    "1_20_1": "fg6",
    "1_20_2": "fg6",
    "1_20_3": "fg6",
    "1_20_4": "fg6",
    "1_20_6": "fg6",
    "1_21": "fg6",
    "1_21_1": "fg6",
    "1_21_3": "fg6",
    "1_21_4": "mc26",
    "1_21_5": "mc26",
    "1_21_6": "mc26",
    "1_21_7": "mc26",
    "1_21_8": "mc26",
    "1_21_9": "mc26",
    "1_21_10": "mc26",
    "1_21_11": "mc26",
    "26_1_2": "mc26",
    "26_2": "mc26"
  },
  fabric_loom: {
    "1.7.2": "1.7-SNAPSHOT",
    "1.7.10": "1.7-SNAPSHOT",
    "1.8": "1.7-SNAPSHOT",
    "1.8.8": "1.7-SNAPSHOT",
    "1.8.9": "1.7-SNAPSHOT",
    "1.9.4": "1.7-SNAPSHOT",
    "1.10.2": "1.7-SNAPSHOT",
    "1.11": "1.7-SNAPSHOT",
    "1.11.2": "1.7-SNAPSHOT",
    "1.12": "1.7-SNAPSHOT",
    "1.12.1": "1.7-SNAPSHOT",
    "1.12.2": "1.7-SNAPSHOT",
    "1.13.2": "1.7-SNAPSHOT",
    "1.14.2": "0.8-SNAPSHOT",
    "1.14.3": "0.8-SNAPSHOT",
    "1.14.4": "0.8-SNAPSHOT",
    "1.15": "0.8-SNAPSHOT",
    "1.15.1": "0.8-SNAPSHOT",
    "1.15.2": "0.8-SNAPSHOT",
    "1.16.1": "0.12-SNAPSHOT",
    "1.16.2": "0.12-SNAPSHOT",
    "1.16.3": "0.12-SNAPSHOT",
    "1.16.4": "0.12-SNAPSHOT",
    "1.16.5": "0.12-SNAPSHOT",
    "1.17.1": "1.0-SNAPSHOT",
    "1.18": "1.0-SNAPSHOT",
    "1.18.1": "1.0-SNAPSHOT",
    "1.18.2": "1.0-SNAPSHOT",
    "1.19": "1.0-SNAPSHOT",
    "1.19.1": "1.0-SNAPSHOT",
    "1.19.2": "1.0-SNAPSHOT",
    "1.19.3": "1.3-SNAPSHOT",
    "1.19.4": "1.3-SNAPSHOT",
    "1.20": "1.3-SNAPSHOT",
    "1.20.1": "1.3-SNAPSHOT",
    "1.20.2": "1.3-SNAPSHOT",
    "1.20.3": "1.3-SNAPSHOT",
    "1.20.4": "1.3-SNAPSHOT",
    "1.20.6": "1.5-SNAPSHOT",
    "1.21": "1.5-SNAPSHOT",
    "1.21.1": "1.5-SNAPSHOT",
    "1.21.3": "1.5-SNAPSHOT",
    "1.21.4": "1.7-SNAPSHOT",
    "1.21.5": "1.7-SNAPSHOT",
    "1.21.6": "1.7-SNAPSHOT",
    "1.21.7": "1.7-SNAPSHOT",
    "1.21.8": "1.7-SNAPSHOT",
    "1.21.9": "1.7-SNAPSHOT",
    "1.21.10": "1.7-SNAPSHOT",
    "1.21.11": "1.14-SNAPSHOT",
    "26.1.2": "1.7-SNAPSHOT",
    "26.2": "1.17-SNAPSHOT"
  },
  neoforge_gradle: {
    mdg_2_0_prefix: "2.0",
    mdg_2_0_gradle: "9.3.1",
    mdg_other_gradle: "8.10"
  },
  legacy: {
    eras: [
      "fg12_gtnh",
      "fg21",
      "fg22",
      "fg23",
      "fg3",
      "fg41"
    ]
  }
};

// src/mc/versionsData.ts
var _cached = null;
function parseTomlBasic(content) {
  const result = {};
  let current = result;
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const headerMatch = line.match(/^\[(.+)\]$/);
    if (headerMatch) {
      const keys = headerMatch[1].split(".");
      let obj = result;
      for (let i = 0; i < keys.length - 1; i++) {
        if (!obj[keys[i]] || typeof obj[keys[i]] !== "object") {
          obj[keys[i]] = {};
        }
        obj = obj[keys[i]];
      }
      const target = {};
      obj[keys[keys.length - 1]] = target;
      current = target;
      continue;
    }
    const kvMatch = line.match(/^(\S+)\s*=\s*(.+)$/);
    if (kvMatch) {
      const [, key, rawValue] = kvMatch;
      let value;
      const trimmed = rawValue.trim();
      if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
        value = trimmed.slice(1, -1);
      } else if (/^-?\d+$/.test(trimmed)) {
        value = parseInt(trimmed, 10);
      } else if (/^-?\d+\.\d+$/.test(trimmed)) {
        value = parseFloat(trimmed);
      } else if (trimmed === "true") {
        value = true;
      } else if (trimmed === "false") {
        value = false;
      } else {
        value = trimmed;
      }
      current[key] = value;
    }
  }
  return result;
}
function findVersionsToml() {
  const candidates = [
    resolve("packages", "launcher", "shared", "config", "versions.toml"),
    resolve("..", "packages", "launcher", "shared", "config", "versions.toml"),
    resolve("..", "..", "packages", "launcher", "shared", "config", "versions.toml")
  ];
  const home = process.env.MINECRAFT_MCP_HOME;
  if (home) candidates.push(join(home, "packages", "launcher", "shared", "config", "versions.toml"));
  for (const p of candidates) {
    if (existsSync(p)) return p;
  }
  return null;
}
function loadVersionsData() {
  if (_cached) return _cached;
  const tomlPath = findVersionsToml();
  if (!tomlPath) {
    _cached = versions_embedded_default;
    return _cached;
  }
  try {
    const content = readFileSync(tomlPath, "utf-8");
    const raw = parseTomlBasic(content);
    const fgEras = raw.fg_eras ?? {};
    const versions = raw.versions ?? {};
    const apiGroups = raw.api_groups ?? {};
    const fabricLoom = raw.fabric_loom ?? {};
    const neoforgeGradle = raw.neoforge_gradle ?? {};
    const legacy = raw.legacy ?? { eras: [] };
    _cached = {
      fg_eras: fgEras,
      versions,
      api_groups: apiGroups,
      fabric_loom: fabricLoom,
      neoforge_gradle: neoforgeGradle,
      legacy
    };
    return _cached;
  } catch {
    _cached = versions_embedded_default;
    return _cached;
  }
}

// src/mc/versions.ts
init_esm_shims();
import { existsSync as existsSync2, readFileSync as readFileSync2, readdirSync } from "node:fs";
import { join as join2 } from "node:path";
var DEFAULT_FABRIC_LOADER_VERSION = FABRIC.defaultLoaderVersion;
function tomlKey(mc) {
  return mc.replace(/\./g, "_");
}
function loaders(info) {
  const result = [];
  if (info.forge) result.push("forge");
  if (info.neoforge) result.push("neoforge");
  if (hasFabric(info)) result.push("fabric");
  return result;
}
function hasFabric(info) {
  return Boolean(info.fabric_yarn || info.fabric_loader);
}
function getVersions(data) {
  return Object.entries(data.versions).map(([key, raw]) => ({
    mc_version: key.replace(/_/g, "."),
    forge: raw.forge,
    fg_era: raw.fg_era,
    java: raw.java,
    mappings: raw.mappings,
    version_id: raw.version_id,
    neoforge: raw.neoforge ?? null,
    mdg: raw.mdg ?? null,
    fabric_yarn: raw.fabric_yarn ?? null,
    fabric_loader: raw.fabric_loader ?? null
  }));
}
function getVersion(data, mc) {
  const key = tomlKey(mc);
  const raw = data.versions[key];
  if (!raw) return null;
  return {
    mc_version: mc,
    forge: raw.forge,
    fg_era: raw.fg_era,
    java: raw.java,
    mappings: raw.mappings,
    version_id: raw.version_id,
    neoforge: raw.neoforge ?? null,
    mdg: raw.mdg ?? null,
    fabric_yarn: raw.fabric_yarn ?? null,
    fabric_loader: raw.fabric_loader ?? null
  };
}
function getVersionById(data, versionId) {
  for (const [key, raw] of Object.entries(data.versions)) {
    if (raw.version_id === versionId) {
      return {
        mc_version: key.replace(/_/g, "."),
        forge: raw.forge,
        fg_era: raw.fg_era,
        java: raw.java,
        mappings: raw.mappings,
        version_id: raw.version_id,
        neoforge: raw.neoforge ?? null,
        mdg: raw.mdg ?? null,
        fabric_yarn: raw.fabric_yarn ?? null,
        fabric_loader: raw.fabric_loader ?? null
      };
    }
  }
  const asMc = versionId.replace(/_/g, ".");
  for (const [key, raw] of Object.entries(data.versions)) {
    if (key.replace(/_/g, ".") === asMc) {
      return {
        mc_version: key.replace(/_/g, "."),
        forge: raw.forge,
        fg_era: raw.fg_era,
        java: raw.java,
        mappings: raw.mappings,
        version_id: raw.version_id,
        neoforge: raw.neoforge ?? null,
        mdg: raw.mdg ?? null,
        fabric_yarn: raw.fabric_yarn ?? null,
        fabric_loader: raw.fabric_loader ?? null
      };
    }
  }
  return null;
}
function getVersionForLoader(data, mc, loader) {
  const info = getVersion(data, mc);
  if (!info) return null;
  switch (loader) {
    case "forge":
      return discoverLoaderVersionId(mc, loader) ?? info.version_id;
    case "neoforge":
      return info.neoforge ? discoverLoaderVersionId(mc, loader, info.neoforge) : null;
    case "fabric":
      return hasFabric(info) ? discoverLoaderVersionId(mc, loader) : null;
  }
}
function discoverLoaderVersionId(mcVersion, loader, loaderVer) {
  const vDir = versionsDir();
  if (!existsSync2(vDir)) return null;
  try {
    for (const entry of readdirSync(vDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const dirName = entry.name;
      const jsonPath = join2(vDir, dirName, `${dirName}.json`);
      if (!existsSync2(jsonPath)) continue;
      try {
        const raw = readFileSync2(jsonPath, "utf-8");
        const vj = JSON.parse(raw);
        const inherits = vj.inheritsFrom ?? vj.id;
        if (inherits !== mcVersion) continue;
        const mc = vj.mainClass?.toLowerCase() ?? "";
        if (loader === "neoforge" && mc.includes("neoforged")) return dirName;
        if (loader === "fabric" && mc.includes("fabricmc")) return dirName;
        if (loader === "forge" && (mc.includes("minecraftforge") || mc.includes("forgebootstrap") || mc.includes("modlauncher") || mc.includes("launchwrapper"))) return dirName;
      } catch {
        continue;
      }
    }
  } catch {
  }
  if (loader === "neoforge" && loaderVer) return `${mcVersion}-neoforge-${loaderVer}`;
  if (loader === "fabric") return `fabric-loader-${DEFAULT_FABRIC_LOADER_VERSION}-${mcVersion}`;
  return null;
}
function getFgEra(data, key) {
  return data.fg_eras[key] ?? null;
}

// src/mc/versionJson.ts
init_esm_shims();
import { existsSync as existsSync3, readFileSync as readFileSync3 } from "node:fs";
import { join as join3 } from "node:path";
function loadVersionJson(path) {
  const data = readFileSync3(path, "utf-8");
  return JSON.parse(data);
}
function loadVersion(versionId) {
  const dir = versionsDir();
  const path = join3(dir, versionId, `${versionId}.json`);
  if (!existsSync3(path)) {
    throw new Error(
      `Version "${versionId}" is not installed.
  Missing: ${path}
  Run: minecraft-mod-mcp install ${versionId}`
    );
  }
  return loadVersionJson(path);
}
function inferMcVersion(versionId) {
  const data = loadVersionsData();
  const info = getVersionById(data, versionId);
  return info?.mc_version ?? null;
}
function loadVersionMerged(versionId) {
  let primary = loadVersion(versionId);
  primary = mergeWithParents(primary);
  if (!primary.assets && !primary.inheritsFrom) {
    const mcVer = inferMcVersion(versionId);
    if (mcVer && mcVer !== versionId) {
      try {
        const parent = loadVersion(mcVer);
        const merged = mergeWithParents(parent);
        return {
          id: primary.id,
          type: primary.type || merged.type,
          mainClass: primary.mainClass || merged.mainClass,
          inheritsFrom: mcVer,
          libraries: mergeLibraries(merged.libraries ?? [], primary.libraries ?? []),
          arguments: mergeArguments(merged.arguments, primary.arguments),
          minecraftArguments: primary.minecraftArguments || merged.minecraftArguments,
          assetIndex: primary.assetIndex || merged.assetIndex,
          assets: primary.assets || merged.assets,
          downloads: primary.downloads || merged.downloads,
          jar: primary.jar || merged.jar,
          releaseTime: primary.releaseTime || merged.releaseTime
        };
      } catch {
      }
    }
  }
  return primary;
}
function mergeWithParents(vj) {
  if (!vj.inheritsFrom) return vj;
  let parent;
  try {
    parent = loadVersion(vj.inheritsFrom);
  } catch {
    return vj;
  }
  parent = mergeWithParents(parent);
  return {
    id: vj.id,
    type: vj.type || parent.type,
    mainClass: vj.mainClass || parent.mainClass,
    inheritsFrom: vj.inheritsFrom,
    libraries: mergeLibraries(parent.libraries ?? [], vj.libraries ?? []),
    arguments: mergeArguments(parent.arguments, vj.arguments),
    minecraftArguments: vj.minecraftArguments || parent.minecraftArguments,
    assetIndex: vj.assetIndex || parent.assetIndex,
    assets: vj.assets || parent.assets,
    downloads: vj.downloads || parent.downloads,
    jar: vj.jar || parent.jar,
    releaseTime: vj.releaseTime || parent.releaseTime
  };
}
function libBaseKey(name) {
  const parts = name.split(":");
  return parts.length >= 2 ? `${parts[0]}:${parts[1]}` : name;
}
function libRuleKey(lib) {
  if (!lib.rules || lib.rules.length === 0) return libBaseKey(lib.name) + ":all";
  const oses = lib.rules.filter((r) => r.os?.name).map((r) => `${r.action}:${r.os.name}`).sort().join(",");
  return libBaseKey(lib.name) + ":" + oses;
}
function mergeLibraries(parent, child) {
  const parentByName = /* @__PURE__ */ new Map();
  for (const lib of parent) {
    if (lib.name) parentByName.set(lib.name, lib);
  }
  const result = [];
  const childFullKeys = /* @__PURE__ */ new Set();
  const childBaseKeys = /* @__PURE__ */ new Set();
  for (const lib of child) {
    if (lib.name) {
      childFullKeys.add(libRuleKey(lib));
      childBaseKeys.add(libBaseKey(lib.name));
      if (!lib.downloads) {
        const parentLib = parentByName.get(lib.name);
        if (parentLib?.downloads) {
          result.push({ ...lib, downloads: parentLib.downloads });
          continue;
        }
      }
      result.push(lib);
    }
  }
  for (const lib of parent) {
    if (!lib.name) continue;
    if (childFullKeys.has(libRuleKey(lib))) continue;
    if (!childBaseKeys.has(libBaseKey(lib.name))) {
      result.push(lib);
    } else if (lib.rules && lib.rules.length > 0) {
      result.push(lib);
    }
  }
  return result;
}
function mergeArguments(parent, child) {
  if (!parent && !child) return void 0;
  if (!parent) return child;
  if (!child) return parent;
  return {
    game: [...parent.game ?? [], ...child.game ?? []],
    jvm: [...parent.jvm ?? [], ...child.jvm ?? []]
  };
}
function collectAllArgs(vj) {
  const gameArgs = [];
  const jvmArgs = [];
  if (vj.arguments) {
    if (vj.arguments.game) {
      for (const av of vj.arguments.game) {
        if (typeof av === "string") {
          gameArgs.push(av);
        } else if (shouldApply(av.rules)) {
          if (typeof av.value === "string") gameArgs.push(av.value);
          else gameArgs.push(...av.value);
        }
      }
    }
    if (vj.arguments.jvm) {
      for (const av of vj.arguments.jvm) {
        if (typeof av === "string") {
          jvmArgs.push(av);
        } else if (shouldApply(av.rules)) {
          if (typeof av.value === "string") jvmArgs.push(av.value);
          else jvmArgs.push(...av.value);
        }
      }
    }
  }
  if (vj.minecraftArguments) {
    gameArgs.push(...vj.minecraftArguments.split(/\s+/));
  }
  return { jvmArgs, gameArgs };
}
function shouldApply(rules, features) {
  if (rules.length === 0) return true;
  const feats = features ?? {};
  let allow = false;
  let deny = false;
  for (const rule of rules) {
    if (rule.features) {
      const featMatch = Object.entries(rule.features).every(([k, v]) => {
        if (typeof v === "boolean") return feats[k] === v;
        return feats[k] != null;
      });
      if (!featMatch) continue;
    }
    const osMatch = rule.os ? matchOs(rule.os) : true;
    if (osMatch) {
      if (rule.action === "allow") allow = true;
      else if (rule.action === "disallow") deny = true;
    }
  }
  return allow && !deny;
}
function matchOs(os) {
  if (os.name) {
    const nameOk = (() => {
      switch (os.name) {
        case "windows":
          return isWindows();
        case "linux":
          return !isWindows() && !isMacos();
        case "osx":
          return isMacos();
        default:
          return true;
      }
    })();
    if (!nameOk) return false;
  }
  return true;
}
function libraryPath(name) {
  const parts = name.split(":");
  if (parts.length < 3) return join3(librariesDir(), name.replace(/:/g, "/"));
  const group = parts[0].replace(/\./g, "/");
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3];
  const filename = classifier ? `${artifact}-${version}-${classifier}.jar` : `${artifact}-${version}.jar`;
  return join3(librariesDir(), group, artifact, version, filename);
}
function libraryMavenPath(name) {
  const parts = name.split(":");
  if (parts.length < 3) return name.replace(/:/g, "/");
  const group = parts[0].replace(/\./g, "/");
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3];
  return classifier ? `${group}/${artifact}/${version}/${artifact}-${version}-${classifier}.jar` : `${group}/${artifact}/${version}/${artifact}-${version}.jar`;
}
function resolveClasspath(libraries) {
  const classpath = [];
  for (const lib of libraries) {
    if (lib.rules && !shouldApply(lib.rules)) continue;
    if (lib.downloads?.artifact) {
      const path2 = join3(librariesDir(), lib.downloads.artifact.path);
      if (existsSync3(path2)) classpath.push(path2);
      continue;
    }
    const path = libraryPath(lib.name);
    if (existsSync3(path)) {
      classpath.push(path);
    } else {
      const universalPath = path.replace(/\.jar$/, "-universal.jar");
      if (existsSync3(universalPath)) classpath.push(universalPath);
    }
  }
  const patched = patchClasspath(classpath);
  return patched;
}
function patchClasspath(classpath) {
  return classpath.map((p) => {
    if (p.includes("launchwrapper-1.9")) {
      const better = p.replace(/launchwrapper[\\/]1\.9[\\/]/, "launchwrapper/1.12/").replace("launchwrapper-1.9.jar", "launchwrapper-1.12.jar");
      if (existsSync3(better)) return better;
    }
    return p;
  });
}

// src/mc/proxy.ts
init_esm_shims();
import { execSync } from "node:child_process";
var cachedProxy;
var proxySetup = false;
var proxyActive = false;
function detectSystemProxy() {
  if (cachedProxy !== void 0) return cachedProxy;
  cachedProxy = detectFromEnv() ?? detectFromWindowsRegistry() ?? detectFromMacos() ?? null;
  return cachedProxy;
}
function proxyUrl() {
  const p = detectSystemProxy();
  if (!p) return null;
  return `http://${p.host}:${p.port}`;
}
async function setupGlobalProxy() {
  if (proxySetup) return;
  proxySetup = true;
  const url = proxyUrl();
  if (!url) return;
  try {
    const undici = await import("undici");
    if (undici.ProxyAgent && undici.setGlobalDispatcher) {
      const agent = new undici.ProxyAgent(url);
      undici.setGlobalDispatcher(agent);
      proxyActive = true;
      console.warn(`[WARN] Using system proxy: ${url}`);
      const testOk = await probeProxyTls();
      if (!testOk) {
        console.warn(`[WARN] Proxy TLS interception detected \u2014 disabling TLS verification`);
        process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
      }
    }
  } catch {
  }
}
async function probeProxyTls() {
  try {
    const resp = await fetch("https://piston-meta.mojang.com/", { method: "HEAD", signal: AbortSignal.timeout(8e3) });
    return resp.ok || resp.status < 500;
  } catch {
    return false;
  }
}
var sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function nativeDownload(url, destPath) {
  const { execFile } = await import("child_process");
  const { existsSync: fsExists } = await import("fs");
  if (process.platform === "win32") {
    const curlArgs = [
      "-fSL",
      "--retry",
      "5",
      "--retry-all-errors",
      "--retry-delay",
      "3",
      "--connect-timeout",
      "60",
      "-o",
      destPath,
      url
    ];
    try {
      const { execFile: ef } = await import("child_process");
      await new Promise((resolve2, reject) => {
        ef("curl", curlArgs, { timeout: 9e5, windowsHide: true }, (err) => {
          if (err) reject(err);
          else resolve2();
        });
      });
      return;
    } catch (curlErr) {
      if (curlErr?.code !== "ENOENT") throw curlErr;
      const ps = ["Invoke-WebRequest", "-Uri", url, "-OutFile", destPath, "-TimeoutSec", "300"];
      return new Promise((resolve2, reject) => {
        execFile("powershell", ["-NoProfile", "-Command", ...ps], {
          timeout: 36e4,
          windowsHide: true
        }, (err) => {
          if (err) reject(err);
          else resolve2();
        });
      });
    }
  }
  return new Promise((resolve2, reject) => {
    const { existsSync: existsSync6 } = __require("fs");
    const curlArgs = ["-fSL"];
    if (existsSync6(destPath)) curlArgs.push("-C", "-");
    curlArgs.push(
      "--retry",
      "5",
      "--retry-all-errors",
      "--retry-delay",
      "3",
      "--connect-timeout",
      "60",
      "-o",
      destPath,
      url
    );
    execFile("curl", curlArgs, { timeout: 6e5 }, (err) => {
      if (err) reject(err);
      else resolve2();
    });
  });
}
async function fetchWithFallback(url, init) {
  const maxRetries = 3;
  const proxyTimeout = 15e3;
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), proxyTimeout);
      const resp = await fetch(url, { ...init, signal: controller.signal });
      clearTimeout(timer);
      return resp;
    } catch (err) {
      const isProxy = proxyActive;
      const isRetryable = /ECONNRESET|ETIMEDOUT|ECONNREFUSED|abort|socket hang up|fetch failed/i.test(err.message || "");
      if (isProxy && isRetryable && attempt === 0) {
        console.warn(`[WARN] Proxy request failed (${err.message}), retrying direct...`);
        try {
          const undici = await import("undici");
          if (undici.setGlobalDispatcher) {
            undici.setGlobalDispatcher(new undici.Agent());
          }
          proxyActive = false;
        } catch {
        }
      }
      if (isRetryable && attempt < maxRetries - 1) {
        console.warn(`[WARN] Retrying (${attempt + 1}/${maxRetries}): ${url.slice(0, 80)}...`);
        await sleep(2e3 * (attempt + 1));
        continue;
      }
      throw err;
    }
  }
  throw new Error("unreachable");
}
async function downloadWithNativeFallback(url, destPath) {
  try {
    const resp = await fetchWithFallback(url);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const buf = Buffer.from(await resp.arrayBuffer());
    const { writeFileSync: writeFileSync3 } = await import("fs");
    writeFileSync3(destPath, buf);
  } catch (fetchErr) {
    console.warn(`[WARN] Node fetch failed for ${url.slice(0, 60)}... (${fetchErr.cause?.code || fetchErr.message}), trying native download...`);
    await nativeDownload(url, destPath);
  }
}
function detectFromEnv() {
  for (const key of ["https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY", "all_proxy", "ALL_PROXY"]) {
    const val = process.env[key];
    if (!val) continue;
    const parsed = parseProxyUrl(val);
    if (parsed) return parsed;
  }
  return null;
}
function parseProxyUrl(raw) {
  const s = raw.trim();
  try {
    const url = new URL(s.startsWith("http") ? s : `http://${s}`);
    const host = url.hostname || null;
    const port = parseInt(url.port || "80", 10);
    if (host && port > 0) return { host, port };
  } catch {
  }
  const m = s.match(/^([^:/]+):(\d+)$/);
  if (m) return { host: m[1], port: parseInt(m[2], 10) };
  return null;
}
function detectFromWindowsRegistry() {
  if (process.platform !== "win32") return null;
  try {
    const regBase = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    const serverOut = execSync(`reg query "${regBase}" /v ProxyServer`, { encoding: "utf-8", stdio: ["pipe", "pipe", "ignore"] });
    const serverMatch = serverOut.match(/REG_SZ\s+(.+)/);
    if (serverMatch) {
      const raw = serverMatch[1].trim();
      if (raw) {
        const httpsEntry = raw.split(";").find((e) => e.startsWith("https="));
        const candidate = httpsEntry ? httpsEntry.split("=")[1] : raw;
        const parsed = parseProxyUrl(candidate);
        if (parsed) return parsed;
      }
    }
  } catch {
  }
  return null;
}
function detectFromMacos() {
  if (process.platform !== "darwin") return null;
  try {
    let services = ["Wi-Fi", "Ethernet"];
    try {
      const list = execSync("networksetup -listallnetworkservices", { encoding: "utf-8", stdio: ["pipe", "pipe", "ignore"] });
      services = list.split(/\r?\n/).slice(1).map((s) => s.trim()).filter(Boolean);
    } catch {
    }
    for (const service of services) {
      for (const proto of ["getwebproxy", "getsecurewebproxy"]) {
        const out = execSync(`networksetup -${proto} "${service}"`, { encoding: "utf-8", stdio: ["pipe", "pipe", "ignore"] });
        const lines = out.split(/\r?\n/).map((l) => l.trim());
        const enabled = lines[0] === "Yes";
        const host = lines[1];
        const port = parseInt(lines[2], 10);
        if (enabled && host && port > 0) return { host, port };
      }
    }
  } catch {
  }
  return null;
}
function gradleProxyEnv() {
  const proxy = detectSystemProxy();
  if (!proxy) return {};
  const opts = [
    `-Dhttp.proxyHost=${proxy.host}`,
    `-Dhttp.proxyPort=${proxy.port}`,
    `-Dhttps.proxyHost=${proxy.host}`,
    `-Dhttps.proxyPort=${proxy.port}`
  ].join(" ");
  const existing = process.env.GRADLE_OPTS;
  return { GRADLE_OPTS: existing ? `${existing} ${opts}` : opts };
}
function javaProxyArgs() {
  const proxy = detectSystemProxy();
  if (!proxy) return [];
  return [
    `-Dhttp.proxyHost=${proxy.host}`,
    `-Dhttp.proxyPort=${proxy.port}`,
    `-Dhttps.proxyHost=${proxy.host}`,
    `-Dhttps.proxyPort=${proxy.port}`
  ];
}

// src/mc/download.ts
init_esm_shims();
import { createHash } from "node:crypto";
import { existsSync as existsSync5, mkdirSync as mkdirSync2, writeFileSync as writeFileSync2, readFileSync as readFileSync5, readdirSync as readdirSync2 } from "node:fs";
import { dirname as dirname2, join as join5 } from "node:path";
import { spawnSync as spawnSync2 } from "node:child_process";

// src/mc/forgeProcessor.ts
init_esm_shims();
import { existsSync as existsSync4, mkdirSync, readFileSync as readFileSync4, writeFileSync } from "node:fs";
import { dirname, join as join4 } from "node:path";
import { inflateRawSync } from "node:zlib";
import { spawnSync } from "node:child_process";
var DEFAULT_EXT = "jar";
function readManifestMainClass(jarPath) {
  const sys = spawnSync("unzip", ["-p", jarPath, "META-INF/MANIFEST.MF"], { maxBuffer: 4 * 1024 * 1024 });
  if (sys.status === 0 && sys.stdout && sys.stdout.length > 0) {
    const main = parseMainClass(sys.stdout.toString("utf-8"));
    if (main) return main;
  }
  const entry = readJarEntry(readFileSync4(jarPath), "META-INF/MANIFEST.MF");
  return entry ? parseMainClass(entry.toString("utf-8")) : null;
}
function parseMainClass(manifest) {
  const text = manifest.replace(/\r/g, "");
  for (const line of text.split("\n")) {
    const m = line.match(/^Main-Class:\s*(.+)$/);
    if (m) return m[1].trim();
  }
  return null;
}
function artifactFilePath(coords, ext) {
  const parts = coords.split(":");
  const group = parts[0].replace(/\./g, "/");
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3];
  const file = classifier ? `${artifact}-${version}-${classifier}.${ext}` : `${artifact}-${version}.${ext}`;
  return join4(librariesDir(), group, artifact, version, file);
}
function parseArtifactRef(token) {
  const m = token.match(/^\[([^\]]+)\]$/);
  if (!m) return null;
  const inner = m[1];
  const at = inner.lastIndexOf("@");
  if (at >= 0) return { coords: inner.slice(0, at), ext: inner.slice(at + 1) };
  return { coords: inner, ext: DEFAULT_EXT };
}
async function ensureArtifact(coords, ext) {
  const filePath = artifactFilePath(coords, ext);
  const dir = dirname(filePath);
  if (!existsSync4(dir)) mkdirSync(dir, { recursive: true });
  if (existsSync4(filePath)) return filePath;
  const mvnRel = artifactFilePath(coords, ext).slice(librariesDir().length + 1).split("\\").join("/");
  const candidates = [
    `${DOWNLOAD.forgeMavenUrl}${mvnRel}`,
    `${DOWNLOAD.mavenLibrariesUrl}${mvnRel}`,
    `${DOWNLOAD.neoforgeMavenUrl}${mvnRel}`,
    ...DOWNLOAD.fallbackRepoUrls.map((u) => `${u}${mvnRel}`)
  ];
  for (const url of candidates) {
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const resp = await fetchWithFallback(url);
        if (!resp.ok) break;
        const ab = Buffer.from(await resp.arrayBuffer());
        writeFileSync(filePath, ab);
        return filePath;
      } catch {
        await new Promise((r) => setTimeout(r, 1500 * (attempt + 1)));
      }
    }
  }
  return filePath;
}
async function ensureArtifactWithDeps(coords, extra) {
  let pomPath;
  try {
    pomPath = await ensureArtifact(coords, "pom");
  } catch {
    return;
  }
  if (!existsSync4(pomPath)) return;
  let pomText;
  try {
    pomText = readFileSync4(pomPath, "utf-8");
  } catch {
    return;
  }
  const depRe = /<dependency>([\s\S]*?)<\/dependency>/g;
  let m;
  while ((m = depRe.exec(pomText)) !== null) {
    const block = m[1];
    if (/<scope>(test|system)<\/scope>/.test(block)) continue;
    const g = block.match(/<groupId>([^<]+)<\/groupId>/);
    const a = block.match(/<artifactId>([^<]+)<\/artifactId>/);
    const v = block.match(/<version>([^<]+)<\/version>/);
    if (!g || !a || !v) continue;
    const depCoords = `${g[1]}:${a[1]}:${v[1]}`;
    try {
      const depJar = await ensureArtifact(depCoords, DEFAULT_EXT);
      if (existsSync4(depJar)) extra.push(depJar);
    } catch {
    }
  }
}
function extractInstallerResource(installerJar, entryPath) {
  const entry = entryPath.replace(/^\//, "");
  const outDir = join4(dirname(installerJar), "processor-data");
  if (!existsSync4(outDir)) mkdirSync(outDir, { recursive: true });
  const outPath = join4(outDir, entry.replace(/\//g, "__"));
  if (existsSync4(outPath)) return outPath;
  const sys = spawnSync("unzip", ["-p", installerJar, entry], { maxBuffer: 64 * 1024 * 1024 });
  if (sys.status === 0 && sys.stdout && sys.stdout.length > 0) {
    writeFileSync(outPath, sys.stdout);
    return outPath;
  }
  const buf = readFileSync4(installerJar);
  const data = readJarEntry(buf, entry);
  if (!data) throw new Error(`Installer missing entry: ${entry}`);
  writeFileSync(outPath, data);
  return outPath;
}
async function resolveToken(token, ctx, data) {
  const sym = token.match(/^\{([^}]+)\}$/);
  if (sym) {
    const key = sym[1];
    if (key in data) return data[key];
    switch (key) {
      case "SIDE":
        return ctx.side;
      case "MINECRAFT_JAR":
        return ctx.minecraftJar;
      case "INSTALLER":
        return ctx.installerJar;
      case "LIBRARY_DIR":
        return librariesDir();
      default:
        return token;
    }
  }
  if (token.startsWith("'") && token.endsWith("'")) return token.slice(1, -1);
  const ref = parseArtifactRef(token);
  if (ref) return await ensureArtifact(ref.coords, ref.ext);
  if (token.startsWith("/")) return extractInstallerResource(ctx.installerJar, token);
  return token;
}
async function runForgeProcessors(profile, ctx, onProgress) {
  const processors = profile.processors ?? [];
  if (processors.length === 0) return;
  const data = {};
  for (const [key, sides] of Object.entries(profile.data ?? {})) {
    const raw = sides[ctx.side] ?? sides.client ?? sides.server;
    if (raw != null) data[key] = await resolveToken(raw, ctx, data);
  }
  for (let i = 0; i < processors.length; i++) {
    const proc = processors[i];
    if (proc.sides && !proc.sides.includes(ctx.side)) continue;
    const jarPath = await ensureArtifact(proc.jar, DEFAULT_EXT);
    const cpEntries = [jarPath];
    const transitiveExtra = [];
    for (const cp of proc.classpath ?? []) {
      const bareCoords = cp.split("@")[0];
      const jar = await ensureArtifact(bareCoords, DEFAULT_EXT);
      cpEntries.push(jar);
      if (existsSync4(jar)) await ensureArtifactWithDeps(bareCoords, transitiveExtra);
    }
    cpEntries.push(...new Set(transitiveExtra));
    const mainClass = readManifestMainClass(jarPath);
    if (!mainClass) {
      onProgress?.(`  Warning: processor ${proc.jar} has no Main-Class, skipping`);
      continue;
    }
    const resolvedArgs = [];
    for (const arg of proc.args ?? []) resolvedArgs.push(await resolveToken(arg, ctx, data));
    const classpath = cpEntries.join(process.platform === "win32" ? ";" : ":");
    onProgress?.(`  [processor ${i + 1}/${processors.length}] ${mainClass}`);
    const result = spawnSync(ctx.javaExecutable, ["-cp", classpath, mainClass, ...resolvedArgs], {
      stdio: ["ignore", "pipe", "pipe"],
      maxBuffer: 16 * 1024 * 1024
    });
    if (result.status !== 0) {
      const stderr = result.stderr?.toString("utf-8").trim().slice(-500);
      throw new Error(`Forge processor ${proc.jar} failed (exit ${result.status}): ${stderr}`);
    }
  }
  onProgress?.(`  Processors complete (${processors.length} steps)`);
}
function readJarEntry(buf, entryName) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf[i] === 80 && buf[i + 1] === 75 && buf[i + 2] === 5 && buf[i + 3] === 6) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return null;
  const cdOffset = buf.readUInt32LE(eocd + 16);
  const cdSize = buf.readUInt32LE(eocd + 12);
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
    if (name !== entryName) continue;
    const cSize = buf.readUInt32LE(pos + 20);
    const lNameLen = buf.readUInt16LE(localOffset + 26);
    const lExtraLen = buf.readUInt16LE(localOffset + 28);
    const lMethod = buf.readUInt16LE(localOffset + 8);
    const dataStart = localOffset + 30 + lNameLen + lExtraLen;
    const compressed = buf.subarray(dataStart, dataStart + cSize);
    return lMethod === 0 ? compressed : inflateRawSync(compressed);
  }
  return null;
}

// src/mc/download.ts
async function fetchVersionManifest() {
  const resp = await fetchWithFallback(DOWNLOAD.versionManifestUrl);
  if (!resp.ok) throw new Error(`Failed to fetch version manifest: ${resp.status}`);
  return await resp.json();
}
async function fetchVersionJson(url) {
  const resp = await fetchWithFallback(url);
  if (!resp.ok) throw new Error(`Failed to fetch version JSON: ${resp.status}`);
  return await resp.json();
}
async function sha1File(filePath) {
  const data = readFileSync5(filePath);
  return createHash("sha1").update(data).digest("hex");
}
async function downloadFile(url, path, expectedSha1) {
  if (existsSync5(path)) {
    if (expectedSha1) {
      const existing = await sha1File(path);
      if (existing.toLowerCase() === expectedSha1.toLowerCase()) return;
    } else {
      return;
    }
  }
  const dir = dirname2(path);
  if (!existsSync5(dir)) mkdirSync2(dir, { recursive: true });
  await downloadWithNativeFallback(url, path);
  if (expectedSha1) {
    const { readFile } = await import("fs/promises");
    const buf = await readFile(path);
    const actual = createHash("sha1").update(buf).digest("hex");
    if (actual.toLowerCase() !== expectedSha1.toLowerCase()) {
      throw new Error(`SHA-1 mismatch for ${path}: expected ${expectedSha1}, got ${actual}`);
    }
  }
}
async function downloadVersion(versionJson, onProgress) {
  const versionId = versionJson.id;
  const vDir = join5(versionsDir(), versionId);
  if (!existsSync5(vDir)) mkdirSync2(vDir, { recursive: true });
  const jsonPath = join5(vDir, `${versionId}.json`);
  writeFileSync2(jsonPath, JSON.stringify(versionJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);
  if (versionJson.downloads?.client?.url) {
    const jarPath = join5(vDir, `${versionId}.jar`);
    if (!existsSync5(jarPath)) {
      onProgress?.(`Downloading client JAR for ${versionId}...`);
      await downloadFile(versionJson.downloads.client.url, jarPath, versionJson.downloads.client.sha1);
      onProgress?.(`Client JAR downloaded.`);
    }
  }
  if (versionJson.assetIndex?.url) {
    const idxDir = join5(assetsDir(), PATHS.assetIndexesDirName);
    if (!existsSync5(idxDir)) mkdirSync2(idxDir, { recursive: true });
    const idxPath = join5(idxDir, `${versionJson.assetIndex.id}.json`);
    if (!existsSync5(idxPath)) {
      await downloadFile(versionJson.assetIndex.url, idxPath, versionJson.assetIndex.sha1);
    }
    const indexData = JSON.parse(readFileSync5(idxPath, "utf-8"));
    const objects = indexData.objects;
    if (objects) {
      const entries = Object.entries(objects);
      onProgress?.(`Downloading ${entries.length} assets...`);
      const BATCH = DOWNLOAD.assetBatchSize;
      for (let i = 0; i < entries.length; i += BATCH) {
        const batch = entries.slice(i, i + BATCH);
        await Promise.all(batch.map(async ([, obj]) => {
          const hash = obj.hash;
          const prefix = hash.slice(0, 2);
          const assetUrl = `${DOWNLOAD.assetBaseUrl}${prefix}/${hash}`;
          const objDir = join5(assetsDir(), PATHS.assetObjectsDirName, prefix);
          if (!existsSync5(objDir)) mkdirSync2(objDir, { recursive: true });
          const assetPath = join5(objDir, hash);
          if (!existsSync5(assetPath)) {
            try {
              await downloadFile(assetUrl, assetPath, hash);
            } catch {
            }
          }
        }));
        onProgress?.(`Assets: ${Math.min(i + BATCH, entries.length)}/${entries.length}`);
      }
    }
  }
  onProgress?.(`Downloading libraries for ${versionId}...`);
  for (const lib of versionJson.libraries) {
    if (lib.downloads?.artifact?.url) {
      const libPath = join5(librariesDir(), lib.downloads.artifact.path);
      if (!existsSync5(libPath)) {
        try {
          await downloadFile(lib.downloads.artifact.url, libPath, lib.downloads.artifact.sha1);
        } catch {
          const mvnPath = libraryMavenPath(lib.name);
          let ok = false;
          for (const repo of DOWNLOAD.fallbackRepoUrls) {
            try {
              await downloadFile(`${repo}${mvnPath}`, libPath);
              ok = true;
              break;
            } catch {
            }
          }
          if (!ok) onProgress?.(`  Warning: could not download ${lib.name}`);
        }
      }
    } else if (lib.name) {
      const mvnPath = libraryMavenPath(lib.name);
      const url = `${DOWNLOAD.mavenLibrariesUrl}${mvnPath}`;
      const filePath = join5(librariesDir(), mvnPath);
      if (!existsSync5(filePath)) {
        let ok = false;
        try {
          await downloadFile(url, filePath);
          ok = true;
        } catch {
        }
        if (!ok) {
          for (const repo of DOWNLOAD.fallbackRepoUrls) {
            try {
              await downloadFile(`${repo}${mvnPath}`, filePath);
              ok = true;
              break;
            } catch {
            }
          }
        }
        if (!ok) onProgress?.(`  Warning: could not download ${lib.name}`);
      }
    }
    await downloadNativeClassifiers(lib, onProgress);
  }
  onProgress?.(`Version ${versionId} download complete`);
}
function listInstalledVersions() {
  const vDir = versionsDir();
  if (!existsSync5(vDir)) return [];
  const installed = [];
  try {
    for (const entry of readdirSync2(vDir, { withFileTypes: true })) {
      if (entry.isDirectory() && entry.name !== PATHS.tmpSuffix) {
        const jsonPath = join5(vDir, entry.name, `${entry.name}.json`);
        if (existsSync5(jsonPath)) installed.push(entry.name);
      }
    }
  } catch {
  }
  return installed.sort();
}
async function downloadLoaderVersion(mcVersion, loader, loaderVersion, onProgress) {
  switch (loader) {
    case "forge":
      await downloadForgeInstaller(loaderVersion, onProgress);
      break;
    case "fabric":
      await downloadFabricLoader(mcVersion, loaderVersion, onProgress);
      break;
    case "neoforge":
      await downloadNeoforgeInstaller(loaderVersion, onProgress);
      break;
  }
}
async function downloadForgeInstaller(forgeVersion, onProgress) {
  const mavenUrl = `${DOWNLOAD.forgeMavenUrl}net/minecraftforge/forge/${forgeVersion}/forge-${forgeVersion}-installer.jar`;
  const tmpDir = join5(versionsDir(), PATHS.tmpSuffix);
  if (!existsSync5(tmpDir)) mkdirSync2(tmpDir, { recursive: true });
  const installerPath = join5(tmpDir, `forge-${forgeVersion}-installer.jar`);
  onProgress?.(`Downloading Forge installer for ${forgeVersion}...`);
  await downloadFile(mavenUrl, installerPath);
  onProgress?.(`Extracting install profile...`);
  const profile = await extractJarJson(installerPath, "install_profile.json");
  if (!profile) throw new Error("Invalid Forge installer: missing install_profile.json");
  let versionJson;
  let versionId;
  if (profile.versionInfo && typeof profile.versionInfo === "object" && "id" in profile.versionInfo) {
    versionJson = profile.versionInfo;
    versionId = versionJson.id;
    onProgress?.(`Found embedded versionInfo (legacy format)`);
  } else {
    const jsonPath = profile.json || profile.install?.json || "/version.json";
    const entryName = jsonPath.replace(/^\//, "");
    onProgress?.(`Extracting version JSON from ${entryName} (modern format)...`);
    const vj = await extractJarJson(installerPath, entryName);
    if (!vj || !vj.id) throw new Error(`Invalid Forge installer: missing ${entryName}`);
    versionJson = vj;
    versionId = vj.id;
  }
  const vDir = join5(versionsDir(), versionId);
  if (!existsSync5(vDir)) mkdirSync2(vDir, { recursive: true });
  const jsonFilePath = join5(vDir, `${versionId}.json`);
  writeFileSync2(jsonFilePath, JSON.stringify(versionJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);
  const universalFileName = profile.install?.filePath ?? `forge-${forgeVersion}-universal.jar`;
  const universalUrl = `${DOWNLOAD.forgeMavenUrl}net/minecraftforge/forge/${forgeVersion}/${universalFileName}`;
  const forgeLibName = `net.minecraftforge:forge:${forgeVersion}`;
  const forgeLibPath = join5(librariesDir(), libraryMavenPath(forgeLibName));
  const forgeLibDir = dirname2(forgeLibPath);
  if (!existsSync5(forgeLibDir)) mkdirSync2(forgeLibDir, { recursive: true });
  if (!existsSync5(forgeLibPath)) {
    onProgress?.(`Downloading Forge universal JAR...`);
    await downloadFile(universalUrl, forgeLibPath);
  }
  const allLibs = [...versionJson.libraries ?? []];
  if (profile.libraries) allLibs.push(...profile.libraries);
  await downloadLibraries(allLibs, onProgress);
  let processorsOk = false;
  let replayErr = "";
  try {
    await runForgeInstallerProcessors(profile, installerPath, forgeVersion, "client", onProgress);
    processorsOk = true;
  } catch (err) {
    replayErr = (err?.message ?? err).toString();
    onProgress?.(`  Headless processor replay failed: ${replayErr.slice(0, 120)}`);
  }
  const wantsOfficial = !processorsOk && /binarypatcher|checksum|Patch expected/i.test(replayErr);
  if (wantsOfficial) {
    onProgress?.(`Falling back to official installer jar (binarypatcher mismatch)...`);
    const usedOfficial = await tryOfficialInstaller(installerPath, versionId, "client", mcDir(), onProgress);
    if (!usedOfficial) {
      throw new Error("Both headless processor replay and official installer failed");
    }
  } else if (!processorsOk) {
    throw new Error(replayErr || "Forge processor replay failed");
  }
  onProgress?.(`Forge ${versionId} install complete`);
}
async function tryOfficialInstaller(installerPath, versionId, side, targetMcDir, onProgress) {
  if (!existsSync5(installerPath)) return false;
  const javaExe = processorJavaExecutable();
  const flag = side === "client" ? "--installClient" : "--installServer";
  const versionLibBase = join5(librariesDir(), "net", "minecraftforge", "forge");
  const expectedClientJar = join5(
    versionLibBase,
    versionId.replace(/^forge-/, "").replace("-forge", "-forge"),
    `forge-${versionId.split("-").slice(-2).join("-") || versionId}-client.jar`
  );
  onProgress?.(`Running official installer (${side})...`);
  const r = spawnSync2(javaExe, ["-jar", installerPath, flag, targetMcDir], {
    stdio: ["ignore", "pipe", "pipe"],
    maxBuffer: 32 * 1024 * 1024,
    timeout: 6e5
  });
  if (r.status !== 0) {
    const tail = (r.stderr?.toString("utf-8") || r.stdout?.toString("utf-8") || "").trim().slice(-300);
    onProgress?.(`  Official installer exited ${r.status}: ${tail}`);
    return false;
  }
  const out = (r.stdout?.toString("utf-8") || "") + (r.stderr?.toString("utf-8") || "");
  const ok = /Successfully installed|already installed/i.test(out);
  if (!ok) {
    onProgress?.(`  Official installer did not report success; falling back`);
    return false;
  }
  onProgress?.(`  Official installer succeeded`);
  return true;
}
function processorJavaExecutable() {
  for (const v of [17, 21, 8, 16]) {
    const exe = findJavaForVersion(v);
    if (exe) return exe;
  }
  const all = detectJavas();
  if (all.length > 0) return `${all[0].path}/bin/java`;
  return "java";
}
async function runForgeInstallerProcessors(profile, installerPath, loaderVersion, side, onProgress) {
  if (!profile.processors || profile.processors.length === 0) return;
  const mcVersion = profile.minecraft ?? profile.install?.minecraft ?? loaderVersion.split("-")[0];
  const vanillaJar = join5(versionsDir(), mcVersion, `${mcVersion}.jar`);
  if (side === "client" && !existsSync5(vanillaJar)) {
    onProgress?.(`  Skipping processors: vanilla client jar not found at ${vanillaJar}`);
    return;
  }
  onProgress?.(`Running ${profile.processors.length} installer processors (${side})...`);
  await runForgeProcessors(
    profile,
    {
      installerJar: installerPath,
      side,
      minecraftJar: vanillaJar,
      javaExecutable: processorJavaExecutable()
    },
    onProgress
  );
}
async function downloadFabricLoader(mcVersion, loaderVersion, onProgress) {
  const profileUrl = `${DOWNLOAD.fabricMetaUrl}/${mcVersion}/${loaderVersion}/profile/json`;
  onProgress?.(`Fetching Fabric profile for ${mcVersion} loader ${loaderVersion}...`);
  const resp = await fetchWithFallback(profileUrl);
  if (!resp.ok) throw new Error(`Failed to fetch Fabric profile: HTTP ${resp.status}`);
  const profileJson = await resp.json();
  const versionId = profileJson.id;
  const vDir = join5(versionsDir(), versionId);
  if (!existsSync5(vDir)) mkdirSync2(vDir, { recursive: true });
  const jsonPath = join5(vDir, `${versionId}.json`);
  if (!existsSync5(jsonPath)) {
    writeFileSync2(jsonPath, JSON.stringify(profileJson, null, 2), "utf-8");
    onProgress?.(`Saved version JSON for ${versionId}`);
  }
  await downloadLibraries(profileJson.libraries, onProgress);
  onProgress?.(`Fabric ${versionId} install complete`);
}
async function downloadNeoforgeInstaller(neoforgeVersion, onProgress) {
  const mavenUrl = `${DOWNLOAD.neoforgeMavenUrl}net/neoforged/neoforge/${neoforgeVersion}/neoforge-${neoforgeVersion}-installer.jar`;
  const tmpDir = join5(versionsDir(), PATHS.tmpSuffix);
  if (!existsSync5(tmpDir)) mkdirSync2(tmpDir, { recursive: true });
  const installerPath = join5(tmpDir, `neoforge-${neoforgeVersion}-installer.jar`);
  onProgress?.(`Downloading NeoForge installer for ${neoforgeVersion}...`);
  await downloadFile(mavenUrl, installerPath);
  onProgress?.(`Extracting install profile...`);
  const profile = await extractJarJson(installerPath, "install_profile.json");
  if (!profile) throw new Error("Invalid NeoForge installer: missing install_profile.json");
  let versionJson;
  let versionId;
  if (profile.versionInfo && typeof profile.versionInfo === "object" && "id" in profile.versionInfo) {
    versionJson = profile.versionInfo;
    versionId = versionJson.id;
    onProgress?.(`Found embedded versionInfo (legacy format)`);
  } else {
    const jsonPath = profile.json || profile.install?.json || "/version.json";
    const entryName = jsonPath.replace(/^\//, "");
    onProgress?.(`Extracting version JSON from ${entryName} (modern format)...`);
    const vj = await extractJarJson(installerPath, entryName);
    if (!vj || !vj.id) throw new Error(`Invalid NeoForge installer: missing ${entryName}`);
    versionJson = vj;
    versionId = vj.id;
  }
  const vDir = join5(versionsDir(), versionId);
  if (!existsSync5(vDir)) mkdirSync2(vDir, { recursive: true });
  const jsonFilePath = join5(vDir, `${versionId}.json`);
  writeFileSync2(jsonFilePath, JSON.stringify(versionJson, null, 2), "utf-8");
  onProgress?.(`Saved version JSON for ${versionId}`);
  const universalFileName = profile.install?.filePath ?? `neoforge-${neoforgeVersion}-universal.jar`;
  const universalUrl = `${DOWNLOAD.neoforgeMavenUrl}net/neoforged/neoforge/${neoforgeVersion}/${universalFileName}`;
  const nfLibName = `net.neoforged:neoforge:${neoforgeVersion}`;
  const nfLibPath = join5(librariesDir(), libraryMavenPath(nfLibName));
  const nfLibDir = dirname2(nfLibPath);
  if (!existsSync5(nfLibDir)) mkdirSync2(nfLibDir, { recursive: true });
  if (!existsSync5(nfLibPath)) {
    onProgress?.(`Downloading NeoForge JAR...`);
    await downloadFile(universalUrl, nfLibPath);
  }
  const allLibs = [...versionJson.libraries ?? []];
  if (profile.libraries) allLibs.push(...profile.libraries);
  await downloadLibraries(allLibs, onProgress);
  let processorsOk = false;
  let replayErr = "";
  try {
    await runForgeInstallerProcessors(profile, installerPath, neoforgeVersion, "client", onProgress);
    processorsOk = true;
  } catch (err) {
    replayErr = (err?.message ?? err).toString();
    onProgress?.(`  Headless processor replay failed: ${replayErr.slice(0, 120)}`);
  }
  const wantsOfficial = !processorsOk && /binarypatcher|checksum|Patch expected|invalid file signature/i.test(replayErr);
  if (wantsOfficial) {
    onProgress?.(`Falling back to official installer jar (patch mismatch)...`);
    const usedOfficial = await tryOfficialInstaller(installerPath, versionId, "client", mcDir(), onProgress);
    if (!usedOfficial) {
      throw new Error("Both headless processor replay and official installer failed");
    }
  } else if (!processorsOk) {
    throw new Error(replayErr || "NeoForge processor replay failed");
  }
  onProgress?.(`NeoForge ${versionId} install complete`);
}
async function downloadNativeClassifiers(lib, onProgress) {
  if (!lib.natives || !lib.downloads?.classifiers) return;
  for (const classifierName of Object.values(lib.natives)) {
    const cl = lib.downloads.classifiers[classifierName];
    if (!cl?.path || !cl.url) continue;
    const clPath = join5(librariesDir(), cl.path);
    if (existsSync5(clPath)) continue;
    try {
      await downloadFile(cl.url, clPath, cl.sha1);
    } catch {
      onProgress?.(`  Warning: could not download native ${lib.name}:${classifierName}`);
    }
  }
}
async function downloadLibraries(libraries, onProgress) {
  for (const lib of libraries) {
    if (lib.downloads?.artifact?.url) {
      const libPath = join5(librariesDir(), lib.downloads.artifact.path);
      try {
        await downloadFile(lib.downloads.artifact.url, libPath, lib.downloads.artifact.sha1);
      } catch {
        onProgress?.(`  Warning: could not download ${lib.name}`);
      }
    } else if (lib.name) {
      const repoUrl = lib.url;
      const mvnPath = libraryMavenPath(lib.name);
      const filePath = join5(librariesDir(), mvnPath);
      if (!existsSync5(filePath)) {
        const baseUrls = repoUrl ? [`${repoUrl.replace(/\/$/, "")}/${mvnPath}`] : [];
        baseUrls.push(...DOWNLOAD.fallbackRepoUrls.map((u) => `${u}${mvnPath}`));
        let downloaded = false;
        for (const url of baseUrls) {
          try {
            await downloadFile(url, filePath);
            downloaded = true;
            break;
          } catch {
          }
        }
        if (!downloaded) {
          onProgress?.(`  Warning: could not download ${lib.name}`);
        }
      }
    }
    await downloadNativeClassifiers(lib, onProgress);
  }
}
async function extractJarJson(jarPath, entryName) {
  const { inflateRawSync: inflateRawSync2 } = await import("zlib");
  const data = readFileSync5(jarPath);
  let eocdOffset = -1;
  for (let i = data.length - 22; i >= 0; i--) {
    if (data[i] === 80 && data[i + 1] === 75 && data[i + 2] === 5 && data[i + 3] === 6) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset < 0) return null;
  const cdOffset = data.readUInt32LE(eocdOffset + 16);
  const cdEntries = data.readUInt16LE(eocdOffset + 10);
  let offset = cdOffset;
  for (let i = 0; i < cdEntries; i++) {
    if (data.readUInt32LE(offset) !== 33639248) break;
    const fnameLen = data.readUInt16LE(offset + 28);
    const extraLen = data.readUInt16LE(offset + 30);
    const commentLen = data.readUInt16LE(offset + 32);
    const localHeaderOffset = data.readUInt32LE(offset + 42);
    const fname = data.subarray(offset + 46, offset + 46 + fnameLen).toString("utf-8");
    if (fname === entryName) {
      const compMethod = data.readUInt16LE(localHeaderOffset + 8);
      const lhFnameLen = data.readUInt16LE(localHeaderOffset + 26);
      const lhExtraLen = data.readUInt16LE(localHeaderOffset + 28);
      const compSize = data.readUInt32LE(offset + 20);
      const contentStart = localHeaderOffset + 30 + lhFnameLen + lhExtraLen;
      const compressed = data.subarray(contentStart, contentStart + compSize);
      const jsonBytes = compMethod === 0 ? compressed : inflateRawSync2(compressed);
      return JSON.parse(jsonBytes.toString("utf-8"));
    }
    offset = offset + 46 + fnameLen + extraLen + commentLen;
  }
  return null;
}
async function ensureVersionInstalled(version, loader, onProgress) {
  const data = loadVersionsData();
  const vi = getVersion(data, version);
  if (!vi) {
    try {
      loadVersionMerged(version);
      return version;
    } catch {
      throw new Error(
        `Unknown version: "${version}". Use list_supported_versions to see available versions.`
      );
    }
  }
  const mcVersion = vi.mc_version;
  let versionId = getVersionForLoader(data, version, loader) ?? vi.version_id;
  try {
    loadVersionMerged(versionId);
    return versionId;
  } catch {
  }
  onProgress?.(`Auto-installing ${mcVersion} (${loader})...`);
  const baseJsonPath = join5(versionsDir(), mcVersion, `${mcVersion}.json`);
  if (!existsSync5(baseJsonPath)) {
    onProgress?.(`Downloading base MC ${mcVersion}...`);
    const manifest = await fetchVersionManifest();
    const mv = manifest.versions.find((v) => v.id === mcVersion);
    if (!mv) throw new Error(`Base MC version ${mcVersion} not found in manifest.`);
    const baseVj = await fetchVersionJson(mv.url);
    await downloadVersion(baseVj, onProgress);
  }
  let loaderVersion;
  if (loader === "forge" && vi.forge) loaderVersion = vi.forge;
  else if (loader === "neoforge" && vi.neoforge) loaderVersion = vi.neoforge;
  else if (loader === "fabric") loaderVersion = vi.fabric_loader ?? DEFAULT_FABRIC_LOADER_VERSION;
  if (loaderVersion) {
    onProgress?.(`Installing ${loader} ${loaderVersion}...`);
    await downloadLoaderVersion(mcVersion, loader, loaderVersion, onProgress);
  }
  const resolved = getVersionForLoader(data, version, loader);
  if (resolved) versionId = resolved;
  onProgress?.(`Installation complete: ${versionId}`);
  return versionId;
}

export {
  loadVersionsData,
  DEFAULT_FABRIC_LOADER_VERSION,
  loaders,
  hasFabric,
  getVersions,
  getVersion,
  getVersionById,
  getVersionForLoader,
  getFgEra,
  loadVersion,
  loadVersionMerged,
  collectAllArgs,
  shouldApply,
  libraryPath,
  resolveClasspath,
  setupGlobalProxy,
  fetchWithFallback,
  downloadWithNativeFallback,
  gradleProxyEnv,
  javaProxyArgs,
  fetchVersionManifest,
  fetchVersionJson,
  downloadFile,
  downloadVersion,
  listInstalledVersions,
  downloadLoaderVersion,
  downloadForgeInstaller,
  downloadFabricLoader,
  downloadNeoforgeInstaller,
  ensureVersionInstalled
};
