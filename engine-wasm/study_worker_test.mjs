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

for (const worker of ["study-worker.js", "analysis-worker.js"]) {
  test(`${worker}: retains mate distance and verification independently`, () => {
    const code = readFileSync(new URL(`../docs/kento/${worker}`, import.meta.url), "utf8");
    const context = vm.createContext({ importScripts() {}, self: {} });
    vm.runInContext(code, context);
    for (const value of [3, -2, 0]) {
      context.line = `info depth 10 multipv 1 score mate ${value} nodes 100 pv G*5b`;
      const raw = JSON.parse(vm.runInContext("JSON.stringify(parseInfo(line))", context));
      assert.deepEqual(raw.score, { mate: value, verified: false });
      context.line = `info depth 10 multipv 1 score mate ${value} mateverified nodes 100 pv G*5b`;
      const verified = JSON.parse(vm.runInContext("JSON.stringify(parseInfo(line))", context));
      assert.deepEqual(verified.score, { mate: value, verified: true });
    }
  });

  test(`${worker}: normalizes whitespace so an empty PV stays empty`, () => {
    const code = readFileSync(new URL(`../docs/kento/${worker}`, import.meta.url), "utf8");
    const context = vm.createContext({ importScripts() {}, self: {} });
    vm.runInContext(code, context);
    const parse = (line) => {
      context.line = line;
      return JSON.parse(vm.runInContext("JSON.stringify(parseInfo(line))", context));
    };

    const empty = parse("info depth 0 multipv 1 score mate 0 mateverified nodes 0 pv ");
    assert.equal(empty.multipv, 1);
    assert.deepEqual(empty.score, { mate: 0, verified: true });
    assert.equal(empty.nodes, 0);
    assert.deepEqual(empty.pv, []);

    const spaced = parse("info depth 0   multipv 1   score mate 0 mateverified   nodes 0   pv   ");
    assert.equal(spaced.multipv, 1);
    assert.deepEqual(spaced.score, { mate: 0, verified: true });
    assert.equal(spaced.nodes, 0);
    assert.deepEqual(spaced.pv, []);

    const normal = parse("info depth 5 multipv 1 score cp 12 nodes 100 pv 7g7f   2g2f  8c8d");
    assert.deepEqual(normal.pv, ["7g7f", "2g2f", "8c8d"]);
  });
}
