package dev.miyado.shogisupplement.crypto

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// このアプリの他のKeychain項目と衝突しないよう専用のservice文字列で名前空間を切る。
private const val KEYCHAIN_SERVICE = "dev.miyado.shogisupplement.transfer_secret"
private const val KEYCHAIN_ACCOUNT = "S"

/**
 * Sの永続化＝iOS Keychain（kSecClassGenericPassword）。純粋なCoreFoundation API
 * （CFDictionary/CFString/CFData）だけで組み立てる。
 *
 * Why not NSMutableDictionary/NSData経由: kSec*定数はCFStringRef（COpaquePointer）で
 * あり、Foundation（NSMutableDictionary等）を経由するとObjC⇄CFのブリッジング
 * （CFBridgingRetain/Release・NSCopyingProtocol周り）で型が合わず遠回りになる。
 * Security APIの引数型（CFDictionaryRef等）とそのままそろうCore Foundation API
 * だけで完結させたほうが単純で壊れにくい。
 *
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` を使う理由:
 * - `ThisDeviceOnly`: iCloud Keychain同期の対象外にする（設計書付録「後付け可能なUX改善」で
 *   同期は将来のオプトインとして扱っており、既定でオンにはしない）
 * - `AfterFirstUnlock`（`WhenUnlocked`ではなく）: 自動アップロード（解析完了後の
 *   バックグラウンド寄りの処理）からKeychainを読む可能性があり、端末ロック中でも
 *   （初回アンロック後なら）読めておく必要がある
 */
@OptIn(ExperimentalForeignApi::class)
class IosTransferSecretStore : TransferSecretStore {

    override suspend fun load(): ByteArray? = memScoped {
        val query = baseQuery()
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val out = alloc<COpaquePointerVar>()
        val status = SecItemCopyMatching(query, out.ptr.reinterpret())
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val data: CFDataRef? = out.value?.rawValue?.let { interpretCPointer(it) }
        if (data == null) return@memScoped null
        val bytes = cfDataToByteArray(data)
        CFRelease(data)
        bytes
    }

    override suspend fun save(secret: ByteArray) {
        val existing = load()
        val data = byteArrayToCFData(secret)
        if (existing != null) {
            val query = baseQuery()
            val update = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                1,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )
            CFDictionarySetValue(update, kSecValueData, data)
            SecItemUpdate(query, update)
            CFRelease(query)
            CFRelease(update)
        } else {
            val query = baseQuery()
            CFDictionarySetValue(query, kSecValueData, data)
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            SecItemAdd(query, null)
            CFRelease(query)
        }
        CFRelease(data)
    }

    override suspend fun clear() {
        val query = baseQuery()
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun baseQuery(): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            4,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(dict, kSecAttrService, cfString(KEYCHAIN_SERVICE))
        CFDictionarySetValue(dict, kSecAttrAccount, cfString(KEYCHAIN_ACCOUNT))
        return dict
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfString(value: String): platform.CoreFoundation.CFStringRef? = memScoped {
    CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
}

@OptIn(ExperimentalForeignApi::class)
private fun byteArrayToCFData(bytes: ByteArray): CFDataRef? = bytes.usePinned { pinned ->
    CFDataCreate(
        kCFAllocatorDefault,
        if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret(),
        bytes.size.toLong(),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun cfDataToByteArray(data: CFDataRef): ByteArray {
    val length = CFDataGetLength(data).toInt()
    if (length == 0) return ByteArray(0)
    val bytePtr = CFDataGetBytePtr(data) ?: return ByteArray(0)
    return bytePtr.readBytes(length)
}
