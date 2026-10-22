package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Testet die Aufloesung ueber DNS-over-HTTPS.
 *
 * Der Anlass: Auf dem Geraet im LTE-Netz antwortete kein UDP-Resolver.
 * Mobilfunkbetreiber blockieren ausgehendes UDP/53 haeufig. DoH
 * laeuft ueber TCP/443 und wird deshalb durchgelassen.
 */
class DohResolutionTest {

    private data class Endpoint(val ip: String, val host: String, val path: String)

    /**
     * Nur Cloudflare beantwortet DoH ueber die IP-Adresse mit
     * application/dns-json. Google antwortet mit 400, Quad9 und
     * OpenResolv ebenso - geprueft, nicht angenommen.
     */
    private val endpoints = listOf(
        Endpoint("1.1.1.1", "cloudflare-dns.com", "/dns-query"),
        Endpoint("1.0.0.1", "cloudflare-dns.com", "/dns-query"),
        Endpoint("1.1.1.2", "cloudflare-dns.com", "/dns-query")
    )

    private fun queryDoh(host: String, endpoint: Endpoint): List<String> {
        val url = URL("https://${endpoint.ip}${endpoint.path}?name=$host&type=A")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/dns-json")
        connection.setRequestProperty("Host", endpoint.host)
        connection.setInstanceFollowRedirects(false)

        val code = connection.responseCode
        if (code != 200) {
            connection.disconnect()
            return emptyList()
        }
        val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        connection.disconnect()
        return parseDoh(body)
    }

    /**
     * Liest die IP-Adressen aus einer DoH-Antwort. Nur der
     * Answer-Block wird ausgewertet - im Authority-Block stehen bei
     * einem unbekannten Hostname SOA-Datensaetze mit Textwerten.
     */
    private fun parseDoh(body: String): List<String> {
        val answerStart = body.indexOf("\"Answer\"")
        if (answerStart < 0) return emptyList()

        var answerEnd = body.length
        for (section in listOf("\"Authority\"", "\"Additional\"")) {
            val at = body.indexOf(section, answerStart)
            if (at > 0 && at < answerEnd) answerEnd = at
        }
        val block = body.substring(answerStart, answerEnd)

        val result = mutableListOf<String>()
        var index = 0
        while (index < block.length) {
            val typeAt = block.indexOf("\"type\"", index)
            if (typeAt < 0) break
            val typeStart = block.indexOf(':', typeAt)
            if (typeStart < 0) break
            val typeEnd = block.indexOfAny(charArrayOf(',', '}'), typeStart)
            if (typeEnd < 0) break
            if (block.substring(typeStart + 1, typeEnd).trim() == "1") {
                val dataAt = block.indexOf("\"data\"", typeEnd)
                if (dataAt < 0) break
                val quoteStart = block.indexOf('"', block.indexOf(':', dataAt) + 1)
                if (quoteStart < 0) break
                val quoteEnd = block.indexOf('"', quoteStart + 1)
                if (quoteEnd < 0) break
                val address = block.substring(quoteStart + 1, quoteEnd)
                if (!address.contains(':') && address.count { it == '.' } == 3) {
                    result.add(address)
                }
            }
            index = typeEnd + 1
        }
        return result
    }

    @Test
    fun `doh ueber 1_1_1_1 liefert die ip der domain`() {
        val addresses = queryDoh("omp.steimercloud.xyz", endpoints[0])
        println("  1.1.1.1 -> $addresses")
        assertTrue("Keine Adresse erhalten", addresses.isNotEmpty())
        assertTrue("Falsche Adresse: $addresses", addresses.contains("77.11.143.146"))
    }

    @Test
    fun `mehrere cloudflare endpunkte liefern dasselbe ergebnis`() {
        val first = queryDoh("omp.steimercloud.xyz", endpoints[0])
        val second = queryDoh("omp.steimercloud.xyz", endpoints[1])
        val third = queryDoh("omp.steimercloud.xyz", endpoints[2])
        println("  1.1.1.1 -> $first")
        println("  1.0.0.1 -> $second")
        println("  1.1.1.2 -> $third")
        assertEquals("1.1.1.1 und 1.0.0.1 weichen ab", first, second)
        assertEquals("1.1.1.1 und 1.1.1.2 weichen ab", first, third)
    }

    @Test
    fun `antwort kommt ohne dns zustande`() {
        // Der Kernpunkt: die Verbindung entsteht ueber die IP. Der
        // Hostname des Resolvers wird nie aufgeloest - belegt
        // dadurch, dass die Abfrage mit gesetztem Host-Header
        // funktioniert.
        val addresses = queryDoh("omp.steimercloud.xyz", endpoints[1])
        println("  1.0.0.1 -> $addresses")
        assertTrue(
            "Zweiter Cloudflare-Endpunkt antwortet nicht",
            addresses.contains("77.11.143.146")
        )
    }

    @Test
    fun `unbekannter host liefert keine adresse`() {
        // Der Resolver antwortet mit einem Authority-Block voller
        // SOA-Datensaetze. Ohne Begrenzung auf den Answer-Block
        // wuerden die dort enthaltenen Textwerte als Treffer gelten.
        val addresses = queryDoh("gibt-es-diese-domain-nicht-4711.xyz", endpoints[0])
        println("  nicht existent -> $addresses")
        assertTrue("Unbekannter Host lieferte Adressen: $addresses", addresses.isEmpty())
    }

    @Test
    fun `doh und system resolver kommen zum selben ergebnis`() {
        val system = InetAddress.getAllByName("omp.steimercloud.xyz")
            .map { it.hostAddress }
            .toSet()
        val ueberDoh = queryDoh("omp.steimercloud.xyz", endpoints[0]).toSet()
        println("  System: $system")
        println("  DoH:    $ueberDoh")
        assertTrue(
            "DoH weicht vom System ab: $ueberDoh vs $system",
            ueberDoh.any { system.contains(it) }
        )
    }
}