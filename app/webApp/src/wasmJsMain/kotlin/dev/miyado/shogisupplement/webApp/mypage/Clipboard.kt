@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp.mypage

@JsFun("(text) => { navigator.clipboard.writeText(text) }")
external fun copyTextToClipboard(text: String)
