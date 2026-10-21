package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Testet die Auswahl des richtigen Icons.
 *
 * Das echte HTML von omp.steimercloud.xyz ist der Referenzfall: es
 * bietet drei Icons an, das erste ist ein 16x16-ICO, das zweite ein
 * 192x192-PNG. Der Browser nimmt das groesste - genau das muss die
 * App auch tun, sonst zeigt das Widget ein pixeliges Miniaturbild.
 */
class IconSelectionTest {

    /**
     * Das echte Icon-Set von omp.steimercloud.xyz (Next.js).
     */
    private val realHtml = """
        <!DOCTYPE html><html><head>
        <link rel="stylesheet" href="/_next/static/css/x.css"/>
        <link rel="icon" href="/favicon.ico?99f64a30ef2d78d5" type="image/x-icon" sizes="16x16"/>
        <link rel="icon" href="/icons/icon-192.png" sizes="192x192" type="image/png"/>
        <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png" sizes="180x180" type="image/png"/>
        <link rel="manifest" href="/manifest.webmanifest"/>
        </head><body></body></html>
    """.trimIndent()

    private fun findIconUrl(html: String, pageUrl: String): String? =
        FaviconLoader.findIconUrlForTest(html, pageUrl)

    @Test
    fun `groesstes icon gewinnt, nicht das erste`() {
        val chosen = findIconUrl(realHtml, "https://omp.steimercloud.xyz/login")
        assertEquals(
            "Es wurde nicht das groesste Icon gewaehlt",
            "https://omp.steimercloud.xyz/icons/icon-192.png",
            chosen
        )
    }

    @Test
    fun `sizes-attribut wird ausgewertet`() {
        assertEquals(16, FaviconLoader.parseSizesForTest("""sizes="16x16""""))
        assertEquals(192, FaviconLoader.parseSizesForTest("""sizes="192x192""""))
        assertEquals(32, FaviconLoader.parseSizesForTest("""sizes="16x16 32x32""""))
        assertEquals(0, FaviconLoader.parseSizesForTest("""sizes="any""""))
        assertEquals(0, FaviconLoader.parseSizesForTest("""type="image/png""""))
    }

    @Test
    fun `icon ohne sizes gewinnt gegen eines mit sizes wenn rel besser`() {
        val html = """
            <link rel="icon" href="/small.png" sizes="16x16"/>
            <link rel="apple-touch-icon" href="/big.png" sizes="180x180"/>
        """.trimIndent()
        assertEquals(
            "https://example.com/big.png",
            findIconUrl(html, "https://example.com/")
        )
    }

    @Test
    fun `einziges icon wird genommen`() {
        val html = """<link rel="icon" href="/only.png"/>"""
        assertEquals(
            "https://example.com/only.png",
            findIconUrl(html, "https://example.com/")
        )
    }

    @Test
    fun `rel fluid-icon wird bevorzugt bei gleicher groesse`() {
        val html = """
            <link rel="icon" href="/classic.png" sizes="64x64"/>
            <link rel="fluid-icon" href="/fluid.png" sizes="64x64"/>
        """.trimIndent()
        val chosen = findIconUrl(html, "https://example.com/")
        println("  gewaehlt: $chosen")
        assertEquals(
            "https://example.com/fluid.png",
            chosen
        )
    }

    @Test
    fun `ohne icon-links kommt null zurueck`() {
        assertEquals(null, findIconUrl("<html><head></head></html>", "https://example.com/"))
    }

    @Test
    fun `data-uri wird uebersprungen`() {
        val html = """<link rel="icon" href="data:image/png;base64,AAAA"/>"""
        assertEquals(null, findIconUrl(html, "https://example.com/"))
    }

    @Test
    fun `live-abruf von omp steimercloud liefert das 192er png`() {
        val connection = URL("https://omp.steimercloud.xyz").openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0")
        connection.instanceFollowRedirects = true
        val html = connection.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }

        val chosen = findIconUrl(html, "https://omp.steimercloud.xyz")
        assertNotNull("Kein Icon gefunden", chosen)
        assertEquals(
            "Es wurde nicht das 192x192-PNG gewaehlt",
            "https://omp.steimercloud.xyz/icons/icon-192.png",
            chosen
        )
    }
}
