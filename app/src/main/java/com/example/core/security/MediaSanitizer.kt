package com.example.core.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class SanitizedMediaResult(
    val filePath: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val sha256Hex: String,
    val strippedSensitiveExif: Boolean,
    val mimeType: String = "image/jpeg",
)

/**
 * Sanitizador real de imagens para privacidade (LGPD) e limite de envio seguro (< 20 MB):
 * 1. Valida tipo MIME e tamanho bruto.
 * 2. Lê apenas a orientação EXIF para manter a foto na posição correta.
 * 3. Reamostra e recomprime para JPEG em pasta privada, eliminando GPS/câmera/EXIF sensíveis.
 */
object MediaSanitizer {

    private const val MAX_RAW_BYTES = 25L * 1024L * 1024L
    private const val MAX_OUTPUT_BYTES = 18L * 1024L * 1024L
    private const val MAX_DIMENSION_PX = 1600
    private const val JPEG_QUALITY = 84
    private const val SANITIZED_DIR = "sanitized_media"

    private val allowedMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
    )

    suspend fun sanitizeFromUri(
        context: Context,
        uri: Uri,
    ): Result<SanitizedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(uri)?.lowercase()
            if (detectedMime != null && detectedMime !in allowedMimeTypes) {
                throw IllegalArgumentException(
                    "Formato de arquivo não suportado ($detectedMime). Envie apenas JPEG, PNG ou WebP.",
                )
            }

            val rawBytes = resolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_RAW_BYTES) {
                        throw IllegalArgumentException(
                            "Imagem excede o limite bruto permitido de 25 MB.",
                        )
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: throw IllegalStateException("Não foi possível ler a imagem selecionada.")

            sanitizeBytesToPrivateFile(context = context, rawBytes = rawBytes)
        }
    }

    internal fun sanitizeBytesToPrivateFile(
        context: Context,
        rawBytes: ByteArray,
    ): SanitizedMediaResult {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("Arquivo de imagem vazio.")
        }

        // 1. Inspeciona metadados EXIF originais (orientação + presença de tags sensíveis)
        var orientation = ExifInterface.ORIENTATION_NORMAL
        var hadSensitiveExif = false
        runCatching {
            rawBytes.inputStream().use { exifStream ->
                val exif = ExifInterface(exifStream)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                hadSensitiveExif = listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME,
                ).any { tag -> !exif.getAttribute(tag).isNullOrBlank() }
            }
        }

        // 2. Decodifica dimensões sem estourar memória
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Conteúdo inválido ou imagem corrompida.")
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDim = MAX_DIMENSION_PX,
            )
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
            ?: throw IllegalStateException("Falha ao decodificar a imagem.")

        val oriented = applyExifOrientation(decoded, orientation)
        if (oriented !== decoded) decoded.recycle()

        val scaled = scaleDownIfNeeded(oriented, MAX_DIMENSION_PX)
        if (scaled !== oriented) oriented.recycle()

        val outDir = File(context.filesDir, SANITIZED_DIR).apply { mkdirs() }
        val outFile = File(outDir, "ua_${UUID.randomUUID()}.jpg")

        val compressedBytes = ByteArrayOutputStream().use { baos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            baos.toByteArray()
        }
        val finalWidth = scaled.width
        val finalHeight = scaled.height
        scaled.recycle()

        if (compressedBytes.size.toLong() > MAX_OUTPUT_BYTES) {
            throw IllegalArgumentException(
                "Imagem sanitizada ultrapassou o limite seguro de 18 MB.",
            )
        }

        FileOutputStream(outFile).use { fos ->
            fos.write(compressedBytes)
            fos.flush()
        }

        // 3. Garante que o arquivo final salvo não possui atributos EXIF sensíveis
        val hasResidualExif = verifyNoSensitiveExif(outFile)
        val sha256 = sha256Hex(compressedBytes)

        return SanitizedMediaResult(
            filePath = outFile.absolutePath,
            byteSize = outFile.length(),
            width = finalWidth,
            height = finalHeight,
            sha256Hex = sha256,
            strippedSensitiveExif = hadSensitiveExif || !hasResidualExif,
        )
    }

    fun verifyNoSensitiveExif(file: File): Boolean {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val sensitiveTags = listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_DIGITIZED,
            )
            sensitiveTags.any { !exif.getAttribute(it).isNullOrBlank() }
        }.getOrDefault(false)
    }

    fun deleteLocalMedia(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        return runCatching { File(filePath).delete() }.getOrDefault(false)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = maxOf(width, height)
        while (largest / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
