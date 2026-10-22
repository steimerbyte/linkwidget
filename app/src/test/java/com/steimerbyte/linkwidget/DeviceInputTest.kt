package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Reproduziert genau die Eingabe, die auf dem Geraet fehlgeschlagen ist.
 *
 * Im Screenshot stand "omp.steimercloud.xyz/" - ohne Schema. Das
 * muss durch den gesamten Weg bis zum HTTP-Request.
 */
class DeviceInputTest {

    @Test
    fun `eingabe ohne schema wird normalisiert`() {
        val raw = "omp.steimercloud.xyz/"
        val normalized = UrlNormalizer.normalize(raw)
        assertEquals("https://omp.steimercloud.xyz/", normalized)
    }

    @Test
    fun `host ist aus der normalisierten url lesbar`() {
        val normalized = UrlNormalizer.normalize("omp.steimercloud.xyz/")
        assertNotNull("Host konnte nicht gelesen werden", FaviconLoader.hostOf(normalized))
        assertEquals("omp.steimercloud.xyz", FaviconLoader.hostOf(normalized))
    }

    @Test
    fun `host ist aus der rohen eingabe NICHT lesbar`() {
        // Das ist die Ursache des Fehlers: hostOf() erwartet ein Schema.
        // Wird die rohe Eingabe uebergeben, scheitert es.
        assertEquals(null, FaviconLoader.hostOf("omp.steimercloud.xyz/"))
    }

    @Test
    fun `bibliothek kommt mit der normalisierten url klar`() {
        val normalized = UrlNormalizer.normalize("omp.steimercloud.xyz/")
        val extractor = net.mm2d.touchicon.TouchIconExtractor()
        extractor.userAgent =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        extractor.downloadLimit = 512 * 1024

        val icons = extractor.fromPage(normalized, true)
        println("  Icons: ${icons.size}")
        assertEquals("Keine Icons gefunden", true, icons.isNotEmpty())
    }
}
