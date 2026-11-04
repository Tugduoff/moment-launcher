package app.moment.launcher.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backgrounds the companion paints with Muse Image: one soft wallpaper per context, made the
 * first time that context is used (or when you ask for one). Kept on the phone.
 */
object Backdrops {
    private fun file(ctx: Context, key: String) = File(ctx.filesDir, "backdrop_$key.png")

    fun load(ctx: Context, key: String): Bitmap? = file(ctx, key).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun remove(ctx: Context, key: String) { file(ctx, key).delete() }

    /** One of your own photos as the background: scaled down to screen size and kept on the phone. */
    suspend fun fromPhoto(ctx: Context, key: String, uri: android.net.Uri): Bitmap = withContext(Dispatchers.IO) {
        val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val scale = minOf(1f, 1600f / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        file(ctx, key).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp
    }

    fun clear(ctx: Context) = ctx.filesDir.listFiles { f -> f.name.startsWith("backdrop_") }?.forEach { it.delete() }

    suspend fun paint(ctx: Context, key: String, subject: String): Bitmap {
        val prompt = "A calm phone wallpaper: $subject. Soft painterly illustration, pastel colors, low contrast, " +
            "gentle light, lots of quiet empty space at the top and bottom, no text, no letters, no people, no characters, no logos."
        val bytes = MuseClient.image(prompt)
        return withContext(Dispatchers.IO) {
            file(ctx, key).writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}
