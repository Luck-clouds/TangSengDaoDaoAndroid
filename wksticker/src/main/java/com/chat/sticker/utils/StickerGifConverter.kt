package com.chat.sticker.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.chat.base.WKBaseApplication
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

data class StickerUploadFile(
    val path: String,
    val fileName: String,
    val convertedToGif: Boolean,
)

object StickerGifConverter {
    fun prepareUploadFile(localPath: String): StickerUploadFile {
        val sourceFile = File(localPath)
        val originalName = sourceFile.name.ifEmpty { "sticker.gif" }
        if (!sourceFile.exists()) {
            throw IOException("source file not found: $localPath")
        }
        if (sourceFile.extension.equals("gif", true)) {
            StickerTrace.d("STICKER_TRACE_UPLOAD_CONVERT skip path=$localPath reason=already_gif")
            return StickerUploadFile(sourceFile.absolutePath, originalName, false)
        }
        val bitmap = BitmapFactory.decodeFile(localPath)
            ?: throw IOException("decode bitmap failed: $localPath")
        val cacheDir = File(
            WKBaseApplication.getInstance().application.cacheDir,
            "wksticker_gif"
        )
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val targetName = buildString {
            append(sourceFile.nameWithoutExtension.ifEmpty { "sticker" })
            append('_')
            append(System.currentTimeMillis())
            append(".gif")
        }
        val targetFile = File(cacheDir, targetName)
        FileOutputStream(targetFile).use { output ->
            SingleFrameGifEncoder.encode(bitmap, output)
        }
        bitmap.recycle()
        StickerTrace.d("STICKER_TRACE_UPLOAD_CONVERT success source=$localPath target=${targetFile.absolutePath}")
        return StickerUploadFile(targetFile.absolutePath, targetFile.name, true)
    }
}

private object SingleFrameGifEncoder {
    private const val WIDTH_HEIGHT_LIMIT = 65535

    fun encode(bitmap: Bitmap, output: OutputStream) {
        val safeBitmap = ensureArgb8888(bitmap)
        try {
            val width = safeBitmap.width.coerceIn(1, WIDTH_HEIGHT_LIMIT)
            val height = safeBitmap.height.coerceIn(1, WIDTH_HEIGHT_LIMIT)
            val pixels = IntArray(width * height)
            safeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val colorTable = buildColorTable()
            val indexedPixels = ByteArray(pixels.size)
            for (i in pixels.indices) {
                indexedPixels[i] = quantizeTo332(pixels[i]).toByte()
            }

            output.write("GIF89a".toByteArray(Charsets.US_ASCII))
            writeShort(output, width)
            writeShort(output, height)
            output.write(0xF7)
            output.write(0x00)
            output.write(0x00)
            output.write(colorTable)

            output.write(0x21)
            output.write(0xF9)
            output.write(0x04)
            output.write(0x00)
            writeShort(output, 0)
            output.write(0x00)
            output.write(0x00)

            output.write(0x2C)
            writeShort(output, 0)
            writeShort(output, 0)
            writeShort(output, width)
            writeShort(output, height)
            output.write(0x00)

            output.write(8)
            val encoded = encodeLzw(indexedPixels)
            writeSubBlocks(output, encoded)
            output.write(0x3B)
            output.flush()
        } finally {
            if (safeBitmap !== bitmap) {
                safeBitmap.recycle()
            }
        }
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun buildColorTable(): ByteArray {
        val table = ByteArray(256 * 3)
        var index = 0
        for (r in 0..7) {
            for (g in 0..7) {
                for (b in 0..3) {
                    table[index++] = ((r * 255) / 7).toByte()
                    table[index++] = ((g * 255) / 7).toByte()
                    table[index++] = ((b * 255) / 3).toByte()
                }
            }
        }
        return table
    }

    private fun quantizeTo332(color: Int): Int {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return ((red shr 5) shl 5) or ((green shr 5) shl 2) or (blue shr 6)
    }

    private fun encodeLzw(indexedPixels: ByteArray): ByteArray {
        if (indexedPixels.isEmpty()) return byteArrayOf()
        val minCodeSize = 8
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        val writer = GifCodeWriter()
        var dictionary = HashMap<String, Int>()

        fun resetDictionary() {
            codeSize = minCodeSize + 1
            nextCode = endCode + 1
            dictionary = HashMap()
            for (i in 0 until clearCode) {
                dictionary[String(charArrayOf(i.toChar()))] = i
            }
        }

        resetDictionary()
        writer.write(clearCode, codeSize)

        var sequence = String(charArrayOf((indexedPixels[0].toInt() and 0xFF).toChar()))
        for (i in 1 until indexedPixels.size) {
            val char = String(charArrayOf((indexedPixels[i].toInt() and 0xFF).toChar()))
            val joined = sequence + char
            if (dictionary.containsKey(joined)) {
                sequence = joined
                continue
            }
            writer.write(dictionary[sequence] ?: 0, codeSize)
            if (nextCode < 4096) {
                dictionary[joined] = nextCode++
                if (nextCode == (1 shl codeSize) && codeSize < 12) {
                    codeSize += 1
                }
            } else {
                writer.write(clearCode, codeSize)
                resetDictionary()
            }
            sequence = char
        }

        writer.write(dictionary[sequence] ?: 0, codeSize)
        writer.write(endCode, codeSize)
        return writer.toByteArray()
    }

    private fun writeSubBlocks(output: OutputStream, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val blockSize = minOf(255, data.size - offset)
            output.write(blockSize)
            output.write(data, offset, blockSize)
            offset += blockSize
        }
        output.write(0x00)
    }

    private fun writeShort(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }
}

private class GifCodeWriter {
    private val output = ByteArrayOutputStream()
    private var current = 0
    private var bitCount = 0

    fun write(code: Int, codeSize: Int) {
        current = current or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            output.write(current and 0xFF)
            current = current ushr 8
            bitCount -= 8
        }
    }

    fun toByteArray(): ByteArray {
        if (bitCount > 0) {
            output.write(current and 0xFF)
            current = 0
            bitCount = 0
        }
        return output.toByteArray()
    }
}
