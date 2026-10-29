@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp.mypage

import kotlin.js.JsAny
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardShareTest {
    @Test
    fun successfulShareDoesNotWriteClipboard() {
        val fake = fakeShareNavigator("success")
        shareTextWith(fake.navigator(), "url")
        assertEquals(1, fake.shareCalls())
        assertEquals("existing", fake.clipboardWrites())
    }

    @Test
    fun cancelledSharePreservesClipboard() {
        val fake = fakeShareNavigator("cancel")
        shareTextWith(fake.navigator(), "url")
        assertEquals(1, fake.shareCalls())
        assertEquals("existing", fake.clipboardWrites())
    }

    @Test
    fun unavailableShareFallsBackToClipboard() {
        val fake = fakeShareNavigator("unavailable")
        shareTextWith(fake.navigator(), "url")
        assertEquals(0, fake.shareCalls())
        assertEquals("existing,url", fake.clipboardWrites())
    }

    @Test
    fun failedShareFallsBackToClipboard() {
        val fake = fakeShareNavigator("error")
        shareTextWith(fake.navigator(), "url")
        assertEquals(1, fake.shareCalls())
        assertEquals("existing,url", fake.clipboardWrites())
    }
}

private external interface FakeShare : JsAny {
    fun navigator(): JsAny
    fun shareCalls(): Int
    fun clipboardWrites(): String
}

@JsFun("""(mode) => {
    const state = { shareCalls: 0, writes: ['existing'] };
    const nav = {
        clipboard: { writeText: (text) => { state.writes.push(text); } },
    };
    if (mode !== 'unavailable') {
        nav.share = ({ text }) => {
            state.shareCalls += 1;
            if (mode === 'success') return { catch: () => {} };
            const error = mode === 'cancel' ? { name: 'AbortError' } : { name: 'NotAllowedError' };
            return { catch: (handler) => handler(error) };
        };
    }
    return {
        navigator: () => nav,
        shareCalls: () => state.shareCalls,
        clipboardWrites: () => state.writes.join(','),
    };
}""")
private external fun fakeShareNavigator(mode: String): FakeShare
