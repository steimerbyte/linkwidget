package com.steimerbyte.linkwidget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueft, dass die Ausgabe des Abrufs den konkreten Fehlergrund
 * nennt statt ihn zu verschlucken.
 *
 * Der Anlass: Auf dem Geraet blieb nur "Kein verwendbares Icon
 * gefunden" stehen. Damit ist nicht unterscheidbar, ob das Geraet
 * den Host nicht aufloest, die Verbindung blockiert ist oder die
 * Seite kein Icon liefert.
 */
class FetchDiagnosticsTest {

    @Test
    fun `nicht aufloesbare domain wird nicht als gelesene seite gemeldet`() {
        val lines = mutableListOf<String>()
        val candidates = FaviconLoader.findIconCandidates(
            "https://gibt-es-diese-domain-nicht-4711.xyz",
            null,
            { lines.add(it) }
        )

        assertTrue("Es sollten keine Kandidaten kommen", candidates.isEmpty())

        // Wichtig: Es wird nicht behauptet, die Seite habe kein Icon.
        // Die Bibliothek liefert bei einem Netzfehler eine leere
        // Liste statt einer Ausnahme - genau daraus entstand die
        // irrefuehrende Meldung auf dem Geraet.
        val reported = lines.joinToString("\n")
        println("  Ausgabe:\n$reported")
        assertTrue(
            "Ausgabe behauptet eine gelesene Seite: $reported",
            !lines.any { it.contains("Seite gelesen, aber kein") }
        )
    }

    @Test
    fun `erreichbare domain liefert ausgabe ohne fehler`() {
        val lines = mutableListOf<String>()
        FaviconLoader.findIconCandidates("https://omp.steimercloud.xyz", null, { lines.add(it) })

        val reported = lines.joinToString("\n")
        println("  Ausgabe:\n$reported")
        assertTrue(
            "Erwartet werden Icons: $reported",
            lines.any { it.contains("Icons erkannt") }
        )
        assertTrue(
            "Kein Fehler erwartet: $reported",
            lines.none { it.contains("FEHLER") }
        )
    }

    @Test
    fun `kandidatenliste nennt jeden kandidaten einzeln`() {
        val lines = mutableListOf<String>()
        FaviconLoader.findIconCandidates("https://omp.steimercloud.xyz", null, { lines.add(it) })

        // Jeder Kandidat muss als eigene Zeile erscheinen, damit beim
        // Kopieren der Ausgabe sichtbar ist, welche Adresse probiert
        // wurde - und mit welcher Groesse.
        val candidates = lines.count { it.trim().startsWith("http") || it.contains("px²") }
        println("  Kandidatenzeilen: $candidates")
        assertTrue("Keine Kandidatenzeilen: $lines", candidates >= 5)
    }
}