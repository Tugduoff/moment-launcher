package app.moment.launcher.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.FileProvider
import app.moment.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * "Share my companion": Muse Image puts the companion, dressed as it is now, into a small
 * illustrated scene for the current moment, ready to post.
 */
object Postcard {
    suspend fun make(ctx: Context, outfit: Bitmap?, scene: String, wearing: List<String>): File {
        val png = withContext(Dispatchers.Default) {
            val body = outfit ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.body)
            val out = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(Bitmap.createScaledBitmap(body, 1024, 1024, true), 0f, 0f, null) }
            ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        val prompt = "Turn this into a charming illustrated square postcard. Keep this exact character, same style and colors" +
            (if (wearing.isNotEmpty()) ", still wearing ${wearing.joinToString(", ")}" else "") +
            ". Give it a happy face: two small dark oval eyes with a tiny white highlight and a small smile, like a cute sticker. " +
            "Place it in a soft, cozy scene: $scene. Pastel colors, gentle light, no text, no borders."
        val bytes = MuseClient.editImage(png, prompt)
        return withContext(Dispatchers.IO) {
            File(ctx.cacheDir, "postcards").apply { mkdirs() }.let { dir -> File(dir, "moment_${System.currentTimeMillis()}.png") }
                .also { it.writeBytes(bytes) }
        }
    }

    fun share(ctx: Context, file: File, caption: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share your companion").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
