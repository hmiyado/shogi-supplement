package dev.miyado.shogisupplement.ui.repertoire

import dev.miyado.shogisupplement.ui.text.title

import org.jetbrains.compose.resources.stringResource

import org.jetbrains.compose.resources.vectorResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import dev.miyado.shogisupplement.navigation.AppDestination
import dev.miyado.shogisupplement.navigation.NavigationEvent
import dev.miyado.shogisupplement.navigation.NavigationMachine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val labelRepertoireSyncPending = stringResource(Res.string.repertoire_sync_pending)
    val labelRepertoireSyncOffline = stringResource(Res.string.repertoire_sync_offline)
    val labelRepertoireDeleteTitle = stringResource(Res.string.repertoire_delete_title)
    val labelRepertoireDeleteDescription = stringResource(Res.string.repertoire_delete_description)
    val labelRepertoireDeleteConflict = stringResource(Res.string.repertoire_delete_conflict)
    val labelGameListSavedFilterDelete = stringResource(Res.string.game_list_saved_filter_delete)
    val labelKentoCancelButton = stringResource(Res.string.kento_cancel_button)
    val labelRepertoireListTitle = stringResource(Res.string.repertoire_list_title)
    val labelSelectionFinish = stringResource(Res.string.selection_finish)
    val labelBack = stringResource(Res.string.back)
    val labelRepertoireDelete = stringResource(Res.string.repertoire_delete)
    val labelRepertoireSelectDelete = stringResource(Res.string.repertoire_select_delete)
    val labelRepertoireAdd = stringResource(Res.string.repertoire_add)
    val labelRepertoireAccountRequired = stringResource(Res.string.repertoire_account_required)

    val scope = rememberCoroutineScope()
    var entries by remember(owner) { mutableStateOf(owner?.let(repository::entries).orEmpty()) }
    var selected by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var destination by rememberSaveable(owner) { mutableStateOf(AppDestination.REPERTOIRE) }
    fun openStudy(id: String) {
        destination = NavigationMachine.next(destination, NavigationEvent.Open(AppDestination.REPERTOIRE_STUDY))
        if (destination == AppDestination.REPERTOIRE_STUDY) selected = id
    }
    var selectionMode by remember(owner) { mutableStateOf(false) }
    var deleteIds by remember(owner) { mutableStateOf(setOf<String>()) }
    var deleteTargets by remember(owner) { mutableStateOf<List<RepertoireEntry>>(emptyList()) }
    val lines = entries.filter { it.kind == "line" && !RepertoireCodec.document(it.payload).deleted }
    var message by remember { mutableStateOf<String?>(null) }
    fun refresh() { entries = owner?.let(repository::entries).orEmpty() }
    fun synchronize(notice: String? = null) {
        scope.launch {
            try {
                if (sync?.synchronize() == false) message = listOfNotNull(notice, labelRepertoireSyncPending).joinToString("\n")
                else message = notice
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = listOfNotNull(notice, labelRepertoireSyncOffline).joinToString("\n") }
        }
    }
    LaunchedEffect(owner) { synchronize() }
    val detailChanged = dev.miyado.shogisupplement.ui.navigation.LocalRootDetailChanged.current
    SideEffect { detailChanged(selected != null) }
    DisposableEffect(Unit) { onDispose { detailChanged(false) } }
    val id = selected
    if (destination == AppDestination.REPERTOIRE_STUDY && id != null && owner != null) {
        val entry = entries.firstOrNull { it.id == id }
        var savedPayload by remember(id) { mutableStateOf(entry?.payload) }
        val document = remember(id) { entry?.let { RepertoireCodec.document(it.payload) }
            ?: RepertoireDocument("", ShogiBoard().toSfen()) }
        RepertoireStudyScreen(
            document = document,
            evalDisplay = evalDisplay,
            engineFactory = engineFactory,
            onBack = {
                destination = NavigationMachine.next(destination, NavigationEvent.Back)
                if (destination == AppDestination.REPERTOIRE) { selected = null; refresh() }
            },
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
        title = { Text(labelRepertoireDeleteTitle) },
        text = { Text(labelRepertoireDeleteDescription) },
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
            synchronize(if (conflict) labelRepertoireDeleteConflict else null)
        }) { Text(labelGameListSavedFilterDelete, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteTargets = emptyList() }) { Text(labelKentoCancelButton) } },
    )
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = if (dev.miyado.shogisupplement.ui.navigation.rootTabsOverlayContent) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface), title = { Text(if (selectionMode) stringResource(Res.string.text_game_list_selected_count, deleteIds.size) else labelRepertoireListTitle) }, navigationIcon = {
            if (selectionMode) IconButton(onClick = { exitSelection() }) { Icon(vectorResource(Res.drawable.ic_filled_close), labelSelectionFinish) }
            else if (!dev.miyado.shogisupplement.ui.navigation.LocalRootTabHost.current) IconButton(onClick = onBack) { Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back), labelBack) }
        }, actions = {
            if (lines.isNotEmpty()) IconButton(
                onClick = { if (selectionMode) deleteTargets = lines.filter { it.id in deleteIds } else selectionMode = true },
                enabled = !selectionMode || deleteIds.isNotEmpty(),
            ) { Icon(vectorResource(Res.drawable.ic_outlined_delete), if (selectionMode) labelRepertoireDelete else labelRepertoireSelectDelete, tint = MaterialTheme.colorScheme.error) }
        }) },
        bottomBar = {
            Button(onClick = { openStudy(Uuid.random().toString()) }, enabled = owner != null,
                modifier = Modifier.fillMaxWidth().padding(bottom = dev.miyado.shogisupplement.ui.navigation.LocalRootTabBottomPadding.current).padding(16.dp), shape = MaterialTheme.shapes.medium) { Text(labelRepertoireAdd) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).adaptiveContentWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (owner == null) item { Text(labelRepertoireAccountRequired) }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(lines, key = { it.id }) { entry ->
                val doc = RepertoireCodec.document(entry.payload)
                Card(onClick = { if (selectionMode) deleteIds = if (entry.id in deleteIds) deleteIds - entry.id else deleteIds + entry.id else openStudy(entry.id) }, modifier = Modifier.fillMaxWidth(),
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
    val labelRepertoireSaveFailed = stringResource(Res.string.repertoire_save_failed)
    val labelRepertoireDiscardTitle = stringResource(Res.string.repertoire_discard_title)
    val labelDiscardAndClose = stringResource(Res.string.discard_and_close)
    val labelRepertoireSave = stringResource(Res.string.repertoire_save)
    val labelRepertoireName = stringResource(Res.string.repertoire_name)
    val labelDebugWasmSiteSave = stringResource(Res.string.debug_wasm_site_save)
    val labelKentoCancelButton = stringResource(Res.string.kento_cancel_button)
    val labelRepertoireTitle = stringResource(Res.string.repertoire_title)
    val labelBack = stringResource(Res.string.back)
    val labelBoardFlip = stringResource(Res.string.board_flip)
    val labelPositionLabelTitle = stringResource(Res.string.position_label_title)
    val labelReportTabStudy = stringResource(Res.string.report_tab_study)
    val labelRepertoireNew = stringResource(Res.string.repertoire_new)
    val labelRepertoireRegister = stringResource(Res.string.repertoire_register)
    val labelRepertoireRename = stringResource(Res.string.repertoire_rename)
    val labelInitialPositionCreate = stringResource(Res.string.initial_position_create)

    val scope = rememberCoroutineScope()
    var draftPayload by rememberSaveable { mutableStateOf(RepertoireCodec.encode(document)) }
    val initialDocument = remember { RepertoireCodec.document(draftPayload) }
    var base by rememberSaveable { mutableStateOf(initialDocument.initialSfen) }
    var name by rememberSaveable { mutableStateOf(initialDocument.name) }
    var nameInput by rememberSaveable { mutableStateOf(initialDocument.name) }
    var flip by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(AppDestination.REPERTOIRE_STUDY) }
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
        saveError = labelRepertoireSaveFailed
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
        onDismissRequest = { discard = false }, title = { Text(labelRepertoireDiscardTitle) },
        confirmButton = { TextButton(onClick = onBack) { Text(labelDiscardAndClose) } },
        dismissButton = { TextButton(onClick = { discard = false; naming = true }) { Text(labelRepertoireSave) } },
    )
    if (destination == AppDestination.REPERTOIRE_INITIAL_POSITION) {
        InitialPositionEditorScreen(base, state?.displayLine?.isNotEmpty() == true, initialFlip = flip,
            onClose = { destination = NavigationMachine.next(destination, NavigationEvent.Back) }, onApply = { sfen ->
                if (sfen != dev.miyado.shogisupplement.board.InitialPositionEditor.fromSfen(base).toSfen()) { base = sfen; start(sfen, emptyList()); save() }
                destination = NavigationMachine.next(destination, NavigationEvent.Back)
            })
        return
    }
    if (naming) AlertDialog(
        onDismissRequest = { naming = false }, title = { Text(labelRepertoireName) },
        text = { OutlinedTextField(value = nameInput, onValueChange = { nameInput = it.take(100) }, singleLine = true) },
        confirmButton = { TextButton(enabled = nameInput.isNotBlank(), onClick = { name = nameInput.trim(); save(); naming = false }) { Text(labelDebugWasmSiteSave) } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text(labelKentoCancelButton) } },
    )
    ReportBackHandler(onBack = ::leave)
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = { TopAppBar(title = { Text(labelRepertoireTitle) }, navigationIcon = {
            IconButton(onClick = ::leave) { Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back), labelBack) }
        }, actions = {
            TextButton(onClick = { flip = !flip }) { Text(labelBoardFlip) }

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
            BoardTabRow(listOf(labelRepertoireTitle, labelPositionLabelTitle, labelReportTabStudy), tab, { tab = it })
            when (tab) {
                0 -> Column(Modifier.padding(16.dp)) {
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(name.ifEmpty { labelRepertoireNew }, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { nameInput = name; naming = true }) { Text(if (name.isEmpty()) labelRepertoireRegister else labelRepertoireRename) }
                    TextButton(onClick = { destination = NavigationMachine.next(destination, NavigationEvent.Open(AppDestination.REPERTOIRE_INITIAL_POSITION)) }) { Text(labelInitialPositionCreate) }
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
    val labelPositionLabelOccupied = stringResource(Res.string.position_label_occupied)
    val labelRepertoireSaveFailed = stringResource(Res.string.repertoire_save_failed)
    val labelPositionLabelName = stringResource(Res.string.position_label_name)
    val labelPositionLabelDeleteTitle = stringResource(Res.string.position_label_delete_title)
    val labelPositionLabelUpdateConflict = stringResource(Res.string.position_label_update_conflict)
    val labelPositionLabelDeleteFailed = stringResource(Res.string.position_label_delete_failed)
    val labelGameDeleteConfirm = stringResource(Res.string.game_delete_confirm)
    val labelKentoCancelButton = stringResource(Res.string.kento_cancel_button)
    val labelPositionLabelAdd = stringResource(Res.string.position_label_add)
    val labelAdd = stringResource(Res.string.add)
    val labelPositionLabelGameLabels = stringResource(Res.string.position_label_game_labels)
    val labelPositionLabelCurrentLabels = stringResource(Res.string.position_label_current_labels)

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
            } else { entries = repository.entries(owner); error = labelPositionLabelOccupied }
        } catch (_: Exception) { error = labelRepertoireSaveFailed }
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
                                Icon(vectorResource(Res.drawable.ic_filled_close), stringResource(Res.string.position_label_delete_description, display), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                label = { Text(labelPositionLabelName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    removing?.let { (entry, name) ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(labelPositionLabelDeleteTitle) },
            text = { Column {
                Text(stringResource(Res.string.position_label_delete_confirmation, name))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                try {
                    if (repository.removePositionLabel(owner, entry, name)) { removing = null; error = null; refresh() }
                    else { error = labelPositionLabelUpdateConflict; entries = repository.entries(owner) }
                } catch (_: Exception) { error = labelPositionLabelDeleteFailed }
            }) { Text(labelGameDeleteConfirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(labelKentoCancelButton) } })
    }
    if (adding && !inlineEditing) ModalBottomSheet(onDismissRequest = { adding = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(labelPositionLabelAdd, style = MaterialTheme.typography.titleLarge)
            fields()
            Row {
                TextButton(onClick = { adding = false }) { Text(labelKentoCancelButton) }
                TextButton(enabled = text.isNotBlank() && !occupied(scope), onClick = ::add) { Text(labelAdd) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (document != null) {
            Text(labelPositionLabelGameLabels, style = MaterialTheme.typography.bodySmall)
            tags(document.labelEntries(entries))
            HorizontalDivider()
        }
        Text(labelPositionLabelCurrentLabels, style = MaterialTheme.typography.bodySmall)
        tags(current)
        if (adding && inlineEditing) {
            fields()
            Row {
                TextButton(onClick = { adding = false }) { Text(labelKentoCancelButton) }
                TextButton(enabled = text.isNotBlank() && !occupied(scope), onClick = ::add) { Text(labelAdd) }
            }
        } else ShogiSecondaryButton(enabled = PositionLabelScope.entries.any { !occupied(it) }, onClick = {
            entries = repository.entries(owner)
            scope = PositionLabelScope.entries.firstOrNull { !occupied(it) } ?: PositionLabelScope.ALL
            text = ""; error = null; adding = true
        }, modifier = Modifier.fillMaxWidth()) { Text(labelPositionLabelAdd) }
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
    val labelRepertoireLoadOffline = stringResource(Res.string.repertoire_load_offline)
    val labelStudySaved = stringResource(Res.string.study_saved)
    val labelRepertoireSaved = stringResource(Res.string.repertoire_saved)
    val labelRepertoireSavedRetrySync = stringResource(Res.string.repertoire_saved_retry_sync)
    val labelRepertoireSavedOffline = stringResource(Res.string.repertoire_saved_offline)
    val labelRepertoireName = stringResource(Res.string.repertoire_name)
    val labelRepertoireRegister = stringResource(Res.string.repertoire_register)

    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var ready by remember(owner) { mutableStateOf(sync == null) }
    LaunchedEffect(owner) {
        try { sync?.synchronize() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { result = labelRepertoireLoadOffline }
        ready = true
    }
    if (!ready) { CircularProgressIndicator(Modifier.padding(16.dp)); return }
    fun saved() {
        result = labelStudySaved
        scope.launch {
            try {
                result = if (sync?.synchronize() == true) labelRepertoireSaved else labelRepertoireSavedRetrySync
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = labelRepertoireSavedOffline }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        PositionLabelEditor(repository, owner, computeSfenAtStep(baseSfen, moves, moves.size), ::saved, inlineEditing = true)
        HorizontalDivider()
        OutlinedTextField(name, { name = it.take(100) }, label = { Text(labelRepertoireName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = name.isNotBlank(), onClick = {
            var line = emptyList<RepertoireLine>()
            for (move in moves.asReversed()) line = listOf(RepertoireLine(move, line))
            repository.save(owner, Uuid.random().toString(), "line", RepertoireCodec.encode(RepertoireDocument(name.trim(), baseSfen, line)))
            name = ""
            saved()
        }) { Text(labelRepertoireRegister) }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
