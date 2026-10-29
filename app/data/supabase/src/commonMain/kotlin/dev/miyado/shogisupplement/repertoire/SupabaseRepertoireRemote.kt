package dev.miyado.shogisupplement.repertoire

import dev.miyado.shogisupplement.crypto.PrivateEncCodec
import dev.miyado.shogisupplement.crypto.TransferSecretKeys
import dev.miyado.shogisupplement.crypto.TransferSecrets
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

class SupabaseRepertoireRemote(
    private val client: SupabaseClient,
    private val secretStore: TransferSecretStore,
) : RepertoireRemote {
    @Serializable
    private data class Row(val id: String, val kind: String, val version: String, @SerialName("payload_enc") val payload: String)

    private suspend fun key(): ByteArray {
        val stored = requireNotNull(secretStore.load()) { "引き継ぎコードがありません" }
        val secrets = requireNotNull(TransferSecrets.fromStored(stored))
        return TransferSecretKeys.deriveEncKey(secrets.encSecret)
    }

    override suspend fun list(owner: String): List<RemoteRepertoireEntry> {
        val rows = mutableListOf<Row>()
        var offset = 0L
        do {
            val page = client.from("repertoire_entries").select {
                filter { eq("user_id", owner) }
                order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                range(offset, offset + 499)
            }.decodeList<Row>()
            rows.addAll(page)
            offset += page.size
        } while (page.size == 500)
        if (rows.isEmpty()) return emptyList()
        val key = key()
        return rows.map { row ->
            val plaintext = PrivateEncCodec.decryptBytes(key, Base64.decode(row.payload), aad(owner, row.id, row.kind))
            RemoteRepertoireEntry(row.id, row.kind, plaintext.decodeToString(), row.version)
        }
    }

    override suspend fun put(owner: String, entry: RepertoireEntry): String? {
        val version = requireNotNull(entry.uploadToken)
        val encrypted = PrivateEncCodec.encryptBytes(key(), entry.payload.encodeToByteArray(), aad(owner, entry.id, entry.kind))
        val applied = client.postgrest.rpc("put_repertoire_entry", buildJsonObject {
            put("p_owner", owner)
            put("p_id", entry.id)
            put("p_kind", entry.kind)
            put("p_expected_version", entry.remoteVersion)
            put("p_version", version)
            put("p_payload_enc", Base64.encode(encrypted))
        }).decodeAs<Boolean>()
        return version.takeIf { applied }
    }

    private fun aad(owner: String, id: String, kind: String): ByteArray = "repertoire:v1:$owner:$kind:$id".encodeToByteArray()
}
