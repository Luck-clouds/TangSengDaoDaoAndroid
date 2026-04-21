package com.chat.sticker.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.chat.base.WKBaseApplication
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

data class StickerUploadFile(
    val path: String,
    val fileName: String,
    val convertedToGif: Boolean,
)

object StickerGifConverter {
    private const val MAX_GIF_SIDE = 512

    fun prepareUploadFile(localPath: String): StickerUploadFile {
        val sourceFile = File(localPath)
        val originalName = sourceFile.name.ifEmpty { "sticker.gif" }
        if (!sourceFile.exists()) {
            throw IOException("source file not found: $localPath")
        }
        val sourceInfo = readBitmapSourceInfo(localPath)
        StickerTrace.d(
            "STICKER_TRACE_UPLOAD_CONVERT source path=$localPath fileSize=${sourceFile.length()} " +
                "bounds=${sourceInfo.width}x${sourceInfo.height} mime=${sourceInfo.mimeType}"
        )
        if (sourceFile.extension.equals("gif", true)) {
            StickerTrace.d("STICKER_TRACE_UPLOAD_CONVERT skip path=$localPath reason=already_gif ${readGifDebugInfo(sourceFile).toLogString()}")
            return StickerUploadFile(sourceFile.absolutePath, originalName, false)
        }
        val bitmap = decodeBitmap(localPath, sourceInfo)
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
        val encodeResult = FileOutputStream(targetFile).use { output ->
            SingleFrameGifEncoder.encode(bitmap, output)
        }
        bitmap.recycle()
        StickerTrace.d(
            "STICKER_TRACE_UPLOAD_CONVERT success source=$localPath target=${targetFile.absolutePath} " +
                "encoded=${encodeResult.width}x${encodeResult.height} indexedBytes=${encodeResult.indexedBytes} " +
                "transparentPixels=${encodeResult.transparentPixels} ${readGifDebugInfo(targetFile).toLogString()}"
        )
        return StickerUploadFile(targetFile.absolutePath, targetFile.name, true)
    }

    private fun readBitmapSourceInfo(path: String): BitmapSourceInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return BitmapSourceInfo(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = options.outMimeType.orEmpty()
        )
    }

    private fun decodeBitmap(path: String, sourceInfo: BitmapSourceInfo): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateInSampleSize(sourceInfo.width, sourceInfo.height)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        while (width / sampleSize > MAX_GIF_SIDE || height / sampleSize > MAX_GIF_SIDE) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}

private data class BitmapSourceInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
)

private data class GifEncodeResult(
    val width: Int,
    val height: Int,
    val indexedBytes: Int,
    val transparentPixels: Int,
)

private data class PaletteResult(
    val colorTable: ByteArray,
    val colors: IntArray,
    val firstColorIndex: Int,
)

private data class ColorPoint(
    val red: Int,
    val green: Int,
    val blue: Int,
    val count: Long,
) {
    fun toColor(): Int {
        return (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }
}

private class ColorBox(private val points: List<ColorPoint>) {
    private val redRange: Int
    private val greenRange: Int
    private val blueRange: Int
    private val totalCount: Long

    val canSplit: Boolean
        get() = points.size > 1

    val score: Long
        get() = maxOf(redRange, greenRange, blueRange).toLong() * totalCount

    init {
        var minRed = 255
        var maxRed = 0
        var minGreen = 255
        var maxGreen = 0
        var minBlue = 255
        var maxBlue = 0
        var count = 0L
        points.forEach { point ->
            minRed = minOf(minRed, point.red)
            maxRed = maxOf(maxRed, point.red)
            minGreen = minOf(minGreen, point.green)
            maxGreen = maxOf(maxGreen, point.green)
            minBlue = minOf(minBlue, point.blue)
            maxBlue = maxOf(maxBlue, point.blue)
            count += point.count
        }
        redRange = maxRed - minRed
        greenRange = maxGreen - minGreen
        blueRange = maxBlue - minBlue
        totalCount = count
    }

    fun split(): Pair<ColorBox, ColorBox>? {
        if (!canSplit) return null
        val sorted = when (maxOf(redRange, greenRange, blueRange)) {
            redRange -> points.sortedBy { it.red }
            greenRange -> points.sortedBy { it.green }
            else -> points.sortedBy { it.blue }
        }
        val halfCount = totalCount / 2L
        var runningCount = 0L
        var splitIndex = 1
        for (i in sorted.indices) {
            runningCount += sorted[i].count
            if (runningCount >= halfCount) {
                splitIndex = (i + 1).coerceIn(1, sorted.size - 1)
                break
            }
        }
        return ColorBox(sorted.subList(0, splitIndex)) to ColorBox(sorted.subList(splitIndex, sorted.size))
    }

    fun averageColor(): Int {
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var countSum = 0L
        points.forEach { point ->
            redSum += point.red.toLong() * point.count
            greenSum += point.green.toLong() * point.count
            blueSum += point.blue.toLong() * point.count
            countSum += point.count
        }
        if (countSum <= 0L) return 0xFFFFFF
        val red = (redSum / countSum).toInt()
        val green = (greenSum / countSum).toInt()
        val blue = (blueSum / countSum).toInt()
        return (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }
}

private data class GifDebugInfo(
    val fileSize: Long = 0,
    val header: String = "",
    val logicalWidth: Int = 0,
    val logicalHeight: Int = 0,
    val hasGlobalColorTable: Boolean = false,
    val globalColorTableSize: Int = 0,
    val graphicControlCount: Int = 0,
    val imageDescriptorCount: Int = 0,
    val trailerHex: String = "",
    val firstBytesHex: String = "",
    val error: String = "",
) {
    fun toLogString(): String {
        if (error.isNotEmpty()) return "gifInfoError=$error fileSize=$fileSize"
        return "gifInfo=fileSize=$fileSize header=$header logical=${logicalWidth}x$logicalHeight " +
            "gct=$hasGlobalColorTable gctSize=$globalColorTableSize gce=$graphicControlCount " +
            "images=$imageDescriptorCount trailer=$trailerHex firstBytes=$firstBytesHex"
    }
}

private object SingleFrameGifEncoder {
    private const val MAX_GIF_SIDE = 512
    private const val WIDTH_HEIGHT_LIMIT = 65535
    private const val TRANSPARENT_ALPHA_THRESHOLD = 32

    fun encode(bitmap: Bitmap, output: OutputStream): GifEncodeResult {
        val safeBitmap = createSafeBitmap(bitmap)
        try {
            val width = safeBitmap.width.coerceIn(1, WIDTH_HEIGHT_LIMIT)
            val height = safeBitmap.height.coerceIn(1, WIDTH_HEIGHT_LIMIT)
            val pixels = IntArray(width * height)
            safeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val hasTransparentPixels = pixels.any { ((it ushr 24) and 0xFF) < TRANSPARENT_ALPHA_THRESHOLD }
            val palette = buildAdaptivePalette(pixels, hasTransparentPixels)
            val indexedPixels = ByteArray(pixels.size)
            val colorCache = HashMap<Int, Int>()
            var transparentPixels = 0
            for (i in pixels.indices) {
                val alpha = (pixels[i] ushr 24) and 0xFF
                if (alpha < TRANSPARENT_ALPHA_THRESHOLD) {
                    indexedPixels[i] = 0
                    transparentPixels += 1
                } else {
                    indexedPixels[i] = nearestPaletteIndex(
                        pixels[i],
                        palette.colors,
                        palette.firstColorIndex,
                        colorCache
                    ).toByte()
                }
            }

            output.write("GIF89a".toByteArray(Charsets.US_ASCII))
            writeShort(output, width)
            writeShort(output, height)
            output.write(0xF7)
            output.write(0x00)
            output.write(0x00)
            output.write(palette.colorTable)

            output.write(0x21)
            output.write(0xF9)
            output.write(0x04)
            output.write(if (hasTransparentPixels) 0x01 else 0x00)
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
            val encoded = encodeLiteralLzw(indexedPixels)
            writeSubBlocks(output, encoded)
            output.write(0x3B)
            output.flush()
            return GifEncodeResult(width, height, indexedPixels.size, transparentPixels)
        } finally {
            if (safeBitmap !== bitmap) {
                safeBitmap.recycle()
            }
        }
    }

    private fun createSafeBitmap(bitmap: Bitmap): Bitmap {
        val argbBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val maxSide = maxOf(argbBitmap.width, argbBitmap.height)
        if (maxSide <= MAX_GIF_SIDE) return argbBitmap
        val scale = MAX_GIF_SIDE.toFloat() / maxSide.toFloat()
        val targetWidth = (argbBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (argbBitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(argbBitmap, targetWidth, targetHeight, true)
        if (argbBitmap !== bitmap) {
            argbBitmap.recycle()
        }
        return scaledBitmap
    }

    private fun buildAdaptivePalette(pixels: IntArray, hasTransparentPixels: Boolean): PaletteResult {
        val maxColors = if (hasTransparentPixels) 255 else 256
        val buckets = HashMap<Int, LongArray>()
        pixels.forEach { color ->
            val alpha = (color ushr 24) and 0xFF
            if (alpha < TRANSPARENT_ALPHA_THRESHOLD) return@forEach
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            val key = ((red shr 3) shl 10) or ((green shr 3) shl 5) or (blue shr 3)
            val bucket = buckets.getOrPut(key) { LongArray(4) }
            bucket[0] += red.toLong()
            bucket[1] += green.toLong()
            bucket[2] += blue.toLong()
            bucket[3] += 1L
        }
        val points = buckets.values.mapNotNull { bucket ->
            val count = bucket[3]
            if (count <= 0L) {
                null
            } else {
                ColorPoint(
                    red = (bucket[0] / count).toInt(),
                    green = (bucket[1] / count).toInt(),
                    blue = (bucket[2] / count).toInt(),
                    count = count
                )
            }
        }
        val paletteColors = mutableListOf<Int>()
        if (hasTransparentPixels) {
            paletteColors += 0x000000
        }
        if (points.isEmpty()) {
            paletteColors += 0xFFFFFF
        } else {
            paletteColors += quantizePalette(points, maxColors)
        }
        while (paletteColors.size < 256) {
            paletteColors += paletteColors.lastOrNull() ?: 0xFFFFFF
        }
        val table = ByteArray(256 * 3)
        paletteColors.take(256).forEachIndexed { colorIndex, color ->
            val tableIndex = colorIndex * 3
            table[tableIndex] = ((color shr 16) and 0xFF).toByte()
            table[tableIndex + 1] = ((color shr 8) and 0xFF).toByte()
            table[tableIndex + 2] = (color and 0xFF).toByte()
        }
        return PaletteResult(
            colorTable = table,
            colors = paletteColors.take(256).toIntArray(),
            firstColorIndex = if (hasTransparentPixels) 1 else 0
        )
    }

    private fun quantizePalette(points: List<ColorPoint>, maxColors: Int): List<Int> {
        if (points.size <= maxColors) {
            return points.map { it.toColor() }
        }
        val boxes = mutableListOf(ColorBox(points))
        while (boxes.size < maxColors) {
            val index = boxes
                .withIndex()
                .filter { it.value.canSplit }
                .maxByOrNull { it.value.score }
                ?.index ?: break
            val split = boxes.removeAt(index).split() ?: break
            boxes += split.first
            boxes += split.second
        }
        return boxes.map { it.averageColor() }
    }

    private fun nearestPaletteIndex(
        color: Int,
        colors: IntArray,
        firstColorIndex: Int,
        cache: MutableMap<Int, Int>
    ): Int {
        val key = color and 0x00FFFFFF
        cache[key]?.let { return it }
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        var bestIndex = firstColorIndex.coerceAtMost(colors.lastIndex)
        var bestDistance = Int.MAX_VALUE
        for (i in firstColorIndex until colors.size) {
            val paletteColor = colors[i]
            val dr = red - ((paletteColor shr 16) and 0xFF)
            val dg = green - ((paletteColor shr 8) and 0xFF)
            val db = blue - (paletteColor and 0xFF)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
                if (distance == 0) break
            }
        }
        cache[key] = bestIndex
        return bestIndex
    }

    private fun encodeLiteralLzw(indexedPixels: ByteArray): ByteArray {
        val minCodeSize = 8
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        var firstAfterClear = true
        val writer = GifCodeWriter()

        writer.write(clearCode, codeSize)
        indexedPixels.forEach { pixel ->
            writer.write(pixel.toInt() and 0xFF, codeSize)
            if (firstAfterClear) {
                firstAfterClear = false
            } else {
                nextCode += 1
                if (nextCode == (1 shl codeSize) && codeSize < 12) {
                    codeSize += 1
                }
                if (nextCode >= 4096) {
                    writer.write(clearCode, codeSize)
                    codeSize = minCodeSize + 1
                    nextCode = endCode + 1
                    firstAfterClear = true
                }
            }
        }
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

private fun readGifDebugInfo(file: File): GifDebugInfo {
    return runCatching {
        val bytes = file.readBytes()
        val packed = if (bytes.size > 10) bytes[10].toInt() and 0xFF else 0
        val hasGlobalColorTable = (packed and 0x80) != 0
        val globalColorTableSize = if (hasGlobalColorTable) 2 shl (packed and 0x07) else 0
        GifDebugInfo(
            fileSize = file.length(),
            header = if (bytes.size >= 6) String(bytes, 0, 6, Charsets.US_ASCII) else "",
            logicalWidth = bytes.readUnsignedShort(6),
            logicalHeight = bytes.readUnsignedShort(8),
            hasGlobalColorTable = hasGlobalColorTable,
            globalColorTableSize = globalColorTableSize,
            graphicControlCount = bytes.countPattern(0x21, 0xF9, 0x04),
            imageDescriptorCount = bytes.count { (it.toInt() and 0xFF) == 0x2C },
            trailerHex = bytes.lastOrNull()?.let { "%02X".format(it.toInt() and 0xFF) }.orEmpty(),
            firstBytesHex = bytes.toHexString(24)
        )
    }.getOrElse {
        GifDebugInfo(fileSize = file.length(), error = it.message.orEmpty())
    }
}

private fun ByteArray.readUnsignedShort(offset: Int): Int {
    if (size <= offset + 1) return 0
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.countPattern(vararg pattern: Int): Int {
    if (pattern.isEmpty() || size < pattern.size) return 0
    var count = 0
    for (i in 0..(size - pattern.size)) {
        var matched = true
        for (j in pattern.indices) {
            if ((this[i + j].toInt() and 0xFF) != pattern[j]) {
                matched = false
                break
            }
        }
        if (matched) count += 1
    }
    return count
}

private fun ByteArray.toHexString(limit: Int): String {
    return take(limit).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
