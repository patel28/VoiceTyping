#!/usr/bin/env node
// Dependency-free local refinement server (Node built-ins only).
// Loads server/.env.local, defaults: model gpt-5.4, reasoning none, port 8787.
// Endpoints: GET /health, POST /v1/refine -> OpenAI POST https://api.openai.com/v1/responses
// Retries 429/5xx/network with increasing non-blocking delay (3 total attempts minimum).
// Request-size limit enforced. Never logs secrets or transcript text (lengths/status only).

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_MODEL = "gpt-5.4";
const DEFAULT_REASONING_EFFORT = "none";
const DEFAULT_PORT = 8787;
const MAX_BODY_BYTES = 32 * 1024;
const MAX_TEXT_CHARS = 4000;
const MAX_LOCALE_CHARS = 32;
const OPENAI_URL = "https://api.openai.com/v1/responses";
const OPENAI_TIMEOUT_MS = 20000;
const MAX_OUTPUT_TOKENS = 256;

function parseEnvText(text) {
  const out = {};
  for (const rawLine of text.split("\n")) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (key) out[key] = value;
  }
  return out;
}

function loadLocalEnv() {
  const file = path.join(__dirname, ".env.local");
  try {
    const text = fs.readFileSync(file, "utf8");
    return parseEnvText(text);
  } catch {
    return {};
  }
}

const fileEnv = loadLocalEnv();
function env(name, fallback) {
  if (process.env[name] !== undefined && process.env[name] !== "") return process.env[name];
  if (fileEnv[name] !== undefined && fileEnv[name] !== "") return fileEnv[name];
  return fallback;
}

const MODEL = env(
  "OPENAI_REFINEMENT_MODEL",
  env("OPENAI_MODEL", env("MODEL", DEFAULT_MODEL)),
);
const REASONING_EFFORT = env("OPENAI_REASONING_EFFORT", env("REASONING_EFFORT", DEFAULT_REASONING_EFFORT));
const PORT = Number.parseInt(env("PORT", String(DEFAULT_PORT)), 10) || DEFAULT_PORT;
const OPENAI_API_KEY = env("OPENAI_API_KEY", "");

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
  });
  res.end(body);
}

function readBoundedBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    let tooLarge = false;
    req.on("data", (chunk) => {
      if (tooLarge) return;
      total += chunk.length;
      if (total > MAX_BODY_BYTES) {
        tooLarge = true;
        // Stop accumulating; drain remaining data.
        req.resume();
        const err = new Error("body too large");
        err.code = "BODY_TOO_LARGE";
        reject(err);
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (tooLarge) return;
      resolve(Buffer.concat(chunks).toString("utf8"));
    });
    req.on("error", (err) => {
      err.code = err.code || "BODY_READ_ERROR";
      reject(err);
    });
  });
}

function extractOutputText(data) {
  if (!data || typeof data !== "object") return null;
  if (typeof data.output_text === "string" && data.output_text.trim()) {
    return data.output_text.trim();
  }
  const output = data.output;
  if (Array.isArray(output)) {
    const parts = [];
    for (const item of output) {
      const content = item && item.content;
      if (Array.isArray(content)) {
        for (const block of content) {
          if (block && block.type === "output_text" && typeof block.text === "string") {
            parts.push(block.text);
          }
        }
      }
      // Some shapes nest text directly.
      if (item && typeof item.text === "string" && item.type && String(item.type).includes("output")) {
        parts.push(item.text);
      }
    }
    const joined = parts.join("").trim();
    if (joined) return joined;
  }
  return null;
}

function isRetryableStatus(status) {
  return status === 429 || (status >= 500 && status <= 599);
}

async function callOpenAI(promptText, locale) {
  const headers = {
    "content-type": "application/json",
    authorization: `Bearer ${OPENAI_API_KEY}`,
  };
  const payload = {
    model: MODEL,
    store: false,
    reasoning: { effort: REASONING_EFFORT },
    max_output_tokens: MAX_OUTPUT_TOKENS,
    instructions:
      "Refine a speech-transcription for pasting. Fix casing and punctuation, remove obvious filler repetitions, keep the same language and meaning. Return only the refined text, no quotes or explanation.",
    input: `Locale: ${locale}\nTranscript: ${promptText}`,
  };
  // Initial attempt plus three retries with increasing non-blocking backoff.
  const delays = [400, 1000, 2000];
  const maxAttempts = 4;
  let lastStatus = 0;
  let lastError = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), OPENAI_TIMEOUT_MS);
    try {
      const res = await fetch(OPENAI_URL, {
        method: "POST",
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
      clearTimeout(timer);
      lastStatus = res.status;
      if (res.ok) {
        const data = await res.json().catch(() => null);
        const refined = extractOutputText(data);
        if (refined) return { ok: true, text: refined };
        lastError = new Error("empty upstream result");
        // Empty success payload is not retryable.
        break;
      }
      if (isRetryableStatus(res.status) && attempt < maxAttempts) {
        await res.arrayBuffer().catch(() => null);
        await sleep(delays[attempt - 1] ?? 1500);
        continue;
      }
      await res.arrayBuffer().catch(() => null);
      return { ok: false, status: res.status, error: lastError };
    } catch (err) {
      clearTimeout(timer);
      lastError = err;
      const isAbort = err && (err.name === "AbortError" || err.code === 20);
      // Network errors and timeouts are retryable.
      if (attempt < maxAttempts && !isAbort) {
        await sleep(delays[attempt - 1] ?? 1500);
        continue;
      }
      if (attempt < maxAttempts && isAbort) {
        await sleep(delays[attempt - 1] ?? 1500);
        continue;
      }
      return { ok: false, status: lastStatus, error: lastError, network: true };
    }
  }
  return { ok: false, status: lastStatus, error: lastError };
}

const server = http.createServer(async (req, res) => {
  const method = req.method || "GET";
  const urlPath = (req.url || "/").split("?")[0];

  if (method === "GET" && urlPath === "/health") {
    sendJson(res, 200, { ok: true, model: MODEL });
    console.log(`GET /health -> 200 modelLen=${MODEL.length}`);
    return;
  }

  if (method === "OPTIONS" && urlPath === "/v1/refine") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "POST, OPTIONS",
      "access-control-allow-headers": "content-type",
    });
    res.end();
    return;
  }

  if (method === "POST" && urlPath === "/v1/refine") {
    let rawBody = "";
    try {
      rawBody = await readBoundedBody(req);
    } catch (err) {
      if (err && err.code === "BODY_TOO_LARGE") {
        sendJson(res, 413, { error: "request too large" });
        console.log("POST /v1/refine -> 413 too large");
        return;
      }
      sendJson(res, 400, { error: "unreadable request" });
      console.log("POST /v1/refine -> 400 unreadable");
      return;
    }

    let parsed = null;
    try {
      parsed = JSON.parse(rawBody);
    } catch {
      sendJson(res, 400, { error: "invalid json" });
      console.log(`POST /v1/refine -> 400 invalid json bodyLen=${rawBody.length}`);
      return;
    }

    const text = typeof parsed.text === "string" ? parsed.text.trim() : "";
    let locale = typeof parsed.locale === "string" ? parsed.locale.trim() : "en-US";
    if (locale.length > MAX_LOCALE_CHARS) locale = locale.slice(0, MAX_LOCALE_CHARS);
    if (!locale) locale = "en-US";

    if (!text) {
      sendJson(res, 400, { error: "missing text" });
      console.log(`POST /v1/refine -> 400 missing text bodyLen=${rawBody.length}`);
      return;
    }
    if (text.length > MAX_TEXT_CHARS) {
      sendJson(res, 413, { error: "text too long" });
      console.log(`POST /v1/refine -> 413 textLen=${text.length}`);
      return;
    }
    if (!OPENAI_API_KEY) {
      sendJson(res, 500, { error: "refinement unavailable" });
      console.log(`POST /v1/refine -> 500 no key textLen=${text.length}`);
      return;
    }

    const inLen = text.length;
    const result = await callOpenAI(text, locale);
    if (result.ok) {
      const outLen = result.text.length;
      if (!result.text.trim() || outLen > MAX_TEXT_CHARS * 2) {
        sendJson(res, 502, { error: "refinement failed" });
        console.log(`POST /v1/refine -> 502 bad upstream outLen=${outLen} inLen=${inLen}`);
        return;
      }
      res.writeHead(200, {
        "content-type": "application/json; charset=utf-8",
        "access-control-allow-origin": "*",
      });
      res.end(JSON.stringify({ text: result.text.trim() }));
      console.log(`POST /v1/refine -> 200 inLen=${inLen} outLen=${outLen}`);
      return;
    }

    if (result.status === 429) {
      sendJson(res, 429, { error: "rate limited, try again" });
      console.log(`POST /v1/refine -> 429 inLen=${inLen}`);
      return;
    }
    if (result.status === 401 || result.status === 403) {
      sendJson(res, 500, { error: "refinement unavailable" });
      console.log(`POST /v1/refine -> 500 auth inLen=${inLen} upstream=${result.status}`);
      return;
    }
    sendJson(res, 502, { error: "refinement failed" });
    console.log(`POST /v1/refine -> 502 inLen=${inLen} upstream=${result.status || 0}`);
    return;
  }

  sendJson(res, 404, { error: "not found" });
  console.log(`${method} ${urlPath} -> 404`);
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`refine server listening on 127.0.0.1:${PORT} modelLen=${MODEL.length}`);
});
