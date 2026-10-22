package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Testet den Abrufweg, den der Button in der Konfiguration benutzt.
 *
 * Geprueft wird gegen eine echte Seite, damit der ganze Weg
 * abgedeckt ist: Seite laden, Icon finden, Icon laden, Format
 * erkennen, in eine BMP-Datei umwandeln.
 */
class FetchWithProgressTest {

    private fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0")
        connection.instanceFollowRedirects = true
        return connection.inputStream.use { it.readBytes() }
    }

    @Test
    fun `kompletter abrufweg liefert ein nutzbares bild`() {
        val pageUrl = "https://omp.steimercloud.xyz"
        val progress = mutableListOf<String>()

        val html = String(fetch(pageUrl), Charsets.UTF_8)
        progress.add("Seite geladen")

        val iconUrl = FaviconLoader.findIconUrlForTest(html, pageUrl)
        assertTrue("Kein Icon-Link gefunden", iconUrl != null)
        progress.add("Icon: $iconUrl")

        val bytes = fetch(iconUrl!!)
        val kind = ImageFormat.detect(bytes)
        progress.add("Format: $kind")

        // PNG/JPEG/GIF/WebP sind direkt verwendbar.
        if (kind in listOf(
                ImageFormat.Kind.PNG,
                ImageFormat.Kind.JPEG,
                ImageFormat.Kind.GIF,
                ImageFormat.Kind.WEBP
            )
        ) {
            assertTrue("Bild ist leer", bytes.isNotEmpty())
            progress.add("Direkt verwendbar")
        } else {
            // ICO muss entpackt werden.
            val extracted = IcoExtractor.extractLargestImage(bytes)
            assertTrue("Bild konnte nicht aus ICO extrahiert werden", extracted != null)
            assertTrue("Extrahiertes Bild ist zu kurz", extracted!!.size > 54)
            progress.add("Aus ICO entpackt: ${extracted.size} Bytes")
        }

        println("  " + progress.joinToString(" | "))
    }

}
