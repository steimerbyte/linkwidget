package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Format-Erkennung und die ICO-Extraktion.
 *
 * Verwendet echte Datei-Header, wie sie echte Websites liefern.
 */
class ImageFormatTest {

    @Test
    fun `png wird erkannt`() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
        )
        assertEquals(ImageFormat.Kind.PNG, ImageFormat.detect(png))
    }

    @Test
    fun `jpeg wird erkannt`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(ImageFormat.Kind.JPEG, ImageFormat.detect(jpeg))
    }

    @Test
    fun `gif wird erkannt`() {
        val gif = "GIF89a".toByteArray()
        assertEquals(ImageFormat.Kind.GIF, ImageFormat.detect(gif))
    }

    @Test
    fun `webp wird erkannt`() {
        val webp = "RIFF????WEBPVP8 ".toByteArray()
        assertEquals(ImageFormat.Kind.WEBP, ImageFormat.detect(webp))
    }

    @Test
    fun `ico wird erkannt`() {
        val ico = byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00)
        assertEquals(ImageFormat.Kind.ICO, ImageFormat.detect(ico))
    }

    @Test
    fun `svg wird erkannt`() {
        val svg = """<svg height="18" viewBox="4 4 188 188" xmlns="http://www.w3.org/2000/svg">""".toByteArray()
        assertEquals(ImageFormat.Kind.SVG, ImageFormat.detect(svg))
    }

    @Test
    fun `svg mit xml-deklaration wird erkannt`() {
        val svg = """<?xml version="1.0"?><svg xmlns="x"></svg>""".toByteArray()
        assertEquals(ImageFormat.Kind.SVG, ImageFormat.detect(svg))
    }

    @Test
    fun `leere daten sind unknown`() {
        assertEquals(ImageFormat.Kind.UNKNOWN, ImageFormat.detect(byteArrayOf()))
    }

    @Test
    fun `html ist unknown`() {
        val html = "<!DOCTYPE html><html><head></head></html>".toByteArray()
        assertEquals(ImageFormat.Kind.UNKNOWN, ImageFormat.detect(html))
    }

    /**
     * Baut ein minimales ICO mit einem 32x32-BMP-Bild.
     * Das entspricht der Struktur, die Wikipedia liefert.
     */
    private fun buildIcoWithBmp(): ByteArray {
        val width = 32
        val height = 32
        val bitCount = 32

        // Zeilen muessen auf 4 Byte ausgerichtet sein.
        val rowSize = ((width * bitCount + 31) / 32) * 4
        val pixelBytes = rowSize * height
        val maskRowSize = ((width + 31) / 32) * 4
        val maskBytes = maskRowSize * height

        val dibSize = 40
        // DIB = BITMAPINFOHEADER + Pixeldaten + AND-Maske
        val dib = ByteArray(dibSize + pixelBytes + maskBytes)
        writeIntLe(dib, 0, dibSize)          // biSize
        writeIntLe(dib, 4, width)            // biWidth
        writeIntLe(dib, 8, height * 2)       // biHeight: doppelt wegen AND-Maske
        dib[12] = 1; dib[13] = 0             // biPlanes
        dib[14] = bitCount.toByte(); dib[15] = 0 // biBitCount
        writeIntLe(dib, 16, 0)               // biCompression = BI_RGB
        writeIntLe(dib, 20, pixelBytes)      // biSizeImage

        // ICO-Datei
        val imageSize = dib.size
        val ico = ByteArray(6 + 16 + imageSize)
        ico[0] = 0; ico[1] = 0; ico[2] = 1; ico[3] = 0   // Header
        ico[4] = 1; ico[5] = 0                          // 1 Bild
        // Verzeichniseintrag eines ICO: 16 Byte ab Offset 6.
        //   0  Breite      1  Hoehe       2  Farben      3  reserviert
        //   4  Farbplanes  6  Bits pro Pixel
        //   8  Groesse der Bilddaten (4 Byte)
        //  12  Offset der Bilddaten (4 Byte)
        ico[6] = width.toByte()                          // Breite
        ico[7] = height.toByte()                         // Hoehe
        writeShortLe(ico, 10, 1)                         // Farbplanes
        writeShortLe(ico, 12, bitCount)                  // Bits pro Pixel
        writeIntLe(ico, 14, imageSize)                   // Groesse
        writeIntLe(ico, 18, 6 + 16)                      // Offset
        System.arraycopy(dib, 0, ico, 6 + 16, imageSize)
        return ico
    }

    private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
    @Test
    fun `bmp wird aus ico extrahiert und ist dekodierbar`() {
        val ico = buildIcoWithBmp()
        val extracted = IcoExtractor.extractLargestImage(ico)
        assertNotNull("Es wurde kein Bild extrahiert", extracted)
        assertTrue("Extrahierte Daten sind zu kurz", extracted!!.size > 14)
        // BMP-Dateiheader "BM"
        assertEquals('B'.code.toByte(), extracted[0])
        assertEquals('M'.code.toByte(), extracted[1])

        // BITMAPINFOHEADER der erzeugten BMP-Datei pruefen.
        val buffer = java.nio.ByteBuffer.wrap(extracted)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals("biSize falsch", 40, buffer.getInt(14))
        val width = buffer.getInt(18)
        val height = buffer.getInt(22)
        assertEquals("Bild ist nicht quadratisch: ${width}x$height", width, height)
    }

    @Test
    fun `kaputtes ico liefert null`() {
        assertNull(IcoExtractor.extractLargestImage(byteArrayOf(0x00, 0x00, 0x01, 0x00)))
    }

    @Test
    fun `kein ico liefert null`() {
        assertNull(IcoExtractor.extractLargestImage("nicht ein ico".toByteArray()))
    }
}
