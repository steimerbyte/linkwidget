package com.steviroberts.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testet die echte URL-Normalisierung aus der Produktion.
 *
 * Wichtig: eine URL ohne Schema ("example.com") wuerde von Uri.parse als
 * relativer Pfad interpretiert. Der Intent ginge dann ins Leere bzw. wuerde
 * einen leeren Browser-Tab oeffnen. Deshalb wird "https://" ergaenzt.
 */
class UrlNormalizerTest {

    @Test
    fun `haelt https unveraendert`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com"))
    }

    @Test
    fun `haelt http unveraendert`() {
        assertEquals("http://example.com", UrlNormalizer.normalize("http://example.com"))
    }

    @Test
    fun `ergaenzt https bei fehlendem schema`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test
    fun `ergaenzt https bei punkt-domain start`() {
        assertEquals("https://sub.example.com/path", UrlNormalizer.normalize("sub.example.com/path"))
    }

    @Test
    fun `trimmt umgebende leerzeichen`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("   https://example.com   "))
    }

    @Test
    fun `behaelt pfad und query unveraendert`() {
        assertEquals(
            "https://example.com/a/b?c=d&e=f",
            UrlNormalizer.normalize("https://example.com/a/b?c=d&e=f")
        )
    }

    @Test
    fun `behaelt localhost und ports`() {
        assertEquals("http://localhost:8080/x", UrlNormalizer.normalize("http://localhost:8080/x"))
    }

    @Test
    fun `behaelt mailto scheme ohne doppelpunkt-slash`() {
        assertEquals("mailto:a@b.com", UrlNormalizer.normalize("mailto:a@b.com"))
    }

    @Test
    fun `behaelt tel scheme`() {
        assertEquals("tel:+491234567", UrlNormalizer.normalize("tel:+491234567"))
    }

    @Test
    fun `behaelt custom scheme mit slash`() {
        assertEquals("myapp://open/x", UrlNormalizer.normalize("myapp://open/x"))
    }

    @Test
    fun `pfad mit doppelpunkt ist kein scheme`() {
        assertEquals("https://example.com/a:b", UrlNormalizer.normalize("example.com/a:b"))
    }

    @Test
    fun `leerer string bleibt leer`() {
        assertEquals("", UrlNormalizer.normalize(""))
    }
}
