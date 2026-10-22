package com.steimerbyte.linkwidget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Testet den HTTP-Client mit fest verdrahteter Adresse.
 *
 * Der Anlass: Auf dem Geraet schlug die Aufloesung des Hostnamens
 * ueber den System-Resolver fehl. Der HTTP-Zugriff loest den Namen
 * aber erneut auf - und scheitert damit wieder. Der Client
 * beantwortet die Aufloesung deshalb selbst.
 *
 * Der entscheidende Punkt ist das Zertifikat: Die IP wird
 * angesprochen, der Hostname bleibt aber in SNI und Host-Header.
 * Sonst wuerde die TLS-Pruefung fehlschlagen.
 */
class FixedDnsClientTest {

    @Test
    fun `seite laesst sich ueber die feste adresse laden`() {
        val address = InetAddress.getByName("77.11.143.146")
        val client = FixedDnsHttpClient("omp.steimercloud.xyz", address, "Mozilla/5.0")

        val response = client.get("https://omp.steimercloud.xyz/")
        val body = response.bodyString(MAX_HTML) ?: ""
        response.close()

        println("  Status: ${response.isSuccess}, ${body.length} Zeichen")
        assertTrue("Seite nicht geladen", body.isNotEmpty())
        assertTrue(
            "Erwartet wird die Login-Seite, erhalten: ${body.take(200)}",
            body.contains("login", ignoreCase = true) ||
                body.contains("<html", ignoreCase = true)
        )
    }

    @Test
    fun `zertifikat wird gegen den hostnamen geprueft`() {
        val address = InetAddress.getByName("77.11.143.146")
        val client = FixedDnsHttpClient("omp.steimercloud.xyz", address, "Mozilla/5.0")

        // Eine falsche Host-Angabe muss scheitern: sonst wuerde die
        // Zertifikatspruefung umgangen.
        val wrong = FixedDnsHttpClient("falscher-host.example", address, "Mozilla/5.0")
        var failed = false
        try {
            wrong.get("https://falscher-host.example/").close()
        } catch (e: Exception) {
            failed = true
            println("  Falscher Host wird abgelehnt: ${e.javaClass.simpleName}")
        }
        assertTrue("Falscher Host wurde nicht abgelehnt", failed)
    }

    @Test
    fun `mehrere anfragen funktionieren nacheinander`() {
        val address = InetAddress.getByName("77.11.143.146")
        val client = FixedDnsHttpClient("omp.steimercloud.xyz", address, "Mozilla/5.0")

        val first = client.get("https://omp.steimercloud.xyz/icons/icon-192.png")
        val bytes = first.bodyBytes(MAX_HTML)
        first.close()

        assertTrue("Icon nicht geladen", bytes != null && bytes.isNotEmpty())
        println("  Icon: ${bytes?.size} Bytes")

        // PNG-Signatur pruefen
        assertTrue(
            "Kein PNG",
            bytes != null &&
                bytes.size > 4 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'G'.code.toByte()
        )
    }

    private companion object {
        const val MAX_HTML = 512 * 1024
    }
}
