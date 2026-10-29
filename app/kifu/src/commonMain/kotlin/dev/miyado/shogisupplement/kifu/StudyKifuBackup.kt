package dev.miyado.shogisupplement.kifu

/** 検討文書を暗号化対象だけへ追加し、解析対象の本譜・ヘッダの変更を拒否する。 */
object StudyKifuBackup {
    fun decompose(originalKif: String, studyKif: String?): DecomposedKifu {
        val original = KifuDecomposer.decompose(originalKif, KifParser().parse(originalKif))
        if (studyKif == null) return original
        requireSameGame(originalKif, studyKif)
        return original.copy(private = original.private.copy(studyKif = studyKif, studyOriginalKif = originalKif))
    }

    /** 公開本譜と暗号化された基準原文を照合し、原文と編集文書を別々に復元する。 */
    fun restore(public: PublicKifuFields, privateFields: PrivateKifuFields?): Pair<String, String?> {
        val reconstructed = KifuReconstructor.reconstruct(public, privateFields)
        val original = privateFields?.studyOriginalKif ?: reconstructed
        if (privateFields?.studyOriginalKif != null) {
            val originalPublic = KifuDecomposer.decompose(original, KifParser().parse(original)).public
            require(originalPublic == public) { "Study original does not match the public game" }
        }
        return original to validatedStudy(original, privateFields)
    }

    /** 原文と編集文書を混同せず、復号後も同一棋譜であることを検証する。 */
    fun validatedStudy(originalKif: String, privateFields: PrivateKifuFields?): String? {
        val study = privateFields?.studyKif ?: return null
        requireSameGame(originalKif, study)
        return study
    }

    private fun requireSameGame(originalKif: String, studyKif: String) {
        val original = KifTreeParser().parse(originalKif)
        val study = KifTreeParser().parse(studyKif)
        require(original.headers == study.headers && original.mainLineContent() == study.mainLineContent()) {
            "Study backup changes the original game"
        }
    }
}
