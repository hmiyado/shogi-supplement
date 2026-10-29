package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.ui.report.StudyDocument
import dev.miyado.shogisupplement.util.sha256Hex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 原文のハッシュで検討を特定し、別タブの保存を比較更新で保護する。 */
internal class BrowserStudyDocument private constructor(
    private val store: BrowserStudyStore,
    private val key: String,
    private var stored: String?,
    val document: StudyDocument,
) {
    private val mutex = Mutex()

    suspend fun save(): Boolean = mutex.withLock {
        document.saveUsing { _, edited ->
            if (!store.save(key, stored, edited)) false
            else { stored = edited; true }
        }
    }

    companion object {
        internal const val MIGRATING_PREFIX = "\u0000study-migration:"
        fun storageKey(original: String): String = "study:" + sha256Hex(original)

        /** 旧領域を先に凍結する。中断時は凍結値から再開し、新領域への確定後だけ本文を消す。 */
        suspend fun migrateLegacy(original: String, store: BrowserStudyStore) {
            val legacyStore = BrowserStudyStore()
            val legacyKey = sha256Hex(original)
            repeat(8) {
                val old = legacyStore.load(legacyKey)
                if (old == "") return
                val frozen = if (old?.startsWith(MIGRATING_PREFIX) == true) old
                    else MIGRATING_PREFIX + old.orEmpty()
                if (old != frozen && !legacyStore.save(legacyKey, old, frozen)) return@repeat
                val content = frozen.removePrefix(MIGRATING_PREFIX)
                if (content.isNotEmpty() && !store.save(storageKey(original), null, content)) {
                    val current = store.load(storageKey(original))
                    check(current == content || current == "") { "Legacy study migration conflicted" }
                }
                if (legacyStore.save(legacyKey, frozen, "") || legacyStore.load(legacyKey) == "") return
            }
            error("Legacy study migration conflicted")
        }

        suspend fun load(original: String, store: BrowserStudyStore = BrowserStudyStore("shogi-supplement-reports")): BrowserStudyDocument {
            val key = storageKey(original)
            migrateLegacy(original, store)
            val saved = store.load(key)
            check(saved != "") { "Study was deleted" }
            return BrowserStudyDocument(store, key, saved, StudyDocument(saved ?: original) { _, _ -> false })
        }
    }
}
