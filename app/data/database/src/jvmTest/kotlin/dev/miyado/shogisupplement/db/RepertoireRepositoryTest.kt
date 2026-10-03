package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepertoireRepositoryTest {
    @Test fun pendingChangesSurviveReopenAndLateAcknowledgement() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            ShogiSupplementDatabase.Schema.create(driver)
            val repo = SqlDelightRepertoireRepository(ShogiSupplementDatabase(driver))
            repo.save("alice", "one", "line", "v1")
            val first = repo.pending("alice").single()
            repo.save("alice", "one", "line", "v2")
            assertEquals(first, repo.pending("alice").single())
            repo.acknowledge("alice", "one", first.revision, "remote1")
            val reopened = SqlDelightRepertoireRepository(ShogiSupplementDatabase(driver))
            val pending = reopened.entries("alice").single()
            assertEquals("v2", pending.payload)
            assertTrue(pending.dirty)
            assertEquals("remote1", pending.remoteVersion)
            assertEquals(pending.payload, reopened.pending("alice").single().payload)
            assertEquals(pending.revision, reopened.pending("alice").single().revision)
            reopened.acceptRemote("alice", "one", "line", "old", "remote1")
            assertEquals(pending, reopened.entries("alice").single())
            assertTrue(reopened.entries("bob").isEmpty())
            reopened.acknowledge("alice", "one", pending.revision, "remote2")
            assertFalse(reopened.entries("alice").single().dirty)
            reopened.acceptRemote("alice", "one", "line", "other-device", "remote3")
            assertEquals("other-device", reopened.entries("alice").single().payload)
            assertFalse(reopened.compareAndSave("alice", "one", "line", "v2", "stale-editor"))
            assertEquals("other-device", reopened.entries("alice").single().payload)
        }
    }

    @Test fun lostResponseAndLaterEditAreRetriedWithoutLosingEitherRevision() = kotlinx.coroutines.test.runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            ShogiSupplementDatabase.Schema.create(driver)
            val repo = SqlDelightRepertoireRepository(ShogiSupplementDatabase(driver))
            repo.save("alice", "one", "line", "first")
            var stored: dev.miyado.shogisupplement.repertoire.RemoteRepertoireEntry? = null
            var loseResponse = true
            val remote = object : dev.miyado.shogisupplement.repertoire.RepertoireRemote {
                override suspend fun list(owner: String) = listOfNotNull(stored)
                override suspend fun put(owner: String, entry: dev.miyado.shogisupplement.repertoire.RepertoireEntry): String? {
                    val token = requireNotNull(entry.uploadToken)
                    if (stored?.version == token) return token
                    if (stored?.version != entry.remoteVersion) return null
                    stored = dev.miyado.shogisupplement.repertoire.RemoteRepertoireEntry(entry.id, entry.kind, entry.payload, token)
                    if (loseResponse) { loseResponse = false; error("Response lost") }
                    return token
                }
            }
            val firstSync = dev.miyado.shogisupplement.repertoire.RepertoireSync(repo, remote) { "alice" }
            kotlin.test.assertFails { firstSync.synchronize() }
            repo.save("alice", "one", "line", "second")
            val reopened = SqlDelightRepertoireRepository(ShogiSupplementDatabase(driver))
            val retry = dev.miyado.shogisupplement.repertoire.RepertoireSync(reopened, remote) { "alice" }
            assertTrue(retry.synchronize())
            assertEquals("second", stored?.payload)
            assertFalse(reopened.entries("alice").single().dirty)
            assertTrue(reopened.pending("alice").isEmpty())
            SqlDelightGameRepository(ShogiSupplementDatabase(driver)).deleteAllLocalData()
            assertTrue(reopened.entries("alice").isEmpty())
        }
    }

    @Test fun migrationPreservesOldData() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            ShogiSupplementDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE repertoire_outbox", 0)
            driver.execute(null, "DROP TABLE repertoire_entry", 0)
            ShogiSupplementDatabase.Schema.migrate(driver, 25, ShogiSupplementDatabase.Schema.version)
            val repo = SqlDelightRepertoireRepository(ShogiSupplementDatabase(driver))
            repo.save("alice", "one", "labels", "payload")
            assertEquals("payload", repo.entries("alice").single().payload)
        }
    }
}
