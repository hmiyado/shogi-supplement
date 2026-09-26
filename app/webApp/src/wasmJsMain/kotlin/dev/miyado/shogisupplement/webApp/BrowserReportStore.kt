package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.util.sha256Hex
import dev.miyado.shogisupplement.webApp.report.WebReportData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 解析時の結果をそのまま保存し、再開時のエンジン実行・係数更新を避ける。 */
internal class BrowserReportStore(
    private val store: BrowserStudyStore = BrowserStudyStore("shogi-supplement-reports"),
) {
    private val json = Json { ignoreUnknownKeys = true }
    data class SavedReport(val key: String, val version: String, val report: WebReportData, val studyVersion: String?)

    suspend fun list(): List<SavedReport> {
        store.entries().filterKeys { it != "last" && !it.startsWith("study:") }.values.forEach { value ->
            val report = json.decodeFromString<WebReportData>(value)
            BrowserStudyDocument.migrateLegacy(requireNotNull(report.game.kifText), store)
        }
        val entries = store.entries()
        return entries.filterKeys { it != "last" && !it.startsWith("study:") }.map { (key, value) ->
            SavedReport(key, value, json.decodeFromString<WebReportData>(value), entries["study:$key"])
        }.sortedByDescending { it.report.game.analyzedAt }
    }

    suspend fun load(key: String): WebReportData? {
        require(key != "last")
        return store.load(key)?.let { json.decodeFromString<WebReportData>(it) }
    }

    /** 一覧取得後に他タブが再解析した文書は削除しない。選択解除も同じ取引内で行う。 */
    suspend fun delete(saved: SavedReport): Boolean {
        BrowserStudyDocument.migrateLegacy(requireNotNull(saved.report.game.kifText), store)
        return store.deleteReport(saved.key, saved.version, "study:${saved.key}", saved.studyVersion)
    }

    suspend fun version(original: String): String? = store.load(sha256Hex(original))
    suspend fun loadLast(): WebReportData? = store.load("last")?.let { key ->
        store.load(key)?.let { json.decodeFromString<WebReportData>(it) }
    }

    suspend fun save(report: WebReportData, expected: String?, initialStudyKif: String? = null): Boolean {
        val key = sha256Hex(requireNotNull(report.game.kifText))
        return store.saveReport(key, expected, json.encodeToString(report), initialStudyKif ?: requireNotNull(report.game.kifText), seedStudy = initialStudyKif != null)
    }
}
