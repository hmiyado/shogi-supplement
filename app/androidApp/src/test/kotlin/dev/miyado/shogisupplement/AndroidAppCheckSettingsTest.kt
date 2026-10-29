package dev.miyado.shogisupplement

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AndroidAppCheckSettingsTest {
    @Test
    fun buildsSettingsWhenAllPublicIdentifiersArePresent() {
        val settings = AndroidAppCheckSettings.from(
            applicationId = "1:123456789012:android:abcdef",
            apiKey = "AIzaSyExample",
            projectId = "shogi-supplement",
        )

        assertEquals("1:123456789012:android:abcdef", settings?.applicationId)
        assertEquals("AIzaSyExample", settings?.apiKey)
        assertEquals("shogi-supplement", settings?.projectId)
    }

    @Test
    fun rejectsMissingPublicIdentifiers() {
        assertNull(AndroidAppCheckSettings.from("", "AIzaSyExample", "shogi-supplement"))
        assertNull(AndroidAppCheckSettings.from("1:123456789012:android:abcdef", "", "shogi-supplement"))
        assertNull(AndroidAppCheckSettings.from("1:123456789012:android:abcdef", "AIzaSyExample", ""))
        assertNull(AndroidAppCheckSettings.from(" ", "AIzaSyExample", "shogi-supplement"))
    }
}
