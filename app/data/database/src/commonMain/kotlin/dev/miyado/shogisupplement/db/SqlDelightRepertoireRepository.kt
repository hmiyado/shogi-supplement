package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.repertoire.RepertoireEntry
import dev.miyado.shogisupplement.repertoire.RepertoireRepository

class SqlDelightRepertoireRepository(database: ShogiSupplementDatabase) : RepertoireRepository {
    private val queries = database.repertoireQueries

    override fun entries(owner: String): List<RepertoireEntry> = queries.entries(owner).executeAsList().map {
        RepertoireEntry(it.id, it.kind, it.payload, it.revision, it.remote_version, it.revision != it.uploaded_revision)
    }

    override fun pending(owner: String): List<RepertoireEntry> = queries.transactionWithResult {
        queries.preparePending(owner)
        queries.pending(owner).executeAsList().map {
            RepertoireEntry(it.id, it.kind, it.payload, it.revision, it.remote_version, true, it.upload_token)
        }
    }

    override fun save(owner: String, id: String, kind: String, payload: String) {
        require(owner.isNotBlank() && id.isNotBlank())
        require(kind == "line" || kind == "labels")
        queries.transaction {
            queries.insertEntry(owner, id, kind, payload)
            queries.updateEntry(kind, payload, owner, id)
        }
    }

    override fun compareAndSave(owner: String, id: String, kind: String, expectedPayload: String?, payload: String): Boolean =
        queries.transactionWithResult {
            if (entries(owner).firstOrNull { it.id == id }?.payload != expectedPayload) return@transactionWithResult false
            save(owner, id, kind, payload)
            true
        }

    override fun acknowledge(owner: String, id: String, revision: Long, remoteVersion: String) {
        queries.transaction {
            queries.acknowledge(revision, remoteVersion, owner, id, revision, revision)
            queries.removePending(owner, id, revision)
        }
    }

    override fun acceptRemote(owner: String, id: String, kind: String, payload: String, remoteVersion: String) {
        queries.transaction {
            queries.insertRemote(owner, id, kind, payload, remoteVersion)
            queries.updateRemote(kind, payload, remoteVersion, owner, id)
        }
    }
}
