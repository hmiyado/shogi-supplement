@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp

internal external interface BrowserLeaveGuard : JsAny {
    fun dispose()
}

/** タブを閉じる・再読込・別ページへのリンクはブラウザ標準の離脱確認を使う。 */
@JsFun("""(isDirty, eventTarget) => {
    const target = eventTarget || window;
    const listener = event => {
        if (isDirty()) { event.preventDefault(); event.returnValue = ''; }
    };
    target.addEventListener('beforeunload', listener);
    return { dispose: () => target.removeEventListener('beforeunload', listener) };
}""")
internal external fun installBrowserLeaveGuard(isDirty: () -> Boolean, eventTarget: JsAny?): BrowserLeaveGuard
