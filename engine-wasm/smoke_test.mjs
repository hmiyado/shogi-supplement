#!/usr/bin/env node
// Why Node向けに再リンクした成果物へ、ブラウザ版と互換の「callMain(argv)にUSIコマンド列を
// 渡す」形で疎通する。ブラウザ向け成果物(-sENVIRONMENT=worker)はNodeのファイルAPIに
// 依存する評価関数を扱えないため、このスクリプトでは直接実行しない。
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { pathToFileURL } from "node:url";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, "out-browser", "node-smoke");
const EVAL_NN = path.join(__dirname, "..", "app", "androidApp", "src", "main", "assets", "eval", "nn.bin");

const GO_NODES = 400_000;

const VARIANTS = ["nosimd", "simd"];

function buildCommands() {
	const lines = ["usi"];
	const setoptions = [
		["EvalDir", path.dirname(EVAL_NN)],
		["FV_SCALE", "20"],
		["Threads", "1"],
		["USI_Hash", "128"],
		["MultiPV", "2"],
		["NetworkDelay", "0"],
		["NetworkDelay2", "0"],
		["USI_OwnBook", "false"],
	];
	for (const [name, value] of setoptions) {
		lines.push(`setoption name ${name} value ${value}`);
	}
	lines.push("isready");
	lines.push("usinewgame");
	lines.push("position startpos");
	lines.push(`go nodes ${GO_NODES}`);
	lines.push("quit");
	return lines.join("\n") + "\n";
}

async function runVariant(variant) {
	const jsPath = path.join(OUT_DIR, `yaneuraou-${variant}.js`);
	try {
		readFileSync(jsPath);
	} catch {
		throw new Error(`ビルド成果物が見つかりません: ${jsPath} (先にbuild_wasm_browser.shを実行してください)`);
	}

	const argv = [];
	for (const command of buildCommands().trimEnd().split("\n")) {
		argv.push(command, ",");
	}
	const runner = `
import { pathToFileURL } from "node:url";
const factory = (await import(${JSON.stringify(pathToFileURL(jsPath).href)})).default;
const module = await factory({
  locateFile: (file) => ${JSON.stringify(OUT_DIR)} + "/" + file,
});
module.callMain(${JSON.stringify(argv)});
`;
	// Emscripten 4.xのNode glueはTTY出力をプロセスのconsoleへ直結するため、
	// 別プロセスのstdout/stderrを捕捉して判定する。
	const result = spawnSync(process.execPath, ["--input-type=module", "-e", runner], {
		encoding: "utf8",
		maxBuffer: 10 * 1024 * 1024,
		timeout: 120_000,
	});
	return { stdout: result.stdout, stderr: result.stderr, code: result.status };
}

function check(condition, message, results) {
	results.push({ ok: !!condition, message });
}

async function main() {
	let overallOk = true;

	try {
		readFileSync(EVAL_NN);
	} catch {
		console.error(`評価関数が見つかりません: ${EVAL_NN}`);
		process.exit(1);
	}

	for (const variant of VARIANTS) {
		console.log(`\n=== ${variant} ===`);
		const startedAt = Date.now();
		let result;
		try {
			result = await runVariant(variant);
		} catch (err) {
			console.error(`[${variant}] 実行に失敗: ${err.message}`);
			overallOk = false;
			continue;
		}
		const elapsedMs = Date.now() - startedAt;

		const { stdout } = result;
		const results = [];

		check(/usiok/.test(stdout), "usi -> usiok", results);
		check(/readyok/.test(stdout), "isready -> readyok", results);
		check(/bestmove /.test(stdout), "go -> bestmove", results);

		for (const line of results) {
			console.log(`  ${line.ok ? "OK  " : "NG  "} ${line.message}`);
			if (!line.ok) overallOk = false;
		}

		const infoLines = stdout.split("\n").filter((l) => l.startsWith("info depth") && l.includes(" nps "));
		const last = infoLines[infoLines.length - 1];
		if (last) {
			const npsMatch = last.match(/\bnps (\d+)/);
			const nodesMatch = last.match(/\bnodes (\d+)/);
			const timeMatch = last.match(/\btime (\d+)/);
			console.log(
				`  探索終盤の実測: nodes=${nodesMatch?.[1] ?? "?"} nps=${npsMatch?.[1] ?? "?"} time=${timeMatch?.[1] ?? "?"}ms`
			);
		} else {
			console.log("  info行(nps)が見つかりませんでした");
			overallOk = false;
		}

		const bestmoveLine = stdout.split("\n").find((l) => l.startsWith("bestmove"));
		console.log(`  bestmove行: ${bestmoveLine ?? "(なし)"}`);
		console.log(`  プロセス全体の所要時間: ${elapsedMs}ms`);
	}

	console.log(`\n=== 結果: ${overallOk ? "全変種OK" : "失敗あり"} ===`);
	process.exit(overallOk ? 0 : 1);
}

main();
