package com.steimerbyte.linkwidget

/**
 * Erkennt das Format einer heruntergeladenen Bilddatei.
 *
 * AndroidBitmapFactory kann kein SVG und kein ICO dekodieren. Beides
 * kommt aber in freier Wildbahn vor:
 *
 *  - Hacker News liefert "y18.svg"
 *  - Wikipedia liefert "wikipedia.ico" (eingebettete BMP-Daten)
 *
 * Deshalb wird das Format vor dem Dekodieren geprueft, damit der
 * Loader einen passenden Weg waehlen kann.
 */
object ImageFormat {

    enum class Kind { PNG, JPEG, GIF, WEBP, SVG, ICO, UNKNOWN }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun detect(bytes: ByteArray): Kind {
        if (bytes.isEmpty()) return Kind.UNKNOWN

        if (bytes.size >= 8 && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) {
            return Kind.PNG
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return Kind.JPEG
        }
        if (bytes.size >= 3 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) {
            return Kind.GIF
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return Kind.WEBP
        }
        // ICO: 00 00 01 00
        if (bytes.size >= 4 &&
            bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
            bytes[2] == 1.toByte() && bytes[3] == 0.toByte()
        ) {
            return Kind.ICO
        }
        // SVG: XML mit <svg. Nur in den ersten 512 Bytes pruefen.
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1)
        if (head.contains("<svg") || (head.contains("<?xml") && head.contains("svg"))) {
            return Kind.SVG
        }
        return Kind.UNKNOWN
    }
}
