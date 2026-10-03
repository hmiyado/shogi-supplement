package dev.miyado.shogisupplement.repertoire

import dev.miyado.shogisupplement.board.ShogiBoard
import kotlinx.serialization.Serializable

@Serializable
data class RepertoireLine(val move: String, val children: List<RepertoireLine> = emptyList())

@Serializable
data class RepertoireDocument(
    val name: String,
    val initialSfen: String,
    val lines: List<RepertoireLine> = emptyList(),
)

@Serializable
data class PositionLabels(val positionKey: String, val labels: List<String>)

enum class PositionLabelScope(val title: String) {
    ALL("局面全体"), BLACK("先手の形"), WHITE("後手の形"),
}

fun positionLabelKey(sfen: String, scope: PositionLabelScope = PositionLabelScope.ALL): String =
    positionLabelKey(ShogiBoard.fromSfen(sfen), scope)

private fun positionLabelKey(board: ShogiBoard, scope: PositionLabelScope = PositionLabelScope.ALL): String {
    val normalized = board.toSfen().split(' ')
    if (scope == PositionLabelScope.ALL) return "${normalized[0]} ${normalized[2]}"
    val side = if (scope == PositionLabelScope.BLACK) dev.miyado.shogisupplement.board.Side.BLACK else dev.miyado.shogisupplement.board.Side.WHITE
    val pieces = buildList {
        for (rank in 1..9) for (file in 9 downTo 1) {
            val square = dev.miyado.shogisupplement.board.ShogiSquare(file, rank)
            board.pieceAt(square)?.takeIf { it.side == side }?.let { add("$file$rank:${it.type.name}") }
        }
    }.joinToString(",")
    val hand = board.getHand(side).entries.filter { it.value > 0 }.sortedBy { it.key.name }
        .joinToString(",") { "${it.key.name}:${it.value}" }
    return "${scope.name}:$pieces $hand"
}

fun PositionLabels.displayLabels(): List<String> {
    val prefix = when {
        positionKey.startsWith("BLACK:") -> "先手："
        positionKey.startsWith("WHITE:") -> "後手："
        else -> ""
    }
    return labels.map { prefix + it }
}

private fun Map<String, List<String>>.matching(board: ShogiBoard): List<String> =
    PositionLabelScope.entries.flatMap { get(positionLabelKey(board, it)).orEmpty() }

fun RepertoireDocument.labelEntries(entries: List<RepertoireEntry>): List<RepertoireEntry> {
    val keys = mutableSetOf<String>()
    val queue = ArrayDeque<Pair<String, List<RepertoireLine>>>()
    queue.add(initialSfen to lines)
    while (queue.isNotEmpty()) {
        val (sfen, children) = queue.removeFirst()
        val position = ShogiBoard.fromSfen(sfen)
        PositionLabelScope.entries.forEach { keys.add(positionLabelKey(position, it)) }
        for (line in children) {
            val board = ShogiBoard.fromSfen(sfen)
            board.push(dev.miyado.shogisupplement.board.ShogiMove.fromUsi(line.move))
            queue.add(board.toSfen() to line.children)
        }
    }
    return entries.filter { it.kind == "labels" && RepertoireCodec.labels(it.payload).positionKey in keys }
}

fun RepertoireDocument.allLabels(entries: List<RepertoireEntry>): List<String> =
    labelEntries(entries).flatMap { RepertoireCodec.labels(it.payload).displayLabels() }.distinct()

fun RepertoireRepository.addPositionLabel(owner: String, sfen: String, scope: PositionLabelScope, name: String): Boolean {
    val text = name.trim()
    require(text.isNotEmpty() && text.length <= 100 && !text.contains('\n') && !text.contains('\r'))
    val key = positionLabelKey(sfen, scope)
    val id = "labels-${dev.miyado.shogisupplement.util.sha256Hex(key)}"
    val old = entries(owner).firstOrNull { it.id == id }?.payload
    if (old != null && RepertoireCodec.labels(old).labels.isNotEmpty()) return false
    return compareAndSave(owner, id, "labels", old, RepertoireCodec.encode(PositionLabels(key, listOf(text))))
}

fun RepertoireRepository.removePositionLabel(owner: String, entry: RepertoireEntry, label: String): Boolean {
    val old = RepertoireCodec.labels(entry.payload)
    return compareAndSave(owner, entry.id, "labels", entry.payload,
        RepertoireCodec.encode(old.copy(labels = old.labels.filterNot { it == label })))
}

data class RepertoireEntry(
    val id: String,
    val kind: String,
    val payload: String,
    val revision: Long,
    val remoteVersion: String?,
    val dirty: Boolean,
    val uploadToken: String? = null,
)

interface RepertoireRepository {
    fun entries(owner: String): List<RepertoireEntry>
    fun pending(owner: String): List<RepertoireEntry> = entries(owner).filter { it.dirty }
    fun save(owner: String, id: String, kind: String, payload: String)
    fun compareAndSave(owner: String, id: String, kind: String, expectedPayload: String?, payload: String): Boolean {
        if (entries(owner).firstOrNull { it.id == id }?.payload != expectedPayload) return false
        save(owner, id, kind, payload)
        return true
    }
    fun acknowledge(owner: String, id: String, revision: Long, remoteVersion: String)
    fun acceptRemote(owner: String, id: String, kind: String, payload: String, remoteVersion: String)
}

data class RemoteRepertoireEntry(val id: String, val kind: String, val payload: String, val version: String)

interface RepertoireRemote {
    suspend fun list(owner: String): List<RemoteRepertoireEntry>
    suspend fun put(owner: String, entry: RepertoireEntry): String?
}

object RepertoireCodec {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    fun document(payload: String): RepertoireDocument = json.decodeFromString(payload)
    fun labels(payload: String): PositionLabels = json.decodeFromString(payload)
    fun encode(document: RepertoireDocument): String = json.encodeToString(document)
    fun encode(labels: PositionLabels): String = json.encodeToString(labels)
}

fun RepertoireRepository.labelledGames(owner: String, games: List<dev.miyado.shogisupplement.db.GameRecord>): List<dev.miyado.shogisupplement.db.GameRecord> {
    val labels = entries(owner).filter { it.kind == "labels" }.map { RepertoireCodec.labels(it.payload) }
        .associate { it.positionKey to it.displayLabels() }
    if (labels.isEmpty()) return games.map { it.copy(positionLabels = emptySet()) }
    return games.map { game ->
        val found = mutableSetOf<String>()
        val board = ShogiBoard()
        found.addAll(labels.matching(board))
        for (move in game.movesUsi) {
            val parsed = runCatching { dev.miyado.shogisupplement.board.ShogiMove.fromUsi(move) }.getOrNull() ?: break
            board.push(parsed)
            found.addAll(labels.matching(board))
        }
        game.studyKif?.let { kif ->
            val tree = runCatching { dev.miyado.shogisupplement.kifu.KifTreeParser().parse(kif) }.getOrNull()
            if (tree != null) {
                val children = tree.nodes.groupBy { it.parentId }
                val queue = ArrayDeque<Pair<Int, String>>()
                queue.add(0 to ShogiBoard().toSfen())
                val visited = mutableSetOf<Int>()
                while (queue.isNotEmpty()) {
                    val (parent, sfen) = queue.removeFirst()
                    if (!visited.add(parent)) continue
                    for (node in children[parent].orEmpty()) {
                        val move = node.moveUsi ?: continue
                        val position = runCatching {
                            ShogiBoard.fromSfen(sfen).also { it.push(dev.miyado.shogisupplement.board.ShogiMove.fromUsi(move)) }
                        }.getOrNull() ?: continue
                        found.addAll(labels.matching(position))
                        queue.add(node.id to position.toSfen())
                    }
                }
            }
        }
        game.copy(positionLabels = found)
    }
}

fun RepertoireDocument.firstLabelPath(key: String): List<String>? {
    fun find(sfen: String, children: List<RepertoireLine>, moves: List<String>): List<String>? {
        if (PositionLabelScope.entries.any { positionLabelKey(sfen, it) == key }) return moves
        for (line in children) {
            val board = ShogiBoard.fromSfen(sfen)
            board.push(dev.miyado.shogisupplement.board.ShogiMove.fromUsi(line.move))
            find(board.toSfen(), line.children, moves + line.move)?.let { return it }
        }
        return null
    }
    return find(initialSfen, lines, emptyList())
}

fun RepertoireRepository.allPositionLabels(owner: String): List<String> = entries(owner)
    .filter { it.kind == "labels" }.flatMap { RepertoireCodec.labels(it.payload).displayLabels() }.distinct().sorted()
