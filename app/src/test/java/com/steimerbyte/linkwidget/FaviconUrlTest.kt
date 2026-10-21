package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testet das Herausfinden der Icon-Adresse im HTML.
 *
 * Geprueft wird das tatsaechliche HTML, das echte Seiten liefern -
 * inklusive relativer Pfade und mehrerer rel-Varianten. Die
 * URL-Aufloesung kommt direkt aus FaviconLoader, nicht aus einer
 * nachgebauten Kopie.
 */
class FaviconUrlTest {

    @Test
    fun `absoluter pfad bleibt unveraendert`() {
        val base = "https://www.kernel.org/"
        val href = "https://www.kernel.org/theme/images/logos/favicon.png"
        assertEquals(href, FaviconLoader.resolveUrl(href, base))
    }

    @Test
    fun `relativer pfad wird gegen seiten-url aufgeloest`() {
        // So liefert Hacker News sein Icon: href="y18.svg"
        assertEquals(
            "https://news.ycombinator.com/y18.svg",
            FaviconLoader.resolveUrl("y18.svg", "https://news.ycombinator.com/")
        )
    }

    @Test
    fun `relativer pfad ohne fuehrenden slash wird aufgeloest`() {
        assertEquals(
            "https://example.com/assets/icon.png",
            FaviconLoader.resolveUrl("assets/icon.png", "https://example.com/page/index.html")
        )
    }

    @Test
    fun `wurzel-relativer pfad wird aufgeloest`() {
        assertEquals(
            "https://example.com/favicon.ico",
            FaviconLoader.resolveUrl("/favicon.ico", "https://example.com/deep/page.html")
        )
    }

    @Test
    fun `host wird ohne www normalisiert`() {
        assertEquals("example.com", FaviconLoader.hostOf("https://www.example.com/x"))
        assertEquals("example.com", FaviconLoader.hostOf("https://example.com/x"))
    }

    @Test
    fun `host von mailto ist null`() {
        assertNull(FaviconLoader.hostOf("mailto:a@b.com"))
    }

    @Test
    fun `host von kaputter url ist null`() {
        assertNull(FaviconLoader.hostOf("nicht://eine::url"))
    }
}
