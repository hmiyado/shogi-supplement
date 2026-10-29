package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.kifu.KifTreeParser
import dev.miyado.shogisupplement.kifu.KifTreeWriter

/** 1局分の編集セッション。保存競合時は未保存の編集を保持し、勝手に再読込・上書きしない。 */
class StudyDocument(
    initialKif: String,
    private val persist: (expectedKif: String, editedKif: String) -> Boolean,
) {
    private var expectedKif = initialKif
    /** 最後に保存成功したKIF。未保存の編集はコピー・共有へ混ぜない。 */
    val savedKif: String get() = expectedKif
    private val baseline = KifTreeParser().parse(initialKif)
    private val headers = baseline.headers
    var tree: StudyTree = StudyTree.fromKifu(baseline)
        private set
    var isDirty: Boolean = false
        private set

    data class Edit(val originMoves: List<String>, val tree: StudyTree, val baseline: StudyTree?)

    /** 複数の開始局面の編集を一括反映し、競合時は途中まで反映した状態を残さない。 */
    fun updateAll(edits: List<Edit>) {
        val previousTree = tree
        val previousDirty = isDirty
        try {
            val changed = edits.filter { it.baseline == null || it.tree.toKifu(emptyMap()) != it.baseline.toKifu(emptyMap()) }
            changed.sortedBy { it.originMoves.size }.forEach { edit ->
                val removedByParent = changed.any { parent ->
                    val prefix = parent.originMoves
                    if (prefix.size >= edit.originMoves.size || edit.originMoves.take(prefix.size) != prefix) false
                    else {
                        val relative = edit.originMoves.drop(prefix.size)
                        parent.baseline?.subtree(relative) != null && parent.tree.subtree(relative) == null
                    }
                }
                check(!removedByParent && !(previousTree.subtree(edit.originMoves) != null && tree.subtree(edit.originMoves) == null)) {
                    "Study origin was deleted by another edit"
                }
                update(edit.originMoves, edit.tree, edit.baseline)
            }
        } catch (e: Exception) {
            tree = previousTree
            isDirty = previousDirty
            throw e
        }
    }

    fun update(originMoves: List<String>, edited: StudyTree, editBaseline: StudyTree? = null) {
        var rooted = tree
        fun minimumId(nodes: List<StudyNode>): Long = nodes.minOfOrNull { minOf(it.id, minimumId(it.children)) } ?: 0L
        var nextId = minimumId(rooted.rootChildren) - 1
        originMoves.forEachIndexed { index, move ->
            rooted = rooted.withMovePlayed(originMoves.take(index), move, nextId--)
        }
        val merged = if (editBaseline != null) requireNotNull(rooted.subtree(originMoves)).mergeEdits(editBaseline, edited) else edited
        val updated = rooted.withSubtree(originMoves, merged).copy(selectedChildren = emptyMap())
        // 削除や並び替えで解析対象の本譜が別の枝へ変わることを保存前に防ぐ。
        val main = updated.toKifu(headers)
        require(main.mainLineContent() == baseline.mainLineContent()) { "Study edit changes the analyzed main line" }
        KifTreeWriter().write(main)
        if (updated != tree) {
            tree = updated
            isDirty = true
        }
    }

    fun save(): Boolean {
        if (!isDirty) return true
        val kif = KifTreeWriter().write(tree.toKifu(headers))
        if (!persist(expectedKif, kif)) return false
        expectedKif = kif
        isDirty = false
        return true
    }

    /** ブラウザの非同期トランザクション用。保存待ち中の追加編集は未保存のまま残す。 */
    suspend fun saveUsing(persist: suspend (String, String) -> Boolean): Boolean {
        if (!isDirty) return true
        val snapshot = tree
        val kif = KifTreeWriter().write(snapshot.toKifu(headers))
        if (!persist(expectedKif, kif)) return false
        expectedKif = kif
        isDirty = tree != snapshot
        return true
    }

    companion object {
        fun load(repository: GameRepository, gameId: Long): StudyDocument? =
            repository.getStudyKif(gameId)?.let { kif ->
                StudyDocument(kif) { expected, edited -> repository.saveStudyKif(gameId, expected, edited) }
            }
    }
}
