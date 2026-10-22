package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Anzeigeoptionen der Widgets.
 *
 * Die Optionen entscheiden, ob Titel, URL und der Punkt unter dem
 * Icon auf dem Widget erscheinen. Sie werden pro Widget-Instanz
 * gespeichert, damit jedes Widget seine eigene Darstellung haben
 * kann.
 */
class DisplayOptionsTest {

    @Test
    fun `standard zeigt titel url und punkt an`() {
        val link = LinkData(id = 1, title = "Meine Seite", url = "https://example.com/x")

        assertTrue("Titel muss standardmaessig sichtbar sein", link.showTitle)
        assertTrue("URL muss standardmaessig sichtbar sein", link.showUrl)
        assertTrue("Punkt muss standardmaessig sichtbar sein", link.showDot)
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
        // Die anderen Optionen bleiben unberuehrt.
        assertTrue(link.showUrl)
        assertTrue(link.showDot)
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
        assertTrue(link.showDot)
    }

    @Test
    fun `punkt laesst sich ausblenden`() {
        val link = LinkData(
            id = 1,
            title = "Meine Seite",
            url = "https://example.com/x",
            showDot = false
        )
        assertFalse("Punkt muss ausgeblendet sein", link.showDot)
        assertTrue(link.showTitle)
        assertTrue(link.showUrl)
    }

    @Test
    fun `beide koennen gleichzeitig aus sein`() {
        val link = LinkData(
            id = 1,
            title = "Meine Seite",
            url = "https://example.com/x",
            showTitle = false,
            showUrl = false,
            showDot = false
        )
        assertFalse(link.showTitle)
        assertFalse(link.showUrl)
        assertFalse(link.showDot)
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

    @Test
    fun `alle drei optionen sind unabhaengig schaltbar`() {
        // Jede Option ist unabhaengig. Ein Fehler in einer Option
        // darf die anderen nicht mitveraendern.
        val nurPunktAus = LinkData(
            id = 1, title = "A", url = "https://a.example",
            showTitle = true, showUrl = true, showDot = false
        )
        assertTrue(nurPunktAus.showTitle)
        assertTrue(nurPunktAus.showUrl)
        assertFalse(nurPunktAus.showDot)

        val allesAus = LinkData(
            id = 2, title = "B", url = "https://b.example",
            showTitle = false, showUrl = false, showDot = false
        )
        assertFalse(allesAus.showTitle)
        assertFalse(allesAus.showUrl)
        assertFalse(allesAus.showDot)
    }
}