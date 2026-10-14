package com.nova.vpn.android.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Reads only small UTF-8 profile/config files selected explicitly by the user. */
object ProfileImportFileReader {
    const val MAX_FILE_BYTES = 4 * 1024 * 1024

    fun read(resolver: ContentResolver, uri: Uri): String {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть выбранный файл.")
        return stream.use(::read)
    }

    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_FILE_BYTES) {
                throw IOException("Файл конфигурации больше 4 МБ.")
            }
            output.write(buffer, 0, count)
        }

        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
            .removePrefix("\uFEFF")
        require(decoded.isNotBlank()) { "Файл пустой." }
        require('\u0000' !in decoded) { "Выбранный файл не похож на текстовую конфигурацию." }
        return decoded
    }
}
