import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import vm from "node:vm";

const source = readFileSync(new URL("../docs/kento/study-worker.js", import.meta.url), "utf8");

async function commandsFor(message) {
  const commands = [];
  const messages = [];
  const context = vm.createContext({
    importScripts() {},
    self: { postMessage: (message) => messages.push(message) },
    capture: (argv) => commands.push(...argv.filter((value) => value !== ",")),
  });
  vm.runInContext(source, context);
  vm.runInContext(`preparedModule = {
    Module: { callMain: capture },
    state: { results: [{ bestmove: "7g7f", pvs: [] }] }
  };`, context);
  await context.self.onmessage({ data: { type: "analyze", baseSfenArg: "startpos", movesJson: "[]", ...message } });
  return { commands, messages };
}

test("legacy and study requests retain their existing conditions", async () => {
  for (const [message, count] of [[{}, 2], [{ multiPv: 3 }, 3]]) {
    const { commands, messages } = await commandsFor(message);
    assert(commands.includes(`setoption name MultiPV value ${count}`));
    assert(commands.includes("go nodes 400000"));
    assert.equal(messages[0].type, "result");
  }
});

test("drill ignores study MultiPV and always resets before invariant search", async () => {
  for (const multiPv of [undefined, 1, 3, 20]) {
    const { commands, messages } = await commandsFor({ purpose: "drill", multiPv });
    for (const command of ["setoption name Threads value 1", "setoption name USI_Hash value 128",
      "setoption name FV_SCALE value 20", "setoption name MultiPV value 2", "go nodes 400000"]) {
      assert(commands.includes(command), command);
    }
    assert(commands.indexOf("usinewgame") < commands.indexOf("go nodes 400000"));
    assert.equal(messages[0].type, "result");
  }
});

test("unknown purpose fails before executing an engine", async () => {
  const { commands, messages } = await commandsFor({ purpose: "unknown" });
  assert.deepEqual(commands, []);
  assert.equal(messages[0].type, "error");
});

test("iOS host forwards drill purpose and keeps legacy calls valid", () => {
  const hostSource = readFileSync(new URL("../docs/kento/wasm-analysis-host.js", import.meta.url), "utf8");
  for (const purpose of [undefined, "drill"]) {
    const posted = [];
    const context = vm.createContext({ window: {}, capture: (message) => posted.push(message) });
    vm.runInContext(hostSource, context);
    vm.runInContext("nextWorker = { postMessage: capture }; nextWorkerReady = true;", context);
    context.window.__analyzePosition("request", "startpos", "[]", 2, purpose);
    assert.equal(posted.length, 1);
    assert.equal(posted[0].purpose, purpose);
    assert.equal(posted[0].multiPv, 2);
  }
});
