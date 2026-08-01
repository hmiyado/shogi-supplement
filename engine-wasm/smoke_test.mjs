#!/usr/bin/env node
// Why 子プロセスとして実行する: ネイティブバイナリと同じ「標準入出力にUSIコマンドを
// 流し込む」形で疎通できるかどうかを確かめる。ブラウザ向け成果物
// (out-browser/yaneuraou-*.js、-sENVIRONMENT=worker)はこの方式では動かない
// (fetch/XMLHttpRequestに依存するため。ブラウザでの動作確認は実ブラウザでページを
// 開いて別途行う)。
import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.join(__dirname, "out-browser", "node-smoke");
const EVAL_NN = path.join(__dirname, "..", "app", "androidApp", "src", "main", "assets", "eval", "nn.bin");

// go nodes 400000 は数秒〜数十秒かかりうるので、余裕を持ったタイムアウトにする。
const TIMEOUT_MS = 120_000;
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

function runVariant(variant) {
	return new Promise((resolve, reject) => {
		const jsPath = path.join(OUT_DIR, `yaneuraou-${variant}.js`);
		try {
			readFileSync(jsPath);
		} catch {
			reject(
				new Error(`ビルド成果物が見つかりません: ${jsPath} (先にbuild_wasm_browser.shを実行してください)`)
			);
			return;
		}

		const child = spawn(process.execPath, [jsPath], { stdio: ["pipe", "pipe", "pipe"] });

		let stdout = "";
		let stderr = "";
		const timer = setTimeout(() => {
			child.kill("SIGKILL");
			reject(new Error(`[${variant}] タイムアウト(${TIMEOUT_MS}ms)。ここまでの出力:\n${stdout}\n--- stderr ---\n${stderr}`));
		}, TIMEOUT_MS);

		child.stdout.on("data", (d) => {
			stdout += d.toString();
		});
		child.stderr.on("data", (d) => {
			stderr += d.toString();
		});

		child.on("close", (code) => {
			clearTimeout(timer);
			resolve({ code, stdout, stderr });
		});
		child.on("error", (err) => {
			clearTimeout(timer);
			reject(err);
		});

		child.stdin.write(buildCommands());
		child.stdin.end();
	});
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
		console.log(`  プロセス全体の所要時間: ${elapsedMs}ms (exit code=${result.code})`);
	}

	console.log(`\n=== 結果: ${overallOk ? "全変種OK" : "失敗あり"} ===`);
	process.exit(overallOk ? 0 : 1);
}

main();
