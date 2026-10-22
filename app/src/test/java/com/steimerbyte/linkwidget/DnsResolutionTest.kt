package com.steimerbyte.linkwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Testet die eigene DNS-Aufloesung.
 *
 * Die App loest Hostnamen notfalls ueber eigene DNS-Anfragen an
 * oeffentliche Resolver auf. Grund: auf dem Geraet schlug die
 * Aufloesung ueber den System-Resolver fehl, obwohl der Name
 * ueberall sonst aufloesbar war.
 *
 * Die Abfrage- und Antwortverarbeitung wird hier gegen echte
 * Resolver geprueft. Eine eigene DNS-Implementierung ohne Pruefung
 * waere eine Fehlerquelle fuer sich.
 */
class DnsResolutionTest {

    /**
     * Baut eine DNS-Anfrage fuer einen A-Record.
     */
    private fun buildQuery(host: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val transactionId = (Math.random() * 0xFFFF).toInt()
        output.write(transactionId shr 8 and 0xFF)
        output.write(transactionId and 0xFF)
        output.write(0x01)
        output.write(0x00)
        output.write(0x00)
        output.write(0x01)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        for (label in host.split('.')) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.UTF_8)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0x00)
        output.write(0x00)
        output.write(0x01)
        output.write(0x00)
        output.write(0x01)
        return output.toByteArray()
    }

    /**
     * Liest die A-Records aus einer DNS-Antwort.
     */
    private fun parseAddresses(data: ByteArray, length: Int): List<String> {
        val result = mutableListOf<String>()

        // Kopf: 12 Bytes. Danach die Frage: Name, dann Typ und Klasse.
        // Die Frage wird genau einmal uebersprungen.
        var offset = 12
        while (offset < length && data[offset].toInt() != 0) {
            offset += data[offset].toInt() + 1
        }
        offset += 1
        offset += 4

        val answerCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (answerCount <= 0) return result

        for (a in 0 until answerCount) {
            if (offset >= length) break

            if ((data[offset].toInt() and 0xC0) == 0xC0) {
                // Komprimierter Name: Zeiger auf eine fruehere Stelle.
                offset += 2
            } else {
                while (offset < length && data[offset].toInt() != 0) {
                    offset += data[offset].toInt() + 1
                }
                offset += 1
            }

            if (offset + 10 > length) break
            val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val dataLength = ((data[offset + 8].toInt() and 0xFF) shl 8) or
                (data[offset + 9].toInt() and 0xFF)
            offset += 10

            if (type == 1 && dataLength == 4 && offset + 4 <= length) {
                result.add(
                    "${data[offset].toInt() and 0xFF}." +
                        "${data[offset + 1].toInt() and 0xFF}." +
                        "${data[offset + 2].toInt() and 0xFF}." +
                        "${data[offset + 3].toInt() and 0xFF}"
                )
            }
            offset += dataLength
        }
        return result
    }

    /**
     * Fragt einen Resolver direkt und gibt die gefundenen IPs zurueck.
     */
    private fun query(host: String, resolver: String): List<String> {
        val socket = DatagramSocket()
        try {
            socket.soTimeout = 5000
            val query = buildQuery(host)
            socket.send(
                DatagramPacket(query, query.size, InetAddress.getByName(resolver), 53)
            )
            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return parseAddresses(buffer, response.length)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `eigene dns abfrage liefert die ip der domain`() {
        val addresses = query("omp.steimercloud.xyz", "1.1.1.1")
        println("  1.1.1.1 -> $addresses")
        assertTrue("Keine Adresse erhalten", addresses.isNotEmpty())
        assertTrue(
            "Falsche Adresse: $addresses",
            addresses.contains("77.11.143.146")
        )
    }

    @Test
    fun `abfrage ueber mehrere resolver liefert dasselbe ergebnis`() {
        val viaCloudflare = query("omp.steimercloud.xyz", "1.1.1.1")
        val viaGoogle = query("omp.steimercloud.xyz", "8.8.8.8")
        println("  1.1.1.1 -> $viaCloudflare")
        println("  8.8.8.8 -> $viaGoogle")
        assertEquals(
            "Die Resolver muessen uebereinstimmen",
            viaCloudflare,
            viaGoogle
        )
    }

    @Test
    fun `unbekannter host liefert keine adresse`() {
        val addresses = query("gibt-es-diese-domain-nicht-4711.xyz", "1.1.1.1")
        println("  nicht existent -> $addresses")
        assertTrue("Unbekannter Host lieferte Adressen", addresses.isEmpty())
    }

    @Test
    fun `system resolver und eigene abfrage kommen zum selben ergebnis`() {
        val system = InetAddress.getAllByName("omp.steimercloud.xyz")
            .map { it.hostAddress }
            .toSet()
        val eigene = query("omp.steimercloud.xyz", "9.9.9.9").toSet()
        println("  System:  $system")
        println("  Eigen:   $eigene")
        assertTrue(
            "Eigene Abfrage weicht vom System ab: $eigene vs $system",
            eigene.any { system.contains(it) }
        )
    }
}