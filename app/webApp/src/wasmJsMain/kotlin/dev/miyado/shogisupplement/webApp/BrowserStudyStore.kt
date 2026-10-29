@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 棋譜ごとの検討KIF。比較と更新を同じIndexedDBトランザクションで行う。 */
internal class BrowserStudyStore(private val databaseName: String = "shogi-supplement-study") {
    suspend fun load(key: String): String? = operation("load", key, null, null)

    suspend fun entries(): Map<String, String> = kotlinx.serialization.json.Json.decodeFromString(
        requireNotNull(operation("list", "", null, null)),
    )

    suspend fun save(key: String, expected: String?, kif: String): Boolean =
        operation("save", key, expected, kif) == "saved"

    /** 内容と選択中のキーを同じトランザクションで確定する。 */
    suspend fun saveAndSelect(key: String, expected: String?, value: String, selectionKey: String): Boolean {
        require(key != selectionKey)
        return operation("save", key, expected, value, selectionKey) == "saved"
    }

    suspend fun delete(key: String, expected: String): Boolean =
        operation("delete", key, expected, null) == "saved"

    suspend fun deleteAndUnselect(key: String, expected: String, selectionKey: String): Boolean {
        require(key != selectionKey)
        return operation("delete", key, expected, null, selectionKey) == "saved"
    }

    suspend fun deleteReport(key: String, expected: String, studyKey: String, expectedStudy: String?): Boolean =
        operation("delete", key, expected, null, "last", studyKey, expectedStudy) == "saved"

    suspend fun saveReport(key: String, expected: String?, value: String, original: String, seedStudy: Boolean = false): Boolean =
        operation(if (seedStudy) "importReport" else "saveReport", key, expected, value, "last", "study:$key", original) == "saved"

    private suspend fun operation(action: String, key: String, expected: String?, kif: String?, selectionKey: String? = null,
        studyKey: String? = null, expectedStudy: String? = null): String? =
        suspendCancellableCoroutine { continuation ->
            val handle = studyStorageOperation(databaseName, action, key, expected, kif, selectionKey, studyKey, expectedStudy,
                onSuccess = { if (continuation.isActive) continuation.resume(it) },
                onFailure = { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Browser study storage failed")) },
            )
            continuation.invokeOnCancellation { handle.cancel() }
        }
}

private external interface StudyStorageHandle : JsAny {
    fun cancel()
}

@JsFun("""(databaseName, action, key, expected, kif, selectionKey, studyKey, expectedStudy, onSuccess, onFailure) => {
    let db = null, tx = null, cancelled = false, finished = false;
    const finish = (ok, value) => {
        if (finished) return;
        finished = true;
        if (db) db.close();
        if (!cancelled) { if (ok) onSuccess(value); else onFailure(); }
    };
    const open = indexedDB.open(databaseName, 1);
    open.onupgradeneeded = () => {
        if (!open.result.objectStoreNames.contains('kifu')) open.result.createObjectStore('kifu');
    };
    open.onerror = () => finish(false, null);
    open.onblocked = () => finish(false, null);
    open.onsuccess = () => {
        db = open.result;
        if (cancelled || finished) { db.close(); return; }
        db.onversionchange = () => db.close();
        try {
            tx = db.transaction('kifu', action === 'load' || action === 'list' ? 'readonly' : 'readwrite');
            const store = tx.objectStore('kifu');
            let result = null;
            tx.oncomplete = () => finish(true, result);
            tx.onabort = () => finish(false, null);
            tx.onerror = () => finish(false, null);
            if (action === 'list') {
                const entries = Object.create(null);
                const cursor = store.openCursor();
                cursor.onsuccess = () => {
                    if (cursor.result) {
                        entries[cursor.result.key] = cursor.result.value;
                        cursor.result.continue();
                    } else result = JSON.stringify(entries);
                };
                return;
            }
            const request = store.get(key);
            request.onsuccess = () => {
                const current = request.result === undefined ? null : request.result;
                if (action === 'load') { result = current; return; }
                if (current !== expected) { result = 'conflict'; return; }
                const commit = () => {
                if (action === 'delete') store.delete(key); else store.put(kif, key);
                if (selectionKey !== null) {
                    if (action === 'delete') {
                        const selected = store.get(selectionKey);
                        selected.onsuccess = () => {
                            if (selected.result === key) store.delete(selectionKey);
                        };
                    } else store.put(key, selectionKey);
                }
                result = 'saved';
                };
                if (studyKey !== null) {
                    const study = store.get(studyKey);
                    study.onsuccess = () => {
                        const currentStudy = study.result === undefined ? null : study.result;
                        if (action === 'saveReport' || action === 'importReport') {
                            if (currentStudy === '' || (action === 'importReport' && currentStudy === null)) store.put(expectedStudy, studyKey);
                            commit();
                            return;
                        }
                        if (currentStudy !== expectedStudy) { result = 'conflict'; return; }
                        // 空文字は削除済みの印。旧保存領域からの再取込と古いタブの保存を防ぐ。
                        store.put('', studyKey);
                        commit();
                    };
                } else commit();
            };
        } catch (_) { finish(false, null); }
    };
    return { cancel: () => { cancelled = true; if (tx) { try { tx.abort(); } catch (_) {} } if (db) db.close(); } };
}""")
private external fun studyStorageOperation(
    databaseName: String, action: String, key: String, expected: String?, kif: String?,
    selectionKey: String?,
    studyKey: String?, expectedStudy: String?,
    onSuccess: (String?) -> Unit, onFailure: () -> Unit,
): StudyStorageHandle
