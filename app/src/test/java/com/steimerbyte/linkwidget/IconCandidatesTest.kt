package com.steimerbyte.linkwidget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft die Icon-Kandidaten, die der Produktivpfad fuer eine echte
 * Seite liefert.
 *
 * Der Referenzfall ist omp.steimercloud.xyz: eine Next.js-Anwendung,
 * die ein 16x16-ICO, ein 192x192-PNG, ein 180x180-Apple-Icon und
 * ein Web-App-Manifest mit einem 512x512-Icon anbietet.
 *
 * Der Test greift auf FaviconLoader.findIconCandidates zu - also auf
 * genau den Code, der auch auf dem Geraet laeuft. Ein Test, der sich
 * die Bibliothek selbst sortiert, wuerde einen Sortierfehler im
 * Produktivpfad nicht sehen.
 */
class IconCandidatesTest {

    private val page = "https://omp.steimercloud.xyz"

    private fun candidatesFor(url: String): List<String> =
        FaviconLoader.findIconCandidates(url) { }

    @Test
    fun `kandidaten einer echten seite werden gefunden`() {
        val candidates = candidatesFor(page)
        println("  Kandidaten: $candidates")
        assertTrue("Keine Kandidaten gefunden", candidates.isNotEmpty())
    }

    @Test
    fun `bestes ist nicht das 16x16 ico`() {
        val candidates = candidatesFor(page)
        assertTrue("Keine Kandidaten gefunden", candidates.isNotEmpty())

        // Der erste Kandidat wird geladen. Das 16x16-ICO darf nicht
        // gewinnen - es sieht auf dem Homescreen wie ein unscharfer
        // Fleck aus.
        val first = candidates.first()
        assertTrue(
            "Falscher Kandidat zuerst: $first",
            !first.contains("favicon.ico")
        )
    }

    @Test
    fun `groesstes icon kommt zuerst`() {
        val candidates = candidatesFor(page)
        assertTrue("Keine Kandidaten gefunden", candidates.isNotEmpty())
        assertTrue(
            "Das 512er-Icon muss gewinnen: $candidates",
            candidates.first().contains("icon-512")
        )
    }

    @Test
    fun `web app manifest wird ausgewertet`() {
        val candidates = candidatesFor(page)
        assertTrue(
            "Manifest-Icon fehlt in den Kandidaten: $candidates",
            candidates.any { it.contains("icon-512") }
        )
    }

    @Test
    fun `nicht erreichbare seite wirft nicht`() {
        // Der Aufrufer erwartet eine Liste, keine Ausnahme.
        val candidates = candidatesFor("https://omp.steimercloud.xyz/gibt-es-nicht-12345")
        println("  Kandidaten einer 404-Seite: $candidates")
        assertTrue("Unerwartet Kandidaten bei 404", candidates.isEmpty())
    }
}