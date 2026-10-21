package com.steimerbyte.linkwidget

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Integrationstest gegen echte Websites.
 *
 * Prueft die komplette Kette: Seite laden -> Icon-Link finden ->
 * Icon laden -> Format erkennen -> bei ICO das Bild extrahieren.
 *
 * Ohne BitmapFactory: die ist in lokalen Unit-Tests nicht mockbar.
 * Die eigentliche Dekodierung passiert zur Laufzeit auf dem Geraet.
 */
class FaviconFetchTest {

    private fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        )
        connection.instanceFollowRedirects = true
        return connection.inputStream.use { it.readBytes() }
    }

    private fun findIconLink(html: String): String? {
        val link = Regex("""<link\s[^>]*rel\s*=\s*["']([^"']*)["'][^>]*>""", RegexOption.IGNORE_CASE)
        val href = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val pref = listOf("fluid-icon", "shortcut icon", "icon", "apple-touch-icon", "mask-icon")
        val found = mutableMapOf<String, String>()
        for (m in link.findAll(html)) {
            val rel = m.groupValues[1].trim().lowercase()
            val h = href.find(m.value)?.groupValues?.get(1)?.trim() ?: continue
            if (h.isEmpty() || h.startsWith("data:")) continue
            val best = rel.split(" ").firstOrNull { it in pref } ?: rel
            found.putIfAbsent(best, h)
        }
        for (p in pref) found[p]?.let { return it }
        return null
    }

    @Test
    fun `omp steimercloud liefert ein verwendbares icon`() {
        val pageUrl = "https://omp.steimercloud.xyz"
        val html = String(fetch(pageUrl), Charsets.UTF_8)

        val iconHref = findIconLink(html)
        assertNotNull("Kein Icon-Link im HTML gefunden", iconHref)

        val iconUrl = FaviconLoader.resolveUrl(iconHref!!, pageUrl)
        assertNotNull("Icon-URL nicht aufloesbar", iconUrl)

        val bytes = fetch(iconUrl!!)
        val kind = ImageFormat.detect(bytes)
        println("  omp.steimercloud.xyz -> $iconUrl (${bytes.size} Bytes, $kind)")

        when (kind) {
            ImageFormat.Kind.PNG,
            ImageFormat.Kind.JPEG,
            ImageFormat.Kind.GIF,
            ImageFormat.Kind.WEBP -> {
                assertTrue("Bild ist leer", bytes.isNotEmpty())
            }

            ImageFormat.Kind.ICO -> {
                val extracted = IcoExtractor.extractLargestImage(bytes)
                assertNotNull("Bild konnte nicht aus ICO extrahiert werden", extracted)
                assertTrue("Extrahiertes Bild ist zu kurz", extracted!!.size > 54)
            }

            ImageFormat.Kind.SVG -> {
                // SVG kann die Plattform nicht dekodieren. Der Loader
                // weicht dann auf /favicon.ico aus.
                val base = URL(pageUrl)
                val fallback = "${base.protocol}://${base.host}/favicon.ico"
                val fallbackBytes = fetch(fallback)
                val fallbackKind = ImageFormat.detect(fallbackBytes)
                println("  Fallback $fallback -> $fallbackKind (${fallbackBytes.size} Bytes)")
                assertTrue(
                    "Weder Icon noch Fallback sind verwertbar",
                    fallbackKind != ImageFormat.Kind.UNKNOWN
                )
            }

            ImageFormat.Kind.UNKNOWN -> {
                val base = URL(pageUrl)
                val fallback = "${base.protocol}://${base.host}/favicon.ico"
                assertTrue("Auch /favicon.ico nicht ladbar", fetch(fallback).isNotEmpty())
            }
        }
    }
}
