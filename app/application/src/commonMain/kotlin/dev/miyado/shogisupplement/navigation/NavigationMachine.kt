package dev.miyado.shogisupplement.navigation

enum class AppDestination {
    HOME, GAME_LIST, REPORT, ANALYZING, DRILL, SETTINGS, LICENSES,
    ACCOUNT, TRANSFER_CODE, GAME_RESTORE, MANUAL_KIFU, DEBUG,
    STRENGTH_DETAIL, DRILL_RECORD_DETAIL, KENTO_INPUT, KENTO_LIBRARY,
}

data class NavigationTransition(
    val from: AppDestination,
    val event: NavigationEvent,
    val to: AppDestination,
)

sealed interface NavigationEvent {
    data class Open(val destination: AppDestination) : NavigationEvent
    data object Back : NavigationEvent
    data object AnalysisStarted : NavigationEvent
    data object AnalysisCompleted : NavigationEvent
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
            targets.forEach { add(NavigationTransition(from, NavigationEvent.Open(it), it)) }
        }
        fun event(from: AppDestination, event: NavigationEvent, to: AppDestination) {
            add(NavigationTransition(from, event, to))
        }
        open(AppDestination.HOME,
            AppDestination.GAME_LIST, AppDestination.REPORT, AppDestination.ANALYZING,
            AppDestination.DRILL, AppDestination.SETTINGS, AppDestination.MANUAL_KIFU,
            AppDestination.STRENGTH_DETAIL, AppDestination.DRILL_RECORD_DETAIL)
        open(AppDestination.GAME_LIST, AppDestination.REPORT, AppDestination.ANALYZING)
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
            AppDestination.STRENGTH_DETAIL, AppDestination.DRILL_RECORD_DETAIL).forEach {
            event(it, NavigationEvent.Back, AppDestination.HOME)
        }
        listOf(AppDestination.HOME, AppDestination.MANUAL_KIFU, AppDestination.REPORT, AppDestination.KENTO_INPUT).forEach {
            event(it, NavigationEvent.AnalysisStarted, AppDestination.ANALYZING)
        }
        event(AppDestination.ANALYZING, NavigationEvent.AnalysisCompleted, AppDestination.REPORT)
        event(AppDestination.ANALYZING, NavigationEvent.AnalysisClosed, AppDestination.HOME)
        event(AppDestination.SETTINGS, NavigationEvent.RestoreAuthenticated, AppDestination.GAME_RESTORE)
    }

    /** nullは拒否。同じ種類の画面への未定義操作も許可と取り違えない。 */
    fun resolve(current: AppDestination, event: NavigationEvent): AppDestination? =
        transitions.firstOrNull { it.from == current && it.event == event }?.to

    fun next(current: AppDestination, event: NavigationEvent): AppDestination =
        resolve(current, event) ?: current
}
