// nn.bin(評価関数、約61MB)をCache Storageへ明示的に保存する。GitHub PagesはCache-Control
// を長めに設定しておらず、大容量ファイルをブラウザの標準HTTPキャッシュがどれだけ保持するかは
// ブラウザの裁量任せで確実ではない。study-worker.jsはanalyzeのたびにWorkerを再生成する設計
// (使い切ったwasmインスタンスは再利用できないため)なので、キャッシュが効かないと解析のたびに
// 61MBをネットワークから取得し直すことになる。analysis-worker.js・study-worker.jsの両方から
// importScriptsで読み込んで使う共通ヘルパー。
(function () {
  const CACHE_NAME = "kento-engine-assets-v1";

  async function fetchCachedArrayBuffer(url) {
    const cache = await openCacheOrNull();
    if (!cache) {
      return await fetchFresh(url);
    }
    const hash = await fetchExpectedHash(url).catch(() => null);
    const preferredKey = hash ? `${url}?sha256=${hash}` : null;
    const hit = await lookupCache(cache, url, preferredKey);
    if (hit) {
      return await hit.arrayBuffer();
    }
    if (!(self.navigator && self.navigator.locks)) {
      return await fetchAndStore(cache, url, preferredKey, hash);
    }
    // コールド時、バッチ解析(analysis-worker.js)の2Worker並列などで複数Workerが
    // 同時にcache missを観測しうる。同じファイルへのロックを取り、先着側が取得・
    // 保存した結果を後続側がそのまま使えるようにして61MBの重複取得を避ける。
    return await self.navigator.locks.request(`kento-asset:${new URL(url).pathname}`, async () => {
      const hitAfterLock = await lookupCache(cache, url, preferredKey);
      if (hitAfterLock) {
        return await hitAfterLock.arrayBuffer();
      }
      return await fetchAndStore(cache, url, preferredKey, hash);
    });
  }

  async function lookupCache(cache, url, preferredKey) {
    if (preferredKey) {
      return await cache.match(preferredKey);
    }
    // マニフェストが取れずハッシュで最新性を確認できない場合でも、同名ファイルの
    // 既存キャッシュがあればそれを使う(無いよりまし。次回マニフェストが取れれば
    // 自然に最新のハッシュ付きキーへ差し替わる)。
    return await matchAnyByBasename(cache, url);
  }

  async function fetchAndStore(cache, url, preferredKey, hash) {
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status} (${url})`);
    }
    // Response.clone()はストリームをteeするため、キャッシュ書き込み側を読み切るまで
    // 元のarrayBuffer()側も含め約61MBの2重バッファがピークで発生しうる。一度だけ
    // 読み切り、その結果から作った新しいResponseをcache.putへ渡すことで避ける。
    const buf = await resp.arrayBuffer();
    if (hash && !(await digestMatches(buf, hash))) {
      // マニフェストの期待値と実際の取得内容が食い違う場合(CDNの伝播遅延等)、
      // 誤った内容を正しいハッシュのキャッシュとして残さないよう今回は書き込まない。
      return buf;
    }
    const key = preferredKey || url;
    await evictStaleEntries(cache, key).catch(() => {});
    await cache.put(key, new Response(buf)).catch((err) => {
      console.warn(`wasm-asset-cache: キャッシュへの保存に失敗しました (${key}): ${err}`);
    });
    return buf;
  }

  async function fetchFresh(url) {
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status} (${url})`);
    }
    return await resp.arrayBuffer();
  }

  // Cache StorageはWorkerを埋め込むコンテキストによっては使えない(例: セキュアでない
  // 扱いのカスタムURLスキーム上のWorker)。開けなければ素のfetchへ落とす。
  async function openCacheOrNull() {
    if (typeof caches === "undefined") {
      return null;
    }
    try {
      return await caches.open(CACHE_NAME);
    } catch {
      return null;
    }
  }

  // URLだけをキーにすると、バージョンディレクトリを変えずにファイル内容だけが
  // 更新された場合(例: 評価関数nn.binだけ差し替え、engine-wasm/VERSIONは据え置き)に
  // 古い内容を返し続けてしまう。配信側が生成するSHA-256マニフェスト(iOSの整合性検証
  // と同じ仕組み。docs/generate-kento-manifest.sh)の値をクエリとして付け、内容が
  // 変わればキーも変わるようにする。
  async function fetchExpectedHash(url) {
    const u = new URL(url);
    const idx = u.pathname.indexOf("/kento-assets/");
    if (idx < 0) {
      return null;
    }
    const relPath = u.pathname.slice(idx + 1);
    const manifestUrl = new URL("../MANIFEST.json", u).href;
    const resp = await fetch(manifestUrl);
    if (!resp.ok) {
      return null;
    }
    const manifest = await resp.json();
    return (manifest && manifest.files && manifest.files[relPath]) || null;
  }

  // マニフェストの期待ハッシュはネットワーク越しに取得した値であり、CDNのノード間で
  // MANIFEST.jsonと実体ファイルの伝播タイミングがずれると、期待値だけ新しく実体は
  // 古いままという食い違いが起こりうる。ダウンロード内容から実際に計算したハッシュと
  // 突き合わせてから、そのハッシュ付きキーへ書き込む。
  async function digestMatches(buf, expectedHex) {
    if (!(self.crypto && self.crypto.subtle)) {
      return true;
    }
    try {
      const digest = await self.crypto.subtle.digest("SHA-256", buf);
      return toHex(digest) === expectedHex;
    } catch {
      return true;
    }
  }

  function toHex(buffer) {
    return Array.from(new Uint8Array(buffer))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  }

  async function findEntriesByBasename(cache, basename) {
    const keys = await cache.keys();
    return keys.filter((request) => new URL(request.url).pathname.split("/").pop() === basename);
  }

  async function matchAnyByBasename(cache, url) {
    const basename = new URL(url).pathname.split("/").pop();
    const [first] = await findEntriesByBasename(cache, basename);
    return first ? await cache.match(first) : null;
  }

  async function evictStaleEntries(cache, currentKey) {
    const basename = new URL(currentKey).pathname.split("/").pop();
    for (const request of await findEntriesByBasename(cache, basename)) {
      if (request.url !== currentKey) {
        await cache.delete(request);
      }
    }
  }

  self.kentoWasmAssetCache = { fetchCachedArrayBuffer };
})();
