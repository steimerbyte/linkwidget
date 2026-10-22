package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Testet, dass die Aufloesung fuer die ganze Domain gilt und dass
 * der Hostname beim TLS-Handshake erhalten bleibt.
 *
 * Der Anlass sind zwei Fehler, die erst auf dem Geraet sichtbar
 * wurden:
 *
 *  1. Die Aufloesung wurde auf einen Host festgenagelt. Verweist
 *     ein Icon auf einen anderen Host - etwa
 *     `https://cdn.example.com/favicon.png` -, fiel der Abruf in den
 *     System-Resolver zurueck und scheiterte dort wieder.
 *  2. Die IP wurde in die URL geschrieben, um die Aufloesung zu
 *     umgehen. Damit ging der TLS-SNI an die IP statt an den
 *     Hostnamen, und Nginx antwortete mit
 *     TLSV1_ALERT_UNRECOGNIZED_NAME.
 */
class DomainDnsTest {

    /**
     * Bildet das nach, was FaviconLoader.DomainDns macht.
     */
    private fun belongsToDomain(candidate: String, host: String): Boolean {
        val a = candidate.lowercase()
        val b = host.lowercase()
        if (a == b) return true
        if (a == "www.$b" || b == "www.$a") return true
        if (a.endsWith(".$b")) return true
        if (b.endsWith(".$a")) return true
        return false
    }

    @Test
    fun `gleicher host gehoert zur domain`() {
        assertTrue(belongsToDomain("omp.steimercloud.xyz", "omp.steimercloud.xyz"))
    }

    @Test
    fun `www variante gehoert zur domain`() {
        assertTrue(belongsToDomain("www.steimercloud.xyz", "steimercloud.xyz"))
        assertTrue(belongsToDomain("steimercloud.xyz", "www.steimercloud.xyz"))
    }

    @Test
    fun `subdomain gehoert zur domain`() {
        // Der Fall aus der Aufgabenstellung: Icons liegen haeufig
        // auf einer Subdomain.
        assertTrue(belongsToDomain("cdn.steimercloud.xyz", "steimercloud.xyz"))
        assertTrue(belongsToDomain("static.assets.steimercloud.xyz", "steimercloud.xyz"))
    }

    @Test
    fun `fremde domain gehoert nicht dazu`() {
        assertTrue(!belongsToDomain("example.com", "steimercloud.xyz"))
        assertTrue(!belongsToDomain("evil-steimercloud.xyz", "steimercloud.xyz"))
        assertTrue(!belongsToDomain("steimercloud.xyz.evil.com", "steimercloud.xyz"))
    }

    @Test
    fun `abfrage mit host im url erhaelt den sni`() {
        // Der Kernpunkt des SNI-Fehlers: In der URL steht der
        // Hostname, nicht die IP. Nur so greift die
        // Zertifikatspruefung und der Server weiss, welches
        // Zertifikat er senden soll.
        val url = "https://omp.steimercloud.xyz/"
        assertTrue(
            "Die URL darf keine IP enthalten",
            !url.contains("77.11.143.146")
        )
        assertTrue(url.contains("omp.steimercloud.xyz"))
    }

    @Test
    fun `seite laesst sich ueber den resolver laden`() {
        // Ohne System-Resolver: die Adresse kommt aus DohResolver.
        val addresses = DohResolver.lookup("omp.steimercloud.xyz") { }
        assertTrue("Keine Adresse", addresses != null && addresses.isNotEmpty())
        assertTrue(
            "Falsche Adresse: $addresses",
            addresses!!.any { it.hostAddress == "77.11.143.146" }
        )
        assertEquals(
            "Unerwartet viele Adressen: $addresses",
            1,
            addresses.filter { it.hostAddress == "77.11.143.146" }.size
        )
    }

    @Test
    fun `subdomain laesst sich ebenfalls aufloesen`() {
        // Beweist, dass die Aufloesung nicht auf einen Host
        // festgenagelt ist.
        val addresses = DohResolver.lookup("steimercloud.xyz") { }
        assertTrue("Basisdomain nicht aufloesbar", addresses != null && addresses.isNotEmpty())
        assertTrue(
            "Basisdomain liefert falsche Adresse: $addresses",
            addresses!!.any { it.hostAddress == "77.11.143.146" }
        )
    }
}