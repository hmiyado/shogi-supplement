@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp.mypage

import kotlin.js.JsAny

@JsFun("(text) => { navigator.clipboard.writeText(text) }")
external fun copyTextToClipboard(text: String)

@JsFun("() => navigator")
private external fun browserNavigator(): JsAny

fun shareText(text: String) {
    shareTextWith(browserNavigator(), text)
}

@JsFun("""(nav, text) => {
    const fallback = (error) => {
        if (error && error.name === 'AbortError') return;
        if (nav.clipboard && nav.clipboard.writeText) nav.clipboard.writeText(text);
    };
    if (!nav.share) {
        fallback();
        return;
    }
    try {
        const result = nav.share({ text });
        if (result && result.catch) result.catch(fallback);
    } catch (error) {
        fallback(error);
    }
}""")
internal external fun shareTextWith(nav: JsAny, text: String)
