package dev.miyado.shogisupplement

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import java.io.File

fun shareScreen(context: Context, view: View) {
    val bitmap = view.drawToBitmap(Bitmap.Config.ARGB_8888)
    val directory = File(context.cacheDir, "shared-screenshots").apply { mkdirs() }
    val image = File(directory, "shogi-supplement.png")
    image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ),
    )
}
