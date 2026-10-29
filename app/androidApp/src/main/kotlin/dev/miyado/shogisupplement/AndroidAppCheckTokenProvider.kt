package dev.miyado.shogisupplement

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class AndroidAppCheckSettings(
    val applicationId: String,
    val apiKey: String,
    val projectId: String,
) {
    companion object {
        fun from(applicationId: String, apiKey: String, projectId: String): AndroidAppCheckSettings? {
            if (applicationId.isBlank() || apiKey.isBlank() || projectId.isBlank()) return null
            return AndroidAppCheckSettings(applicationId, apiKey, projectId)
        }
    }
}

class AndroidAppCheckTokenProvider(
    context: Context,
    private val settings: AndroidAppCheckSettings,
) {
    private val appContext = context.applicationContext
    private var appCheck: FirebaseAppCheck? = null

    suspend fun token(): String? {
        val check = runCatching { initializedAppCheck() }.getOrNull() ?: return null
        return suspendCancellableCoroutine { continuation ->
            check.getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.token)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    @Synchronized
    private fun initializedAppCheck(): FirebaseAppCheck {
        appCheck?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApplicationId(settings.applicationId)
            .setApiKey(settings.apiKey)
            .setProjectId(settings.projectId)
            .build()
        val app = checkNotNull(FirebaseApp.initializeApp(appContext, options))
        return FirebaseAppCheck.getInstance(app).also {
            it.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            appCheck = it
        }
    }
}
