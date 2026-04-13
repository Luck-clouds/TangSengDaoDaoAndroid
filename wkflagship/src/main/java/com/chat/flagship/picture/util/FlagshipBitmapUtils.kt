package com.chat.flagship.picture.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.chat.base.utils.ImageUtils
import java.io.IOException
import kotlin.math.sqrt

/**
 * 图片编辑位图工具
 * Created by Luckclouds and chatGPT.
 */
private const val MAX_BITMAP_SIZE = 64f * 1024 * 1024

fun Context.getEditorBitmapFromPath(path: String, targetWidth: Int = 0): Bitmap? {
    return try {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        val bitmapWidth = options.outWidth
        val bitmapHeight = options.outHeight
        val bitmapSize = bitmapWidth * bitmapHeight * 4
        options.inJustDecodeBounds = false
        if (bitmapSize > MAX_BITMAP_SIZE || targetWidth > 0) {
            val maxWidth = (bitmapWidth * sqrt(MAX_BITMAP_SIZE / bitmapSize)).toInt()
            options.inScaled = true
            options.inDensity = bitmapWidth
            options.inTargetDensity = targetWidth.coerceAtMost(maxWidth)
        }
        BitmapFactory.decodeFile(path, options)
    } catch (e: Exception) {
        Log.e("FlagshipBitmapUtils", e.message.orEmpty())
        null
    }
}

fun Context.getEditorBitmapFromUri(uri: Uri, targetWidth: Int = 0): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    if (ContentResolver.SCHEME_CONTENT != uri.scheme && ContentResolver.SCHEME_FILE != uri.scheme) return null
    return try {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val bitmapWidth = info.size.width
            val bitmapHeight = info.size.height
            val bitmapSize = bitmapWidth * bitmapHeight * 4
            if (bitmapSize > MAX_BITMAP_SIZE || targetWidth > 0) {
                val maxWidth = bitmapWidth * sqrt(MAX_BITMAP_SIZE / bitmapSize)
                val scale = targetWidth.toFloat().coerceAtMost(maxWidth) / bitmapWidth
                decoder.setTargetSize((bitmapWidth * scale).toInt(), (bitmapHeight * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: IOException) {
        Log.e("FlagshipBitmapUtils", e.message.orEmpty())
        null
    }
}

fun Context.getEditorBitmapPathFromUri(uri: Uri): String {
    var imagePath = ""
    if (DocumentsContract.isDocumentUri(this, uri)) {
        val docId = DocumentsContract.getDocumentId(uri)
        if ("com.android.providers.media.documents" == uri.authority) {
            val id = docId.split(":".toRegex()).toTypedArray()[1]
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val selection = MediaStore.Images.Media._ID + "=" + id
            imagePath = contentResolverQueryPath(contentUri, selection)
        } else if ("com.android.providers.downloads.documents" == uri.authority) {
            val uriString = "content://downloads/public_downloads"
            val contentUri = ContentUris.withAppendedId(uriString.toUri(), docId.toLong())
            imagePath = contentResolverQueryPath(contentUri)
        }
    } else if ("content".equals(uri.scheme, ignoreCase = true)) {
        imagePath = contentResolverQueryPath(uri)
    } else if ("file".equals(uri.scheme, ignoreCase = true)) {
        imagePath = uri.path.toString()
    }
    return imagePath
}

private fun Context.contentResolverQueryPath(uri: Uri, selection: String = ""): String {
    val cursor = contentResolver.query(uri, null, selection, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(MediaStore.Images.Media.DATA)
            if (index >= 0) {
                return it.getString(index)
            }
        }
    }
    return ""
}

fun saveEditorBitmap(context: Context, bitmap: Bitmap, refreshAlbum: Boolean, onResult: (String) -> Unit) {
    ImageUtils.getInstance().saveBitmap(context, bitmap, refreshAlbum) { path ->
        onResult(path)
    }
}
