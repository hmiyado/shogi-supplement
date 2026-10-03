package dev.miyado.shogisupplement.repertoire

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RepertoireSync(
    private val repository: RepertoireRepository,
    private val remote: RepertoireRemote,
    private val currentOwner: () -> String?,
) {
    private val mutex = Mutex()

    suspend fun synchronize(): Boolean = mutex.withLock {
        val owner = currentOwner() ?: return@withLock false
        var success = true
        val conflicts = mutableSetOf<String>()
        repeat(2) {
            for (entry in repository.pending(owner).filter { it.id !in conflicts }) {
                if (currentOwner() != owner) return@withLock false
                val version = remote.put(owner, entry)
                if (currentOwner() != owner) return@withLock false
                if (version == null) { success = false; conflicts.add(entry.id) }
                else repository.acknowledge(owner, entry.id, entry.revision, version)
            }
        }
        if (currentOwner() != owner) return@withLock false
        val downloaded = remote.list(owner)
        if (currentOwner() != owner) return@withLock false
        for (entry in downloaded) repository.acceptRemote(owner, entry.id, entry.kind, entry.payload, entry.version)
        success && repository.entries(owner).none { it.dirty }
    }
}
