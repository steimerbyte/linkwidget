package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Testet die ICO-Extraktion an einem echten ICO aus der Wildbahn.
 *
 * Das hier verwendete ICO wird live geladen und ist ein typischer
 * Fall: drei DIB-Bilder (16, 32, 48 px) mit AND-Maske.
 */
class RealIcoTest {

    private fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0")
        connection.instanceFollowRedirects = true
        return connection.inputStream.use { it.readBytes() }
    }

    @Test
    fun `echtes ico liefert ein bild mit quadratischen massen`() {
        val ico = fetch("https://omp.steimercloud.xyz/favicon.ico")
        assertTrue("ICO ist zu klein", ico.size > 100)
        assertTrue("Kein ICO", ImageFormat.detect(ico) == ImageFormat.Kind.ICO)

        val extracted = IcoExtractor.extractLargestImage(ico)
        assertNotNull("Es wurde kein Bild extrahiert", extracted)

        // BitmapFactory ist in lokalen Unit-Tests nicht mockbar
        // ("not mocked"). Deshalb wird die Struktur der erzeugten
        // BMP-Datei geprueft: Header, korrigierte Hoehe, quadratisch.
        val bmp = extracted!!
        assertTrue("Zu kurz", bmp.size > 54)
        assertEquals('B'.code.toByte(), bmp[0])
        assertEquals('M'.code.toByte(), bmp[1])

        val buffer = ByteBuffer.wrap(bmp).order(ByteOrder.LITTLE_ENDIAN)
        val width = buffer.getInt(18)
        val height = buffer.getInt(22)
        val bitCount = buffer.getShort(28).toInt() and 0xFFFF

        // Das groesste Bild der Datei ist 48x48. Wegen der AND-Maske
        // steht in biHeight der doppelte Wert - das Ergebnis muss
        // trotzdem quadratisch sein.
        assertEquals("Bild ist nicht quadratisch: ${width}x$height", width, height)
        assertTrue("Bild ist zu klein: $width", width >= 32)
        assertEquals("Unerwartete Bit-Tiefe: $bitCount", 32, bitCount)

        println("  ico -> ${width}x$height, $bitCount bpp")
    }
}
