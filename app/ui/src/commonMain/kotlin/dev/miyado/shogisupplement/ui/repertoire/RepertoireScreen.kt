package dev.miyado.shogisupplement.ui.repertoire

import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.engine.StudyEngine
import dev.miyado.shogisupplement.repertoire.*
import dev.miyado.shogisupplement.ui.common.*
import dev.miyado.shogisupplement.ui.report.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun RepertoireScreen(
    repository: RepertoireRepository,
    owner: String?,
    sync: RepertoireSync?,
    engineFactory: suspend () -> StudyEngine,
    onBack: () -> Unit,
    evalDisplay: String = "cp",
) {
    key(owner) { RepertoireScreenContent(repository, owner, sync, engineFactory, onBack, evalDisplay) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
private fun RepertoireScreenContent(
    repository: RepertoireRepository,
    owner: String?,
    sync: RepertoireSync?,
    engineFactory: suspend () -> StudyEngine,
    onBack: () -> Unit,
    evalDisplay: String = "cp",
) {
    val scope = rememberCoroutineScope()
    var entries by remember(owner) { mutableStateOf(owner?.let(repository::entries).orEmpty()) }
    var selected by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var selectionMode by remember(owner) { mutableStateOf(false) }
    var deleteIds by remember(owner) { mutableStateOf(setOf<String>()) }
    var deleteTargets by remember(owner) { mutableStateOf<List<RepertoireEntry>>(emptyList()) }
    val lines = entries.filter { it.kind == "line" && !RepertoireCodec.document(it.payload).deleted }
    var message by remember { mutableStateOf<String?>(null) }
    fun refresh() { entries = owner?.let(repository::entries).orEmpty() }
    fun synchronize(notice: String? = null) {
        scope.launch {
            try {
                if (sync?.synchronize() == false) message = listOfNotNull(notice, "サーバーへの保存が未完了です。端末の変更は保持しています。").joinToString("\n")
                else message = notice
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = listOfNotNull(notice, "サーバーに接続できませんでした。端末の変更は保持しています。").joinToString("\n") }
        }
    }
    LaunchedEffect(owner) { synchronize() }
    val detailChanged = dev.miyado.shogisupplement.ui.navigation.LocalRootDetailChanged.current
    SideEffect { detailChanged(selected != null) }
    DisposableEffect(Unit) { onDispose { detailChanged(false) } }
    val id = selected
    if (id != null && owner != null) {
        val entry = entries.firstOrNull { it.id == id }
        var savedPayload by remember(id) { mutableStateOf(entry?.payload) }
        val document = remember(id) { entry?.let { RepertoireCodec.document(it.payload) }
            ?: RepertoireDocument("", ShogiBoard().toSfen()) }
        RepertoireStudyScreen(
            document = document,
            evalDisplay = evalDisplay,
            engineFactory = engineFactory,
            onBack = { selected = null; refresh() },
            onSave = { saved ->
                val payload = RepertoireCodec.encode(saved)
                if (payload != savedPayload) {
                    check(repository.compareAndSave(owner, id, "line", savedPayload, payload))
                    savedPayload = payload
                    refresh()
                    synchronize()
                }
            },
            labelContent = { sfen, draft, jump -> PositionLabelEditor(repository, owner, sfen, onSaved = { refresh(); synchronize() }, document = draft, onLabelSelected = jump) },
        )
        return
    }
    fun exitSelection() { selectionMode = false; deleteIds = emptySet() }
    ReportBackHandler(onBack = { if (selectionMode) exitSelection() else onBack() })
    if (deleteTargets.isNotEmpty() && owner != null) AlertDialog(
        onDismissRequest = { deleteTargets = emptyList() },
        title = { Text("選択した定跡を削除しますか？") },
        text = { Text("定跡の手順を削除します。局面ラベルは残ります。") },
        confirmButton = { TextButton(onClick = {
            var conflict = false
            deleteTargets.forEach { entry ->
                val doc = RepertoireCodec.document(entry.payload)
                if (!repository.compareAndSave(owner, entry.id, "line", entry.payload,
                    RepertoireCodec.encode(doc.copy(deleted = true)))) conflict = true
            }
            deleteTargets = emptyList()
            exitSelection()
            refresh()
            synchronize(if (conflict) "変更された定跡は削除しませんでした。再度選択してください。" else null)
        }) { Text("削除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteTargets = emptyList() }) { Text("キャンセル") } },
    )
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = if (dev.miyado.shogisupplement.ui.navigation.rootTabsOverlayContent) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface), title = { Text(if (selectionMode) "${deleteIds.size}件選択中" else "定跡一覧") }, navigationIcon = {
            if (selectionMode) IconButton(onClick = { exitSelection() }) { Icon(Icons.Default.Close, "選択を終了") }
            else if (!dev.miyado.shogisupplement.ui.navigation.LocalRootTabHost.current) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") }
        }, actions = {
            if (lines.isNotEmpty()) IconButton(
                onClick = { if (selectionMode) deleteTargets = lines.filter { it.id in deleteIds } else selectionMode = true },
                enabled = !selectionMode || deleteIds.isNotEmpty(),
            ) { Icon(Icons.Outlined.Delete, if (selectionMode) "定跡を削除" else "削除する定跡を選択", tint = MaterialTheme.colorScheme.error) }
        }) },
        bottomBar = {
            Button(onClick = { selected = Uuid.random().toString() }, enabled = owner != null,
                modifier = Modifier.fillMaxWidth().padding(bottom = dev.miyado.shogisupplement.ui.navigation.LocalRootTabBottomPadding.current).padding(16.dp), shape = MaterialTheme.shapes.medium) { Text("定跡を追加する") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).adaptiveContentWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (owner == null) item { Text("アカウントを作成すると定跡を保存できます。") }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(lines, key = { it.id }) { entry ->
                val doc = RepertoireCodec.document(entry.payload)
                Card(onClick = { if (selectionMode) deleteIds = if (entry.id in deleteIds) deleteIds - entry.id else deleteIds + entry.id else selected = entry.id }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (selectionMode) Checkbox(checked = entry.id in deleteIds, onCheckedChange = null)
                            Text(doc.name, style = MaterialTheme.typography.titleMedium)
                        }
                        val labels = remember(entry.payload, entries) { doc.allLabels(entries) }
                        if (labels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            labels.forEach { label -> Surface(shape = MaterialTheme.shapes.small,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepertoireStudyScreen(
    document: RepertoireDocument,
    evalDisplay: String,
    engineFactory: suspend () -> StudyEngine,
    onBack: () -> Unit,
    onSave: (RepertoireDocument) -> Unit,
    labelContent: @Composable (String, RepertoireDocument, (String) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draftPayload by rememberSaveable { mutableStateOf(RepertoireCodec.encode(document)) }
    val initialDocument = remember { RepertoireCodec.document(draftPayload) }
    var base by rememberSaveable { mutableStateOf(initialDocument.initialSfen) }
    var name by rememberSaveable { mutableStateOf(initialDocument.name) }
    var nameInput by rememberSaveable { mutableStateOf(initialDocument.name) }
    var flip by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var discard by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val currentEvalDisplay by rememberUpdatedState(evalDisplay)
    val saveAction = remember { object { var invoke: () -> Unit = {} } }
    val controller = remember { StudyController(scope, engineFactory, { currentEvalDisplay }, onTreeChanged = { saveAction.invoke() }) }
    val state by controller.studyState.collectAsState()
    fun save(): Boolean = try {
        val draft = RepertoireDocument(name.trim(), base, controller.currentTree()?.rootChildren?.toLines() ?: initialDocument.lines)
        draftPayload = RepertoireCodec.encode(draft)
        if (name.isNotBlank()) onSave(draft)
        saveError = null
        controller.setSaveFailed(false)
        true
    } catch (_: Exception) {
        saveError = "保存できませんでした。もう一度お試しください。"
        controller.setSaveFailed(true)
        false
    }
    saveAction.invoke = { save() }
    fun leave() {
        if (name.isBlank() && (controller.currentTree()?.rootChildren?.isNotEmpty() == true || base != document.initialSfen)) discard = true
        else if (save()) onBack() else discard = true
    }
    fun start(sfen: String, lines: List<RepertoireLine>) {
        controller.startStudy(sfen, false, false, 0, null, 0, StudyOrigin("", null), initialTree = lines.toStudyTree(), protectedMoves = emptyList())
    }
    LaunchedEffect(Unit) { start(base, initialDocument.lines) }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("定跡を保存せずに閉じますか？") },
        confirmButton = { TextButton(onClick = onBack) { Text("保存せず閉じる") } },
        dismissButton = { TextButton(onClick = { discard = false; naming = true }) { Text("保存する") } },
    )
    if (editing) {
        InitialPositionEditorScreen(base, state?.displayLine?.isNotEmpty() == true, initialFlip = flip,
            onClose = { editing = false }, onApply = { sfen ->
                if (sfen != dev.miyado.shogisupplement.board.InitialPositionEditor.fromSfen(base).toSfen()) { base = sfen; start(sfen, emptyList()); save() }
                editing = false
            })
        return
    }
    if (naming) AlertDialog(
        onDismissRequest = { naming = false }, title = { Text("定跡名") },
        text = { OutlinedTextField(value = nameInput, onValueChange = { nameInput = it.take(100) }, singleLine = true) },
        confirmButton = { TextButton(enabled = nameInput.isNotBlank(), onClick = { name = nameInput.trim(); save(); naming = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text("キャンセル") } },
    )
    ReportBackHandler(onBack = ::leave)
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = { TopAppBar(title = { Text("定跡") }, navigationIcon = {
            IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") }
        }, actions = {
            TextButton(onClick = { flip = !flip }) { Text("反転") }

        }) },
    ) { padding ->
        val current = state
        if (current != null) Column(Modifier.fillMaxSize().padding(padding).adaptiveContentWidth()) {
            val sfen = computeSfenAtStep(base, current.moves, current.moves.size)
            ShogiBoardView(sfen, Modifier.heightIn(max = boardMaxHeight()), flip = flip, selectedFrom = current.selectedFrom,
                selectedDropType = current.selectedDropType, legalDestinations = current.legalDestinations,
                onSquareTapped = controller::onStudySquareTapped, onHandPieceTapped = controller::onStudyHandPieceTapped)
            StudyPromoteDialog(current.showPromoteDialog, controller::onStudyPromoteDecision)
            StudyNavRow(current, ShogiBoard.fromSfen(sfen).turn == Side.BLACK, controller::studyStepBack,
                { controller.onChipTapped(current.moves.size + 1) }, null)
            BoardTabRow(listOf("定跡", "局面ラベル", "検討"), tab, { tab = it })
            when (tab) {
                0 -> Column(Modifier.padding(16.dp)) {
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(name.ifEmpty { "新しい定跡" }, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { nameInput = name; naming = true }) { Text(if (name.isEmpty()) "定跡として登録" else "名前を編集") }
                    TextButton(onClick = { editing = true }) { Text("初期局面を作成") }
                }
                1 -> Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { labelContent(sfen, RepertoireDocument(name, base, controller.currentTree()?.rootChildren?.toLines().orEmpty())) { key ->
                    val draft = RepertoireDocument(name, base, controller.currentTree()?.rootChildren?.toLines().orEmpty())
                    draft.firstLabelPath(key)?.let(controller::navigateToMoves)
                } }
                2 -> StudyPanel(current, controller::onChipTapped, controller::onBranchChipTapped,
                    controller::onBranchPopupDismiss, controller::onBranchNodeSelected, controller::analyzeCurrentPosition,
                    controller::onCandidateSelected, modifier = Modifier.weight(1f),
                    onSave = { result -> result(save()) },
                    onDeleteBranch = controller::deleteBranchForPosition)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionLabelEditor(repository: RepertoireRepository, owner: String, sfen: String, onSaved: () -> Unit,
    inlineEditing: Boolean = false, document: RepertoireDocument? = null, onLabelSelected: ((String) -> Unit)? = null) {
    var entries by remember(owner, sfen, document) { mutableStateOf(repository.entries(owner)) }
    var adding by remember(owner, sfen) { mutableStateOf(false) }
    var scope by rememberSaveable { mutableStateOf(PositionLabelScope.ALL) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Pair<RepertoireEntry, String>?>(null) }
    fun refresh() { entries = repository.entries(owner); onSaved() }
    val current = entries.filter { it.kind == "labels" && RepertoireCodec.labels(it.payload).positionKey in
        PositionLabelScope.entries.map { positionLabelKey(sfen, it) } }
    fun occupied(item: PositionLabelScope): Boolean = current.any {
        val labels = RepertoireCodec.labels(it.payload)
        labels.positionKey == positionLabelKey(sfen, item) && labels.labels.isNotEmpty()
    }
    fun add() {
        try {
            if (repository.addPositionLabel(owner, sfen, scope, text)) {
                adding = false; text = ""; error = null; refresh()
            } else { entries = repository.entries(owner); error = "この対象にはラベルがあります。削除してから追加してください。" }
        } catch (_: Exception) { error = "保存できませんでした。もう一度お試しください。" }
    }
    @Composable fun tags(values: List<RepertoireEntry>) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEach { entry ->
                val labels = RepertoireCodec.labels(entry.payload)
                labels.labels.zip(labels.displayLabels()).forEach { (name, display) ->
                    Surface(shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(display, Modifier.then(if (onLabelSelected != null) Modifier.clickable { onLabelSelected(labels.positionKey) } else Modifier).padding(start = 8.dp, top = 12.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { error = null; removing = entry to name }, modifier = Modifier.size(40.dp)) {
                                Icon(androidx.compose.material.icons.Icons.Default.Close, "$display を削除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    val fields: @Composable () -> Unit = {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PositionLabelScope.entries.forEach { item ->
                    FilterChip(selected = scope == item, enabled = !occupied(item), onClick = { scope = item }, label = { Text(item.title) })
                }
            }
            OutlinedTextField(value = text, onValueChange = { text = it.replace("\n", "").replace("\r", "").take(100) },
                label = { Text("ラベル名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    removing?.let { (entry, name) ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("ラベルを削除しますか？") },
            text = { Column {
                Text("「$name」を削除します。同じ局面・形に適用されている他の棋譜からも外れます。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                try {
                    if (repository.removePositionLabel(owner, entry, name)) { removing = null; error = null; refresh() }
                    else { error = "別の更新があります。閉じてからもう一度お試しください。"; entries = repository.entries(owner) }
                } catch (_: Exception) { error = "削除できませんでした。もう一度お試しください。" }
            }) { Text("削除する", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("キャンセル") } })
    }
    if (adding && !inlineEditing) ModalBottomSheet(onDismissRequest = { adding = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("局面ラベルを付ける", style = MaterialTheme.typography.titleLarge)
            fields()
            Row {
                TextButton(onClick = { adding = false }) { Text("キャンセル") }
                TextButton(enabled = text.isNotBlank() && !occupied(scope), onClick = ::add) { Text("追加") }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (document != null) {
            Text("この棋譜のラベル", style = MaterialTheme.typography.bodySmall)
            tags(document.labelEntries(entries))
            HorizontalDivider()
        }
        Text("この局面のラベル", style = MaterialTheme.typography.bodySmall)
        tags(current)
        if (adding && inlineEditing) {
            fields()
            Row {
                TextButton(onClick = { adding = false }) { Text("キャンセル") }
                TextButton(enabled = text.isNotBlank() && !occupied(scope), onClick = ::add) { Text("追加") }
            }
        } else ShogiSecondaryButton(enabled = PositionLabelScope.entries.any { !occupied(it) }, onClick = {
            entries = repository.entries(owner)
            scope = PositionLabelScope.entries.firstOrNull { !occupied(it) } ?: PositionLabelScope.ALL
            text = ""; error = null; adding = true
        }, modifier = Modifier.fillMaxWidth()) { Text("局面ラベルを付ける") }
    }
}

private fun List<StudyNode>.toLines(): List<RepertoireLine> = map { RepertoireLine(it.moveUsi, it.children.toLines()) }
private fun List<RepertoireLine>.toStudyTree(): StudyTree {
    var id = 1L
    fun convert(lines: List<RepertoireLine>): List<StudyNode> = lines.map { StudyNode(id++, it.move, children = convert(it.children)) }
    return StudyTree(rootChildren = convert(this))
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun RepertoirePositionActions(
    repository: RepertoireRepository,
    owner: String,
    sync: RepertoireSync?,
    baseSfen: String,
    moves: List<String>,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var ready by remember(owner) { mutableStateOf(sync == null) }
    LaunchedEffect(owner) {
        try { sync?.synchronize() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { result = "サーバーに接続できませんでした。端末のデータを表示しています。" }
        ready = true
    }
    if (!ready) { CircularProgressIndicator(Modifier.padding(16.dp)); return }
    fun saved() {
        result = "端末に保存しました"
        scope.launch {
            try {
                result = if (sync?.synchronize() == true) "保存しました" else "端末に保存しました。サーバー保存は再試行してください。"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = "端末に保存しました。サーバーに接続できませんでした。" }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        PositionLabelEditor(repository, owner, computeSfenAtStep(baseSfen, moves, moves.size), ::saved, inlineEditing = true)
        HorizontalDivider()
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("定跡名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = name.isNotBlank(), onClick = {
            var line = emptyList<RepertoireLine>()
            for (move in moves.asReversed()) line = listOf(RepertoireLine(move, line))
            repository.save(owner, Uuid.random().toString(), "line", RepertoireCodec.encode(RepertoireDocument(name.trim(), baseSfen, line)))
            name = ""
            saved()
        }) { Text("定跡として登録") }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
