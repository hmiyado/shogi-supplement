package dev.miyado.shogisupplement.navigation

enum class NavigationGroup(val label: String) {
    MAIN("対局・学習"),
    SETTINGS("設定・引き継ぎ"),
    KENTO("Web検討"),
    MYPAGE("Webマイページ"),
}

enum class AppDestination(val label: String, val group: NavigationGroup, val isError: Boolean = false) {
    HOME("ホーム", NavigationGroup.MAIN),
    REPERTOIRE("定跡手順", NavigationGroup.MAIN),
    GAME_LIST("棋譜一覧", NavigationGroup.MAIN),
    REPORT("レポート", NavigationGroup.MAIN),
    ANALYZING("解析中", NavigationGroup.MAIN),
    DRILL("次の一手問題", NavigationGroup.MAIN),
    MANUAL_KIFU("棋譜入力", NavigationGroup.MAIN),
    STRENGTH_DETAIL("推定棋力", NavigationGroup.MAIN),
    DRILL_RECORD_DETAIL("学習の記録", NavigationGroup.MAIN),
    SETTINGS("設定", NavigationGroup.SETTINGS),
    LICENSES("ライセンス", NavigationGroup.SETTINGS),
    ACCOUNT("アカウント", NavigationGroup.SETTINGS),
    TRANSFER_CODE("引き継ぎコード", NavigationGroup.SETTINGS),
    GAME_RESTORE("棋譜の復元", NavigationGroup.SETTINGS),
    DEBUG("デバッグ", NavigationGroup.SETTINGS),
    KENTO_INPUT("Web 棋譜入力", NavigationGroup.KENTO),
    KENTO_LIBRARY("保存した棋譜", NavigationGroup.KENTO),
    MYPAGE_LOGIN("ログイン", NavigationGroup.MYPAGE),
    MYPAGE_LOADING("一覧を取得中", NavigationGroup.MYPAGE),
    MYPAGE_GAMES("棋譜一覧", NavigationGroup.MYPAGE),
    MYPAGE_DETAIL_LOADING("詳細を取得中", NavigationGroup.MYPAGE),
    MYPAGE_DETAIL("棋譜詳細", NavigationGroup.MYPAGE),
    MYPAGE_ERROR("エラー", NavigationGroup.MYPAGE, isError = true),
}

enum class NavigationKind { FORWARD, BACK, RETRY, ERROR }

data class NavigationTransition(
    val from: AppDestination,
    val event: NavigationEvent,
    val to: AppDestination,
    val kind: NavigationKind = NavigationKind.FORWARD,
)

sealed interface NavigationEvent {
    data class Open(val destination: AppDestination) : NavigationEvent
    data class ReturnToTab(val tab: RootTab) : NavigationEvent
    data class SelectTab(val tab: RootTab) : NavigationEvent
    data object Back : NavigationEvent
    data object AnalysisStarted : NavigationEvent
    data object AnalysisCompleted : NavigationEvent
    data class AnalysisCancelled(val destination: AppDestination) : NavigationEvent
    data class AnalysisFailed(val destination: AppDestination) : NavigationEvent
    data object AnalysisClosed : NavigationEvent
    data object RestoreAuthenticated : NavigationEvent
}

/** 未定義の操作は現在地を保持し、非同期の解析完了で別画面を奪わない。 */
object NavigationMachine {
    /** 棋譜IDだけでは同じ棋譜の再解析を区別できないため、要求IDも照合する。 */
    fun acceptsAnalysisEvent(current: AppDestination, watchingRequestId: String?, eventRequestId: String?): Boolean =
        current == AppDestination.ANALYZING && eventRequestId != null && watchingRequestId == eventRequestId

    val transitions: List<NavigationTransition> = buildList {
        fun open(from: AppDestination, vararg targets: AppDestination) {
            targets.forEach { add(NavigationTransition(from, NavigationEvent.Open(it), it,
                if (it.isError) NavigationKind.ERROR else NavigationKind.FORWARD)) }
        }
        fun event(from: AppDestination, event: NavigationEvent, to: AppDestination,
            kind: NavigationKind = if (event == NavigationEvent.Back || event == NavigationEvent.AnalysisClosed)
                NavigationKind.BACK else NavigationKind.FORWARD) {
            add(NavigationTransition(from, event, to, kind))
        }
        RootTab.entries.forEach { from ->
            RootTab.entries.forEach { to -> event(from.destination, NavigationEvent.SelectTab(to), to.destination) }
        }
        listOf(AppDestination.REPORT, AppDestination.ANALYZING, AppDestination.DRILL,
            AppDestination.MANUAL_KIFU, AppDestination.SETTINGS, AppDestination.STRENGTH_DETAIL,
            AppDestination.DRILL_RECORD_DETAIL, AppDestination.GAME_RESTORE).forEach { detail ->
            RootTab.entries.forEach { tab -> event(detail, NavigationEvent.ReturnToTab(tab), tab.destination, NavigationKind.BACK) }
        }
        open(AppDestination.HOME,
            AppDestination.GAME_LIST, AppDestination.REPORT, AppDestination.ANALYZING,
            AppDestination.DRILL, AppDestination.SETTINGS, AppDestination.MANUAL_KIFU,
            AppDestination.STRENGTH_DETAIL, AppDestination.DRILL_RECORD_DETAIL, AppDestination.REPERTOIRE)
        open(AppDestination.REPERTOIRE, AppDestination.REPORT, AppDestination.ANALYZING, AppDestination.MANUAL_KIFU)
        open(AppDestination.GAME_LIST, AppDestination.REPORT, AppDestination.ANALYZING, AppDestination.MANUAL_KIFU)
        open(AppDestination.KENTO_INPUT, AppDestination.REPORT, AppDestination.KENTO_LIBRARY)
        open(AppDestination.KENTO_LIBRARY, AppDestination.REPORT)
        event(AppDestination.KENTO_LIBRARY, NavigationEvent.Back, AppDestination.KENTO_INPUT)
        open(AppDestination.SETTINGS, AppDestination.LICENSES, AppDestination.ACCOUNT,
            AppDestination.TRANSFER_CODE, AppDestination.DEBUG)
        listOf(AppDestination.LICENSES, AppDestination.ACCOUNT, AppDestination.TRANSFER_CODE,
            AppDestination.DEBUG).forEach {
            event(it, NavigationEvent.Back, AppDestination.SETTINGS)
        }
        listOf(AppDestination.GAME_LIST, AppDestination.REPORT, AppDestination.ANALYZING,
            AppDestination.DRILL, AppDestination.SETTINGS, AppDestination.MANUAL_KIFU, AppDestination.GAME_RESTORE,
            AppDestination.STRENGTH_DETAIL, AppDestination.DRILL_RECORD_DETAIL, AppDestination.REPERTOIRE).forEach {
            event(it, NavigationEvent.Back, AppDestination.HOME)
        }
        listOf(AppDestination.HOME, AppDestination.GAME_LIST, AppDestination.REPERTOIRE, AppDestination.MANUAL_KIFU, AppDestination.REPORT, AppDestination.KENTO_INPUT).forEach {
            event(it, NavigationEvent.AnalysisStarted, AppDestination.ANALYZING,
                if (it == AppDestination.REPORT) NavigationKind.RETRY else NavigationKind.FORWARD)
        }
        event(AppDestination.ANALYZING, NavigationEvent.AnalysisCompleted, AppDestination.REPORT)
        event(AppDestination.ANALYZING, NavigationEvent.AnalysisClosed, AppDestination.HOME)
        event(AppDestination.SETTINGS, NavigationEvent.RestoreAuthenticated, AppDestination.GAME_RESTORE)
        listOf(AppDestination.KENTO_INPUT, AppDestination.REPORT).forEach {
            event(AppDestination.ANALYZING, NavigationEvent.AnalysisCancelled(it), it, NavigationKind.BACK)
            event(AppDestination.ANALYZING, NavigationEvent.AnalysisFailed(it), it, NavigationKind.ERROR)
        }
        val myPage = listOf(AppDestination.MYPAGE_LOGIN, AppDestination.MYPAGE_LOADING,
            AppDestination.MYPAGE_GAMES, AppDestination.MYPAGE_DETAIL_LOADING,
            AppDestination.MYPAGE_DETAIL, AppDestination.MYPAGE_ERROR)
        myPage.forEach {
            event(it, NavigationEvent.Open(AppDestination.MYPAGE_LOGIN), AppDestination.MYPAGE_LOGIN, NavigationKind.BACK)
            event(it, NavigationEvent.Open(AppDestination.MYPAGE_LOADING), AppDestination.MYPAGE_LOADING,
                if (it == AppDestination.MYPAGE_LOGIN) NavigationKind.FORWARD else NavigationKind.RETRY)
            event(it, NavigationEvent.Open(AppDestination.MYPAGE_ERROR), AppDestination.MYPAGE_ERROR, NavigationKind.ERROR)
        }
        open(AppDestination.MYPAGE_LOADING, AppDestination.MYPAGE_GAMES)
        open(AppDestination.MYPAGE_GAMES, AppDestination.MYPAGE_DETAIL_LOADING)
        open(AppDestination.MYPAGE_DETAIL_LOADING, AppDestination.MYPAGE_DETAIL)
        listOf(AppDestination.MYPAGE_DETAIL, AppDestination.MYPAGE_ERROR).forEach {
            event(it, NavigationEvent.Back, AppDestination.MYPAGE_GAMES)
        }
    }

    /** nullは拒否。同じ種類の画面への未定義操作も許可と取り違えない。 */
    fun resolve(current: AppDestination, event: NavigationEvent): AppDestination? =
        transitions.firstOrNull { it.from == current && it.event == event }?.to

    fun next(current: AppDestination, event: NavigationEvent): AppDestination =
        resolve(current, event) ?: current
}
