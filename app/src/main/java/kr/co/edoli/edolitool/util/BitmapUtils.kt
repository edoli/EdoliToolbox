package kr.co.edoli.edolitool.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlin.math.roundToInt

fun decodeBitmapFromUri(
    context: Context,
    uri: Uri,
    maxSide: Int = 2048,
): Bitmap? {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val scale = maxOf(width, height).toFloat() / maxSide
            if (scale > 1f) {
                decoder.setTargetSize((width / scale).roundToInt(), (height / scale).roundToInt())
            }
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }

    return bitmap.copy(Bitmap.Config.ARGB_8888, false)
}

fun decodeBitmapBounds(context: Context, uri: Uri): Pair<Int, Int>? {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(input, null, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }
}
