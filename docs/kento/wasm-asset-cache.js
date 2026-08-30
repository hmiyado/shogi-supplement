// GitHub PagesはCache-Controlを長めに設定しておらず、大容量ファイル(nn.bin=評価関数、
// 約61MB)をブラウザの標準HTTPキャッシュがどれだけ保持するかはブラウザの裁量任せで
// 確実ではない。Cache Storage APIで明示的にキャッシュし、確実に再利用できるようにする。
(function () {
  const CACHE_NAME = "kento-engine-assets-v1";

  async function fetchCachedArrayBuffer(url) {
    if (!isHttpUrl(url)) {
      // kentolocal:等の非http(s)スキーム(iOSのWKWebViewからのローカル配信)では、
      // 既にネットワークを経由しないローカルファイルとして配信されているため
      // Cache Storageもマニフェスト照合も意味を持たない。
      return await fetchFresh(url);
    }
    const cache = await openCacheOrNull();
    if (!cache) {
      return await fetchFresh(url);
    }
    const hash = await fetchExpectedHash(url).catch(() => null);
    const preferredKey = hash ? withHashQuery(url, hash) : null;
    const hit = await lookupCache(cache, url, preferredKey);
    if (hit) {
      return await hit.arrayBuffer();
    }
    if (!(self.navigator && self.navigator.locks)) {
      return await fetchAndStore(cache, url, preferredKey, hash);
    }
    // 同一URLへの並行呼び出しがcache missを同時に観測すると、それぞれが61MBを
    // 重複取得しうる。同じファイルへのロックを取り、先着側が取得・保存した結果を
    // 後続側がそのまま使えるようにする。
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
    // マニフェストが取れずハッシュで最新性を確認できない場合でも、同一パスの既存
    // キャッシュがあればそれを使う(無いよりまし。次回マニフェストが取れれば差し替わる)。
    // ignoreSearchでパス一致に限る(basename一致では別バージョンディレクトリを拾いうる)。
    return await cache.match(url, { ignoreSearch: true });
  }

  async function fetchAndStore(cache, url, preferredKey, hash) {
    const buf = await fetchFresh(url);
    if (hash && !(await digestMatches(buf, hash))) {
      // マニフェストの期待値と実際の取得内容が食い違う場合(CDNの伝播遅延等)、
      // 誤った内容を正しいハッシュのキャッシュとして残さないよう今回は書き込まない。
      console.warn(`wasm-asset-cache: マニフェストのハッシュと取得内容が一致しません (${url})`);
      return buf;
    }
    const key = preferredKey || url;
    // 掃除を後回しにすると、約61MBの新しい版を書き込む際に容量超過でputそのものが
    // 失敗し、古い版が残り続けうる。
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
    // Responseそのものではなく読み切ったArrayBufferを返す: Response.clone()はストリームを
    // teeするため、cloneを未消費のまま置いておくと約61MBの2重バッファがピークで発生しうる。
    return await resp.arrayBuffer();
  }

  function isHttpUrl(url) {
    const protocol = new URL(url).protocol;
    return protocol === "http:" || protocol === "https:";
  }

  // Cache Storageはhttp(s)コンテキストでも常に使えるとは限らない(例: LAN内IP直打ち等の
  // 非セキュアオリジンではcachesが未定義になる)。開けなければ素のfetchへ落とす。
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

  function withHashQuery(url, hash) {
    const u = new URL(url);
    u.searchParams.set("sha256", hash);
    return u.href;
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

  async function evictStaleEntries(cache, currentKey) {
    const basename = new URL(currentKey).pathname.split("/").pop();
    const keys = await cache.keys();
    for (const request of keys) {
      if (request.url === currentKey) {
        continue;
      }
      if (new URL(request.url).pathname.split("/").pop() === basename) {
        await cache.delete(request);
      }
    }
  }

  self.kentoWasmAssetCache = { fetchCachedArrayBuffer };
})();
