package com.steimerbyte.linkwidget

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extrahiert aus einer ICO-Datei die groesste enthaltene Bitmap.
 *
 * AndroidBitmapFactory kann kein ICO dekodieren. Eine ICO-Datei ist
 * aber ein Container: sie enthaelt mehrere Bilder, jedes als BMP oder
 * PNG. Das groesste Bild wird herausgeloest und als PNG oder BMP
 * zurueckgegeben, je nachdem, wie es im Container vorliegt.
 *
 * Aufbau einer ICO-Datei:
 *   0..3   Header: 00 00 01 00
 *   4..5   Anzahl der Bilder
 *   6..    Bildverzeichnis, je Eintrag 16 Byte:
 *            0     Breite  (0 bedeutet 256)
 *            1     Hoehe   (0 bedeutet 256)
 *            2     Farben im Farbpalette (0 bedeutet >=256)
 *            3..4   reserviert
 *            4..6   Farbplanes
 *            6..8   Bits pro Pixel
 *            8..12  Groesse der Bilddaten in Byte
 *            12..16 Offset der Bilddaten in der Datei
 */
object IcoExtractor {

    /**
     * Liefert die Bilddaten des groessten Bildes im ICO-Container.
     * Gibt null zurueck, wenn die Datei kein gueltiges ICO ist oder
     * keine Bilddaten enthaelt.
     */
    fun extractLargestImage(icoBytes: ByteArray): ByteArray? {
        if (icoBytes.size < 6) return null
        if (ImageFormat.detect(icoBytes) != ImageFormat.Kind.ICO) return null

        val buffer = ByteBuffer.wrap(icoBytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.getShort(4).toInt() and 0xFFFF
        if (count <= 0) return null

        var bestOffset = -1
        var bestSize = 0
        var bestArea = -1

        for (i in 0 until count) {
            val entry = 6 + i * 16
            if (entry + 16 > icoBytes.size) break

            // 0 bedeutet 256 Pixel
            val width = buffer.get(entry).toInt() and 0xFF
            val height = buffer.get(entry + 1).toInt() and 0xFF
            val size = buffer.getInt(entry + 8)
            val offset = buffer.getInt(entry + 12)

            val effectiveWidth = if (width == 0) 256 else width
            val effectiveHeight = if (height == 0) 256 else height
            val area = effectiveWidth * effectiveHeight

            if (size > 0 && offset >= 0 && offset + size <= icoBytes.size && area > bestArea) {
                bestArea = area
                bestOffset = offset
                bestSize = size
            }
        }

        if (bestOffset < 0 || bestSize <= 0) return null

        val imageData = icoBytes.copyOfRange(bestOffset, bestOffset + bestSize)

        // Liegt das Bild als PNG im Container, ist es direkt verwendbar.
        if (ImageFormat.detect(imageData) == ImageFormat.Kind.PNG) {
            return imageData
        }

        // Sonst ist es ein DIB. Der DIB-Header beginnt mit der
        // BITMAPINFOHEADER-Groesse (40) und enthaelt kein eigenes
        // Datei-Header-Feld. AndroidBitmapFactory erwartet aber ein
        // vollstaendiges BMP. Deshalb wird der ICO-Header (6 Byte)
        // entfernt und die BITMAPINFOHEADER-Struktur in einen
        // BMP-Dateiheader umgewandelt.
        return toBmpFile(imageData)
    }

    /**
     * Wandelt einen DIB (ohne Dateiheader) in eine BMP-Datei.
     */
    private fun toBmpFile(dib: ByteArray): ByteArray? {
        if (dib.size < 40) return null
        val buffer = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN)

        val headerSize = buffer.getInt(0)
        if (headerSize != 40) return null // nur BITMAPINFOHEADER

        val width = buffer.getInt(4)
        // Wichtig: In einer ICO ist biHeight doppelt so gross, weil die
        // AND-Maske mit in die Hoehe eingerechnet wird. Ein 48x48-Icon
        // hat hier biHeight=96. Fuer eine korrekte BMP-Datei muss die
        // Maske abgeschnitten und die halbe Hoehe verwendet werden.
        val storedHeight = buffer.getInt(8)
        val height = storedHeight / 2
        if (height <= 0) return null

        val bitCount = buffer.getShort(14).toInt() and 0xFFFF
        if (bitCount <= 0 || bitCount > 32) return null

        // Groesse der echten Pixeldaten ohne AND-Maske.
        val rowSize = ((width * bitCount + 31) / 32) * 4
        val pixelBytes = rowSize * height
        val maskRowSize = ((width + 31) / 32) * 4
        val maskBytes = maskRowSize * height

        // Das DIB enthaelt am Ende die AND-Maske. Fuer die BMP-Datei
        // wird sie abgeschnitten - BitmapFactory braucht sie nicht.
        val dibWithoutMask = if (dib.size >= 40 + pixelBytes) {
            dib.copyOfRange(0, 40 + pixelBytes)
        } else {
            return null
        }

        val fileSize = 14 + dibWithoutMask.size
        val pixelOffset = 14 + 40

        val out = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
        // BMP-Dateiheader
        out.put('B'.code.toByte())
        out.put('M'.code.toByte())
        out.putInt(fileSize)
        out.putShort(0) // reserviert
        out.putShort(0) // reserviert
        out.putInt(pixelOffset)
        // BITMAPINFOHEADER mit korrigierter Hoehe
        out.putInt(40)          // biSize
        out.putInt(width)       // biWidth
        out.putInt(height)      // biHeight (ohne AND-Maske)
        out.putShort(1)         // biPlanes
        out.putShort(bitCount.toShort()) // biBitCount
        out.putInt(0)           // biCompression = BI_RGB
        out.putInt(pixelBytes)  // biSizeImage
        out.putInt(0)           // biXPelsPerMeter
        out.putInt(0)           // biYPelsPerMeter
        out.putInt(0)           // biClrUsed
        out.putInt(0)           // biClrImportant
        // Pixeldaten
        out.put(dib, 40, pixelBytes)

        return out.array()
    }
}
