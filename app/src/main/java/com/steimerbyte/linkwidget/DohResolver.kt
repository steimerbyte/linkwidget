package com.steimerbyte.linkwidget

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Loest Hostnamen auf, ohne den System-Resolver zu befragen.
 *
 * Reihenfolge:
 *
 *  1. **DNS ueber HTTPS**, direkt an die IP-Adresse des Resolvers.
 *     Mobilfunkbetreiber blockieren ausgehendes UDP/53 haeufig;
 *     DoH laeuft ueber TCP/443 und wird durchgelassen. Der Resolver
 *     wird ueber seine IP angesprochen, weil die Aufloesung sonst
 *     wieder DNS benoetigen wuerde.
 *  2. **Rohe DNS-Abfrage** per UDP, fuer Netze, in denen HTTPS
 *     zu den oeffentlichen Resolvern gesperrt ist.
 *  3. **System-Resolver**, als letzte Moeglichkeit. Auf einem
 *     gesunden Geraet ist er die schnellste Variante und deshalb
 *     nicht zu beanstanden - nur eben nicht verlaesslich.
 *
 * Bewusst keine Geraeteebene-Umgehung (`VpnService`, Private DNS):
 * Beides wuerde Aerger machen, weil die App ihre DNS-Einstellungen
 * nicht kontrolliert.
 */
object DohResolver {

    private const val TIMEOUT_MS = 4000

    /** Timeout fuer die eigene DNS-Abfrage an einen Resolver. */
    private const val DNS_TIMEOUT_MS = 4000

    private data class DohEndpoint(
        val ip: String,
        val host: String,
        val path: String
    )

    /**
     * Nur Cloudflare beantwortet DoH ueber die IP-Adresse mit
     * application/dns-json. Google antwortet mit 400, Quad9 und
     * OpenResolv ebenso - geprueft, nicht angenommen.
     */
    private val dohEndpoints = listOf(
        DohEndpoint("1.1.1.1", "cloudflare-dns.com", "/dns-query"),
        DohEndpoint("1.0.0.1", "cloudflare-dns.com", "/dns-query"),
        DohEndpoint("1.1.1.2", "cloudflare-dns.com", "/dns-query"),
        DohEndpoint("1.0.0.2", "cloudflare-dns.com", "/dns-query")
    )

    private val plainResolvers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    /**
     * Loest einen Hostnamen auf. Gibt null zurueck, wenn keine
     * Variante eine Antwort liefert.
     *
     * @param onStep wird fuer jeden Versuch aufgerufen, damit die
     *   Terminal-Ausgabe zeigt, was versucht wurde.
     */
    fun lookup(host: String, onStep: (String) -> Unit): List<InetAddress>? {
        for (endpoint in dohEndpoints) {
            val result = viaDoh(host, endpoint, onStep)
            if (result != null) {
                onStep("  ${endpoint.ip}: ${result.joinToString(", ") { it.hostAddress ?: "?" }}")
                return result
            }
        }

        for (resolver in plainResolvers) {
            val result = viaUdp(host, resolver, onStep)
            if (result != null) {
                onStep("  $resolver (udp): ${result.joinToString(", ") { it.hostAddress ?: "?" }}")
                return result
            }
        }

        try {
            val result = InetAddress.getAllByName(host).toList()
            onStep("  System: ${result.joinToString(", ") { it.hostAddress ?: "?" }}")
            return result
        } catch (e: Exception) {
            onStep("  System: ${e.javaClass.simpleName}")
            return null
        }
    }

    /**
     * DNS ueber HTTPS, direkt an die IP des Resolvers.
     */
    private fun viaDoh(
        host: String,
        endpoint: DohEndpoint,
        onStep: (String) -> Unit
    ): List<InetAddress>? {
        return try {
            val url = java.net.URL(
                "https://${endpoint.ip}${endpoint.path}?name=$host&type=A"
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0"
            )
            connection.setRequestProperty("Accept", "application/dns-json")
            // SNI muss der Hostname des Zertifikats sein. Ohne
            // diesen Header schlaegt die Zertifikatspruefung fehl.
            connection.setRequestProperty("Host", endpoint.host)
            connection.setInstanceFollowRedirects(false)

            val code = connection.responseCode
            if (code != 200) {
                onStep("  ${endpoint.ip}: HTTP $code")
                connection.disconnect()
                return null
            }

            val body = connection.inputStream.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            connection.disconnect()
            parseDohResponse(body)
        } catch (e: Exception) {
            onStep("  ${endpoint.ip}: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Liest die A-Records aus einer DoH-Antwort.
     *
     * Nur der Answer-Block wird ausgewertet. Bei einem unbekannten
     * Hostname antwortet der Resolver mit einem Authority-Block, in
     * dem SOA-Datensaetze stehen - dort stehen Textwerte wie
     * "ns0.example.net", keine IP-Adressen.
     */
    private fun parseDohResponse(body: String): List<InetAddress>? {
        val answerStart = body.indexOf("\"Answer\"")
        if (answerStart < 0) return null

        var answerEnd = body.length
        for (section in listOf("\"Authority\"", "\"Additional\"")) {
            val at = body.indexOf(section, answerStart)
            if (at > 0 && at < answerEnd) answerEnd = at
        }

        val block = body.substring(answerStart, answerEnd)
        val result = mutableListOf<InetAddress>()
        var index = 0

        while (index < block.length) {
            val typeAt = block.indexOf("\"type\"", index)
            if (typeAt < 0) break
            val typeStart = block.indexOf(':', typeAt)
            if (typeStart < 0) break
            val typeEnd = block.indexOfAny(charArrayOf(',', '}'), typeStart)
            if (typeEnd < 0) break

            if (block.substring(typeStart + 1, typeEnd).trim() != "1") {
                index = typeEnd + 1
                continue
            }

            val dataAt = block.indexOf("\"data\"", typeEnd)
            if (dataAt < 0) break
            val quoteStart = block.indexOf('"', block.indexOf(':', dataAt) + 1)
            if (quoteStart < 0) break
            val quoteEnd = block.indexOf('"', quoteStart + 1)
            if (quoteEnd < 0) break
            val address = block.substring(quoteStart + 1, quoteEnd)

            // Nur IPv4. IPv6 wuerde eine Zone und Komprimierung
            // aufloesen muessen - das uebernimmt getAllByName, wenn
            // es funktioniert.
            if (!address.contains(':') && address.count { it == '.' } == 3) {
                try {
                    result.add(InetAddress.getByName(address))
                } catch (e: Exception) {
                    // Nicht interpretierbar - ueberspringen.
                }
            }

            index = quoteEnd + 1
        }

        return if (result.isEmpty()) null else result
    }

    /**
     * Rohe DNS-Abfrage per UDP an einen Resolver.
     */
    private fun viaUdp(
        host: String,
        resolver: String,
        onStep: (String) -> Unit
    ): List<InetAddress>? {
        return try {
            val socket = DatagramSocket()
            try {
                socket.soTimeout = DNS_TIMEOUT_MS
                val query = buildDnsQuery(host)
                socket.send(
                    DatagramPacket(query, query.size, InetAddress.getByName(resolver), 53)
                )
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                parseDnsResponse(buffer, response.length)
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            onStep("  $resolver (udp): keine Antwort")
            null
        }
    }

    /**
     * Baut eine DNS-Anfrage fuer einen A-Record.
     */
    private fun buildDnsQuery(host: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        val transactionId = (Math.random() * 0xFFFF).toInt()
        output.write(transactionId shr 8 and 0xFF)
        output.write(transactionId and 0xFF)

        // Standardabfrage, Frage gewuenscht, eine Frage.
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

        // Typ A, Klasse IN
        output.write(0x00)
        output.write(0x01)
        output.write(0x00)
        output.write(0x01)

        return output.toByteArray()
    }

    /**
     * Liest die A-Records aus einer DNS-Antwort.
     *
     * Der Antwortname liegt meist als Kompressionszeiger vor
     * (2 Bytes, oberes Bit gesetzt). Die Frage am Anfang wird genau
     * einmal uebersprungen.
     */
    private fun parseDnsResponse(data: ByteArray, length: Int): List<InetAddress>? {
        // Kopf 12 Bytes, dann die Frage: Name, Typ, Klasse.
        var offset = 12
        while (offset < length && data[offset].toInt() != 0) {
            offset += data[offset].toInt() + 1
        }
        offset += 1
        offset += 4

        val answerCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (answerCount <= 0) return null

        val result = mutableListOf<InetAddress>()

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
                val ip = byteArrayOf(
                    data[offset], data[offset + 1], data[offset + 2], data[offset + 3]
                )
                try {
                    result.add(InetAddress.getByAddress(ip))
                } catch (e: Exception) {
                    // Nicht interpretierbar - ueberspringen.
                }
            }
            offset += dataLength
        }

        return if (result.isEmpty()) null else result
    }
}