package dev.miyado.shogisupplement.navigation

import java.io.File
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 実行時と同じ遷移表を読み込む。ソースの正規表現解析や別の遷移定義は持たない。 */
fun navigationDiagramHtml(fonts: Map<String, String> = emptyMap()): String {
    val fontStyles = fonts.entries.joinToString("\n") { (family, data) ->
        "@font-face{font-family:'$family';src:url('data:font/ttf;base64,$data') format('truetype')}"
    }
    val nodes = AppDestination.entries.joinToString(",") { "\"${it.name}\"" }
    val metadata = AppDestination.entries.joinToString(",") {
        "{id:\"${it.name}\",label:${jsString(it.label)},group:\"${it.group.name}\",groupLabel:${jsString(it.group.label)},isError:${it.isError}}"
    }
    val edges = NavigationMachine.transitions.mapIndexed { index, transition ->
        val event = when (transition.event) {
            is NavigationEvent.Open -> "Open"
            is NavigationEvent.AnalysisCancelled -> "AnalysisCancelled"
            is NavigationEvent.AnalysisFailed -> "AnalysisFailed"
            NavigationEvent.Back -> "Back"
            NavigationEvent.AnalysisStarted -> "AnalysisStarted"
            NavigationEvent.AnalysisCompleted -> "AnalysisCompleted"
            NavigationEvent.AnalysisClosed -> "AnalysisClosed"
            NavigationEvent.RestoreAuthenticated -> "RestoreAuthenticated"
        }
        "{id:$index,from:\"${transition.from.name}\",to:\"${transition.to.name}\",event:\"$event\",kind:\"${transition.kind.name}\"}"
    }.joinToString(",\n")
    val data = "{nodes:[$nodes],metadata:[$metadata],edges:[$edges]}"
    return resource("diagram.html")
        .replace("{{CSS}}", resource("diagram.css").replace("{{FONTS}}", fontStyles))
        .replace("{{JS}}", resource("diagram.js"))
        .replace("{{DATA}}", data)
}

private fun jsString(value: String): String = Json.encodeToString(value).replace("<", "\\u003c")

private fun resource(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader.getResource("navigation/$name"))
        .readText()

fun main(args: Array<String>) {
    require(args.size == 2) { "Specify the output HTML path and font directory" }
    val output = File(args[0])
    val fonts = mapOf(
        "Shippori Mincho" to "shippori_mincho_bold.ttf",
        "IBM Plex Sans JP" to "ibm_plex_sans_jp_regular.ttf",
        "IBM Plex Mono" to "ibm_plex_mono_regular.ttf",
    ).mapValues { (_, name) -> Base64.getEncoder().encodeToString(File(args[1], name).readBytes()) }
    output.parentFile.mkdirs()
    output.writeText(navigationDiagramHtml(fonts))
}
