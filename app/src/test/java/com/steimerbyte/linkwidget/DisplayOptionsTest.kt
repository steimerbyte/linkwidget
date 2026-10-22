package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Anzeigeoptionen der Widgets.
 *
 * Die Optionen entscheiden, ob Titel und URL auf dem Widget
 * erscheinen. Sie werden pro Widget-Instanz gespeichert, damit
 * jedes Widget seine eigene Darstellung haben kann.
 */
class DisplayOptionsTest {

    @Test
    fun `standard zeigt titel und url an`() {
        val link = LinkData(id = 1, title = "Meine Seite", url = "https://example.com/x")

        assertTrue("Titel muss standardmaessig sichtbar sein", link.showTitle)
        assertTrue("URL muss standardmaessig sichtbar sein", link.showUrl)
    }

    @Test
    fun `titel laesst sich ausblenden`() {
        val link = LinkData(
            id = 1,
            title = "Meine Seite",
            url = "https://example.com/x",
            showTitle = false
        )
        assertFalse(link.showTitle)
        // Die URL ist eine eigene Option und bleibt unberuehrt.
        assertTrue(link.showUrl)
    }

    @Test
    fun `url laesst sich ausblenden`() {
        val link = LinkData(
            id = 1,
            title = "Meine Seite",
            url = "https://example.com/x",
            showUrl = false
        )
        assertFalse(link.showUrl)
        assertTrue(link.showTitle)
    }

    @Test
    fun `beide koennen gleichzeitig aus sein`() {
        val link = LinkData(
            id = 1,
            title = "Meine Seite",
            url = "https://example.com/x",
            showTitle = false,
            showUrl = false
        )
        assertFalse(link.showTitle)
        assertFalse(link.showUrl)
    }

    @Test
    fun `jede widget instanz hat eigene optionen`() {
        // Zwei Widgets, unterschiedliche Einstellungen. Der Test
        // stellt sicher, dass die Optionen nicht global, sondern pro
        // Instanz gelten - sonst wuerde das Umschalten eines Widgets
        // alle anderen mit veraendern.
        val erstes = LinkData(id = 1, title = "A", url = "https://a.example", showTitle = false)
        val zweites = LinkData(id = 2, title = "B", url = "https://b.example")

        assertFalse(erstes.showTitle)
        assertTrue(zweites.showTitle)
        assertEquals(1, erstes.id)
        assertEquals(2, zweites.id)
    }
}
