package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse

class StudyKifuBackupTest {
    private val original = "先手：秘密の対局者\n1 ７六歩(77)\n2 投了"
    private val study = "先手：秘密の対局者\n1 ７六歩(77)\n*秘密のメモ\n&しおり\n2 投了\n\n変化：1手\n1 ２六歩(27)\n*分岐の秘密"

    @Test
    fun originalAndStudyRemainSeparateAndPublicFieldsStayUnchanged() {
        val without = StudyKifuBackup.decompose(original, null)
        val with = StudyKifuBackup.decompose(original, study)
        assertEquals(without.public, with.public)
        assertEquals(without.private, with.private.copy(studyKif = null, studyOriginalKif = null))
        val decoded = PrivateKifuFields.fromJson(with.private.toJson())
        assertEquals(study, StudyKifuBackup.validatedStudy(original, decoded))
        assertEquals(study, StudyKifuBackup.validatedStudy(KifuReconstructor.reconstruct(with.public, decoded), decoded))
        assertEquals(original to study, StudyKifuBackup.restore(with.public, decoded))
        assertFalse(KifuReconstructor.reconstruct(with.public, decoded).contains("秘密のメモ"))
        assertFalse(KifuReconstructor.reconstruct(with.public, null).contains("秘密"))
    }

    @Test
    fun oldBackupsHaveNoStudyAndDifferentGameIsRejected() {
        val old = StudyKifuBackup.decompose(original, null).private
        assertNull(PrivateKifuFields.fromJson(old.toJson()).studyKif)
        assertNull(StudyKifuBackup.validatedStudy(original, null))
        assertFailsWith<IllegalArgumentException> { StudyKifuBackup.decompose(original, study.replace("７六歩(77)", "２六歩(27)")) }
        assertFailsWith<IllegalArgumentException> { StudyKifuBackup.validatedStudy(original, old.copy(studyKif = study.replace("秘密の対局者", "別人"))) }
    }

    @Test
    fun restoresExactTimestampsAndTerminalTimeWithoutChangingPublicProjection() {
        val raw = "開始日時：2026/09/19 12:34:56\n1 ７六歩(77) (0:02/00:00:02)\n2 投了 (0:03/00:00:03)"
        val edited = raw.replace("2 投了", "*保存メモ\n2 投了")
        val parts = StudyKifuBackup.decompose(raw, edited)
        val decoded = PrivateKifuFields.fromJson(parts.private.toJson())
        assertEquals(raw to edited, StudyKifuBackup.restore(parts.public, decoded))
        assertFailsWith<IllegalArgumentException> {
            StudyKifuBackup.restore(parts.public.copy(movesUsi = listOf("2g2f")), decoded)
        }
    }
}
