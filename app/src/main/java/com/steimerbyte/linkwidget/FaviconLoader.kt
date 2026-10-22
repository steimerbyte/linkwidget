package com.steimerbyte.linkwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import net.mm2d.touchicon.TouchIconExtractor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Laedt das Favicon einer Website direkt von der Website selbst.
 *
 * Die Icon-Kandidaten liefert die Bibliothek "touch-icon-extractor"
 * (net.mm2d:touchicon). Sie parst das HTML, wertet das Web-App-Manifest
 * aus und sortiert die Kandidaten nach Groesse und rel-Prioritaet.
 *
 * Was in dieser Datei dazukommt, kann die Bibliothek nicht: das Icon
 * herunterladen und in ein Format bringen, das Android dekodieren
 * kann. Android kann kein SVG und kein ICO - beides kommt aber in
 * freier Wildbahn vor.
 *
 * Es wird KEIN Drittanbieter-Dienst eingeschaltet: Die aufgerufene
 * Domain ist ausschliesslich die, die der Benutzer selbst eingegeben
 * hat. Weder an Google noch an sonst wen wird etwas weitergegeben.
 */
object FaviconLoader {

    private const val TAG = "FaviconLoader"
    private const val CACHE_DIR = "favicons"
    private const val TIMEOUT_MS = 10000
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Maximale Groesse des HTML-Dokuments, das gelesen wird. */
    private const val MAX_HTML_BYTES = 512 * 1024


    /** Timeout fuer die eigene DNS-Abfrage an einen Resolver. */
    private const val DNS_TIMEOUT_MS = 4000

    /**
     * Alternative Resolver, falls der System-Resolver den Hostnamen
     * nicht aufloesen kann. Die IPs sind fest eingetragen: eine
     * Aufloesung ueber den Hostnamen waere zirkulaer.
     */
    private val FALLBACK_RESOLVERS = listOf(
        "1.1.1.1",
        "8.8.8.8",
        "9.9.9.9"
    )

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * Ermittelt den Host aus einer URL. Ohne Host (z.B. bei mailto)
     * wird null zurueckgegeben - dann gibt es kein Favicon.
     */
    fun hostOf(url: String): String? {
        return try {
            val parsed = URL(url)
            val host = parsed.host
            if (host.isNullOrBlank()) null else host.removePrefix("www.")
        } catch (e: Exception) {
            // Kein Log-Aufruf: hostOf wird von Unit-Tests aufgerufen, und
            // android.util.Log ist dort nicht mockbar ("not mocked").
            null
        }
    }

    /**
     * Laedt das Favicon im Hintergrund. Der Aufrufer bekommt eine
     * Benachrichtigung, sobald der Abruf durch ist.
     */
    fun fetchAsync(context: Context, url: String, onDone: () -> Unit) {
        fetchWithProgress(
            context = context,
            url = url,
            force = false,
            onProgress = { },
            onResult = { onDone() }
        )
    }

    /**
     * Laedt das Favicon mit Rueckmeldung an jeden Schritt.
     *
     * Wird fuer die Konfiguration benutzt, damit der Nutzer sieht,
     * was passiert: welche Adresse abgerufen wird, welches Format
     * gefunden wurde und ob am Ende ein Bild dasteht.
     *
     * @param force true ignoriert den Cache und laedt neu.
     */
    fun fetchWithProgress(
        context: Context,
        url: String,
        force: Boolean = false,
        onProgress: (String) -> Unit,
        onResult: (FetchResult) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                val host = hostOf(url)
                if (host == null) {
                    onProgress("Keine gueltige Adresse")
                    onResult(FetchResult.Failed("Keine gueltige Adresse"))
                    return@execute
                }

                val cacheDir = File(appContext.cacheDir, CACHE_DIR)
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val target = File(cacheDir, fileNameFor(host))

                if (!force && target.exists() && target.length() > 0L) {
                    onProgress("Aus dem Cache geladen")
                    onResult(FetchResult.Success(loadBitmap(context, url), target.name))
                    return@execute
                }

                // Ohne Netz ist jede weitere Angabe irrefuehrend. Die
                // Bibliothek liefert bei einem Netzfehler eine leere
                // Liste statt einer Ausnahme - ohne diese Pruefung
                // wuerde daraus "Seite liefert kein Icon", und genau
                // diese falsche Meldung stand bisher auf dem Geraet.
                val address = resolveAddress(host, url, onProgress)
                if (address == null) {
                    onResult(FetchResult.Failed("Host nicht erreichbar"))
                    return@execute
                }

                // Die Icon-Kandidaten liefert die Bibliothek
                // touch-icon-extractor. Sie parst das HTML, wertet das
                // Web-App-Manifest aus und sortiert nach Groesse und
                // rel-Prioritaet - also mehr, als der eigene Code
                // konnte (er fand das 512er-Icon im Manifest nicht).
                val candidates = findIconCandidates(url, address, onProgress)

                if (candidates.isEmpty()) {
                    onProgress("keine Icon-Kandidaten - Fallback auf Standardpfade")
                }

                val outcome = downloadFaviconWithReport(
                    url,
                    candidates,
                    target,
                    address,
                    onProgress
                )
                onResult(outcome)
            } catch (e: Exception) {
                Log.w(TAG, "Favicon-Abruf fehlgeschlagen fuer $url", e)
                onProgress("Fehler: ${e.message ?: e.javaClass.simpleName}")
                onResult(FetchResult.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * Ermittelt die Adresse des Hosts und prueft, ob er erreichbar
     * ist.
     *
     * Zurueckgegeben wird die **aufgeloeste Adresse**, nicht nur der
     * Name: alle nachfolgenden HTTP-Aufrufe brauchen sie, weil der
     * System-Resolver auf dem Geraet nicht zuverlaessig arbeitet.
     *
     * Gibt null zurueck, wenn der Host nicht erreichbar ist.
     */
    private fun resolveAddress(
        host: String,
        url: String,
        onProgress: (String) -> Unit
    ): java.net.InetAddress? {
        onProgress("--- Umgebung ---")
        onProgress("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        onProgress("Geraet ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        onProgress("Ziel $url")

        val addresses = resolve(host, onProgress)
        if (addresses == null) {
            onProgress("")
            return null
        }
        onProgress("DNS  ${addresses.joinToString(", ") { it.hostAddress ?: "?" }}")

        val address = addresses.first()

        // Reicht das Geraet ueberhaupt aus dem Haus heraus? Ohne
        // Antwort auf diese Frage ist jede weitere Angabe irrefuehrend.
        val reachable = try {
            val probe = URL("https://${address.hostAddress}").openConnection() as HttpURLConnection
            probe.connectTimeout = TIMEOUT_MS
            probe.readTimeout = TIMEOUT_MS
            probe.setRequestProperty("User-Agent", USER_AGENT)
            probe.setRequestProperty("Host", host)
            probe.setInstanceFollowRedirects(true)
            val code = probe.responseCode
            probe.disconnect()
            onProgress("HTTP $code von $host (${address.hostAddress})")
            code in 200..399
        } catch (e: Exception) {
            onProgress("HTTP FEHLER: ${describe(e)}")
            false
        }

        onProgress(
            if (reachable) "Netzwerkzugriff moeglich"
            else "Netzwerkzugriff nicht moeglich - Icon kann nicht geladen werden"
        )
        onProgress("")
        return if (reachable) address else null
    }

    /**
     * Loest den Hostnamen auf und liefert die Adressen.
     *
     * Zuerst der System-Resolver. Der kann auf dem Geraet scheitern,
     * obwohl der Hostname ueberall sonst aufloesbar ist - dann hilft
     * nur ein anderer Resolver.
     *
     * Deshalb wird bei einem Fehlschlag ueber die oeffentlichen
     * Resolver erneut aufgeloest, jeweils mit ausdruecklicher
     * Timeout-Uebergabe. Ohne diese Angabe wartet der System-Resolver
     * unter Umstaenden sehr lange, bevor er aufgibt - und auf einem
     * LTE-Netz mit captive Portal ist genau das der haeufigste
     * Zeitverschwender.
     *
     * Gibt null zurueck, wenn auch der letzte Versuch scheitert.
     */
    private fun resolve(
        host: String,
        onProgress: (String) -> Unit
    ): Array<java.net.InetAddress>? {
        try {
            val addresses = java.net.InetAddress.getAllByName(host)
            onProgress("DNS  System-Resolver: ${addresses.size} Adresse(n)")
            return addresses
        } catch (e: Exception) {
            onProgress("DNS  System-Resolver: ${describe(e)}")
        }

        // Alternative Resolver. Die IPs sind fest eingetragen, damit
        // die Aufloesung nicht selbst von DNS abhaengt.
        for (resolver in FALLBACK_RESOLVERS) {
            val address = resolveVia(host, resolver)
            if (address != null) {
                onProgress("DNS  $resolver: ${address.joinToString(", ") { it.hostAddress ?: "?" }}")
                return address
            }
            onProgress("DNS  $resolver: keine Antwort")
        }

        onProgress("DNS  alle Versuche gescheitert")
        return null
    }

    /**
     * Loest den Hostnamen ueber einen bestimmten DNS-Server auf.
     *
     * Die Abfrage geht als UDP-Paket direkt an den Resolver, nicht
     * ueber den System-Resolver. Nur so laesst sich ein Resolver
     * gezielt waehlen, wenn der System-Resolver den Namen nicht
     * aufloesen kann. Der eigene Timeout ist wichtig: der
     * System-Resolver kann unter Umstaenden sehr lange warten, bevor
     * er aufgibt.
     */
    private fun resolveVia(
        host: String,
        resolver: String
    ): Array<java.net.InetAddress>? {
        return try {
            val socket = java.net.DatagramSocket()
            try {
                socket.soTimeout = DNS_TIMEOUT_MS
                val query = buildDnsQuery(host)
                socket.send(
                    java.net.DatagramPacket(
                        query,
                        query.size,
                        java.net.InetAddress.getByName(resolver),
                        53
                    )
                )

                val buffer = ByteArray(512)
                val response = java.net.DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                parseDnsAddresses(buffer, response.length)
            } catch (e: Exception) {
                Log.d(TAG, "Aufloesung ueber $resolver nicht moeglich: $host", e)
                null
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Resolver $resolver nicht erreichbar", e)
            null
        }
    }

    /**
     * Baut eine DNS-Anfrage fuer einen A-Record.
     *
     * Aufbau: 12 Byte Kopf (Transaktions-ID, Flags, Frage), dann der
     * Name in DNS-Label-Form, danach Typ und Klasse.
     */
    private fun buildDnsQuery(host: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()

        // Transaktions-ID: zufaellig, damit Antworten zugeordnet
        // werden koennen.
        val transactionId = (Math.random() * 0xFFFF).toInt()
        output.write(transactionId shr 8 and 0xFF)
        output.write(transactionId and 0xFF)

        // Flags: Standardabfrage, Frage gewuenscht.
        output.write(0x01)
        output.write(0x00)

        // Fragen: genau eine.
        output.write(0x00)
        output.write(0x01)

        // Antworten, Autoritaet, Zusatz: jeweils null.
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)

        // Name in Label-Form: "a.b.c" -> 1a 1b 1c 00
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
     * Es werden nur A-Records (Typ 1) uebernommen. AAAA-Records
     * (Typ 28) werden bewusst ignoriert: die App braucht eine
     * IPv4-Adresse, weil das Zielhost nur IPv4 liefert.
     */
    private fun parseDnsAddresses(
        data: ByteArray,
        length: Int
    ): Array<java.net.InetAddress>? {
        // Der Kopf endet nach 12 Bytes. Danach kommt die Frage:
        // der Name, dann Typ und Klasse.
        var offset = 12
        while (offset < length && data[offset].toInt() != 0) {
            offset += data[offset].toInt() + 1
        }
        offset += 1
        offset += 4

        val questionCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val answerCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (answerCount <= 0) return null

        val addresses = mutableListOf<java.net.InetAddress>()

        // Die Frage ist bereits uebersprungen. Weitere Fragen gibt
        // es bei einer A-Abfrage nicht - jede zusaetzliche Frage
        // waere ein Fehler in der Antwort und wuerde die Auswertung
        // verschieben.

        // Dann die Antworten auslesen.
        for (a in 0 until answerCount) {
            if (offset >= length) break

            if ((data[offset].toInt() and 0xC0) == 0xC0) {
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
                addresses.add(java.net.InetAddress.getByAddress(ip))
            }
            offset += dataLength
        }

        return if (addresses.isEmpty()) null else addresses.toTypedArray()
    }

    /**
     * Ergebnis eines Abrufs.

     */
    sealed class FetchResult {
        data class Success(val bitmap: Bitmap?, val fileName: String) : FetchResult()
        data class Failed(val reason: String) : FetchResult()
    }

    /**
     * Ermittelt die Icon-Kandidaten einer Seite ueber die Bibliothek
     * touch-icon-extractor, der beste zuerst.
     *
     * Wichtig: Die Bibliothek liefert die Icons in Dokumentreihenfolge,
     * NICHT nach Guete sortiert. Viele Seiten bieten zuerst ein
     * 16x16-favicon.ico an und weiter hinten ein 192x192-PNG oder ein
     * Icon aus dem Web-App-Manifest. Ohne Sortierung wuerde genau das
     * kleine ICO gewinnen - das sieht auf dem Homescreen dann wie ein
     * verkleinerter, unscharfer Fleck aus.
     *
     * Sortiert wird nach Groesse, bei gleicher Groesse nach
     * rel-Prioritaet (apple-touch-icon vor icon vor shortcut-icon).
     */
    internal fun findIconCandidates(
        url: String,
        address: java.net.InetAddress?,
        onProgress: (String) -> Unit
    ): List<String> {
        return try {
            val extractor = TouchIconExtractor(httpClientFor(url, address))
            extractor.userAgent = USER_AGENT
            extractor.downloadLimit = MAX_HTML_BYTES

            onProgress("HTML wird gelesen ...")
            val icons = extractor.fromPage(url, true)

            if (icons.isEmpty()) {
                // Leer heisst nicht "die Seite hat kein Icon": die
                // Bibliothek gibt bei einem Netzfehler ebenfalls eine
                // leere Liste zurueck, ohne zu werfen. Deshalb wird
                // die Seite einmal selbst geholt - nur so laesst sich
                // "nicht erreichbar" von "kein Icon vorhanden"
                // unterscheiden.
                val probe = fetchHtml(url, address, onProgress)
                if (probe == null) {
                    onProgress("Seite nicht lesbar - Kandidaten koennen nicht ermittelt werden")
                    return emptyList()
                }
                onProgress("Seite gelesen (${probe.length} Zeichen), kein <link rel=icon> darin")
                return emptyList()
            }

            // Sortierung selbst, nicht ueber die Bibliothek: deren
            // Comparator-Felder sind in 0.9.9 nicht oeffentlich
            // zugaenglich. Verglichen wird nach Flaeche, bei gleicher
            // Flaeche nach rel-Prioritaet. Icons ohne erkennbare Groesse
            // (inferArea == 0) landen hinten, ein 16x16-ICO ist damit
            // nie der Gewinner, solange ein groesseres Icon existiert.
            val sorted = icons.sortedWith(
                compareByDescending<net.mm2d.touchicon.Icon> { it.inferArea() }
                    .thenBy { it.rel.priority }
            )

            onProgress("${icons.size} Icons erkannt, nach Groesse sortiert:")
            sorted.forEach { icon ->
                val size = if (icon.inferArea() > 0) "${icon.inferArea()} px²" else "unbekannt"
                onProgress("  ${icon.rel} $size ${icon.url}")
            }

            sorted.map { it.url }
        } catch (e: Exception) {
            // Auch hier darf die Ausnahme nicht verschluckt werden:
            // Auf dem Geraet ist dies der Punkt, an dem sich zeigt,
            // ob Netz, TLS oder das HTML die Ursache ist.
            Log.w(TAG, "Icon-Kandidaten konnten nicht ermittelt werden: $url", e)
            onProgress("FEHLER beim Lesen der Seite:")
            onProgress("  ${describe(e)}")
            emptyList()
        }
    }

    /**
     * Liefert den HTTP-Client fuer die Bibliothek.
     *
     * Ist die Adresse bekannt, wird ein Client verwendet, der genau
     * diese Adresse benutzt - der System-Resolver wird nicht mehr
     * gefragt. Ist sie unbekannt, kommt der Standard-Client zum
     * Einsatz.
     */
    private fun httpClientFor(
        url: String,
        address: java.net.InetAddress?
    ): net.mm2d.touchicon.http.HttpClientAdapter {
        if (address == null) {
            return net.mm2d.touchicon.http.simple.SimpleHttpClientAdapterFactory.create()
        }
        val host = hostOf(url) ?: return net.mm2d.touchicon.http.simple.SimpleHttpClientAdapterFactory.create()
        return FixedDnsHttpClient(host, address, USER_AGENT)
    }

    /**
     * Holt das HTML einer Seite und gibt es als Text zurueck, oder
     * null, wenn die Seite nicht erreichbar ist.
     *
     * Der Rueckgabewert null bedeutet ausdruecklich: die Seite wurde
     * nicht gelesen. Das ist etwas voellig anderes als "die Seite
     * enthaelt kein Icon" und in der Ausgabe getrennt ausgewiesen.
     */
    private fun fetchHtml(
        url: String,
        address: java.net.InetAddress?,
        onProgress: (String) -> Unit
    ): String? {
        return try {
            val connection = openConnection(url, address)
            val code = connection.responseCode
            if (code !in 200..299) {
                onProgress("      HTTP $code (Seite nicht als HTML erreichbar)")
                return null
            }
            connection.inputStream.use { stream ->
                stream.readNBytes(MAX_HTML_BYTES).toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            onProgress("      FEHLER: ${describe(e)}")
            null
        }
    }

    /**
     * Laedt das Favicon der Website und meldet jeden Schritt.
     *
     * Reihenfolge:
     *  1. Die von der Bibliothek gelieferten Kandidaten, der beste zuerst
     *  2. Bei ICO: das groesste dekodierbare Bild daraus
     *  3. Bei SVG (nicht dekodierbar): /favicon.ico der Domain
     *  4. Notfalls: /favicon.png der Domain
     */
    private fun downloadFaviconWithReport(
        pageUrl: String,
        candidates: List<String>,
        target: File,
        address: java.net.InetAddress?,
        onProgress: (String) -> Unit
    ): FetchResult {
        for ((index, candidate) in candidates.withIndex()) {
            onProgress("[$index] $candidate")
            val bytes = fetchBytes(candidate, address, onProgress) ?: continue
            if (bytes.isEmpty()) {
                onProgress("      Datei leer - uebersprungen")
                continue
            }

            val kind = ImageFormat.detect(bytes)
            onProgress("      ${bytes.size} Bytes, Format $kind")

            when (kind) {
                ImageFormat.Kind.PNG,
                ImageFormat.Kind.JPEG,
                ImageFormat.Kind.GIF,
                ImageFormat.Kind.WEBP -> {
                    if (saveIfDecodable(bytes, target)) {
                        onProgress("      -> ${target.length()} Bytes gespeichert")
                        return FetchResult.Success(loadBitmap(target), target.name)
                    }
                    onProgress("      Android konnte das nicht dekodieren")
                }

                ImageFormat.Kind.ICO -> {
                    onProgress("      ICO wird entpackt ...")
                    val extracted = IcoExtractor.extractLargestImage(bytes)
                    if (extracted == null) {
                        onProgress("      ICO enthaelt kein verwertbares Bild")
                    } else if (saveIfDecodable(extracted, target)) {
                        onProgress("      -> groesstes ICO-Bild gespeichert")
                        return FetchResult.Success(loadBitmap(target), target.name)
                    } else {
                        onProgress("      entpacktes ICO nicht dekodierbar")
                    }
                }

                ImageFormat.Kind.SVG -> {
                    onProgress("      SVG kann Android nicht anzeigen - weiter")
                }

                ImageFormat.Kind.UNKNOWN -> {
                    onProgress("      Unbekanntes Format - weiter")
                }
            }
        }

        // Standard-Fallbacks der Domain probieren. Der Pfad wird aus
        // der Seiten-URL gebildet, nicht aus dem Host: sonst ginge die
        // Angabe verloren, wenn die Seite ueber www laeuft.
        val base = try {
            val parsed = URL(pageUrl)
            "${parsed.protocol}://${parsed.host}"
        } catch (e: Exception) {
            onProgress("Basis-URL nicht bildbar: ${describe(e)}")
            null
        } ?: return FetchResult.Failed("Keine gueltige Adresse")

        for (fallback in listOf("/favicon.ico", "/favicon.png")) {
            onProgress("fallback  $base$fallback")
            val bytes = fetchBytes(base + fallback, address, onProgress) ?: continue
            if (bytes.isEmpty()) {
                onProgress("      Datei leer - uebersprungen")
                continue
            }

            val kind = ImageFormat.detect(bytes)
            onProgress("      ${bytes.size} Bytes, Format $kind")

            val payload = if (kind == ImageFormat.Kind.ICO) {
                onProgress("      ICO wird entpackt ...")
                IcoExtractor.extractLargestImage(bytes)
            } else {
                bytes
            }

            if (payload == null) {
                onProgress("      ICO enthaelt kein verwertbares Bild")
                continue
            }

            if (saveIfDecodable(payload, target)) {
                onProgress("      -> ${target.length()} Bytes gespeichert")
                return FetchResult.Success(loadBitmap(target), target.name)
            }
            onProgress("      Android konnte das nicht dekodieren")
        }

        onProgress("Ergebnis: kein verwendbares Icon")
        return FetchResult.Failed("Kein verwendbares Icon gefunden")
    }

    /**
     * Speichert die Bilddaten, wenn Android sie dekodieren kann.
     * Gibt true zurueck, wenn gespeichert wurde.
     */
    private fun saveIfDecodable(bytes: ByteArray, target: File): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            Log.d(TAG, "Bild konnte nicht dekodiert werden (${bytes.size} Bytes)")
            return false
        }
        target.writeBytes(bytes)
        Log.d(TAG, "Favicon gespeichert (${bytes.size} Bytes)")
        return true
    }

    /**
     * Laedt eine URL als Bytes (fuer das Icon).
     *
     * Ist eine Adresse uebergeben, wird diese verwendet und der
     * Hostname nicht erneut aufgeloest. Sonst wuerde genau der
     * Resolver wieder befragt, der gerade gescheitert ist.
     */
    private fun fetchBytes(
        url: String,
        address: java.net.InetAddress?,
        onProgress: (String) -> Unit
    ): ByteArray? {
        return try {
            val connection = openConnection(url, address)
            val code = connection.responseCode
            if (code !in 200..299) {
                onProgress("      HTTP $code")
                return null
            }
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            // Die Ursache wird ausgegeben, nicht verschluckt. Ohne das
            // bleibt auf dem Geraet nur "Kein verwendbares Icon gefunden"
            // stehen und die Ursache ist nicht erkennbar.
            onProgress("      FEHLER: ${describe(e)}")
            Log.w(TAG, "Icon konnte nicht geladen werden: $url", e)
            null
        }
    }

    /**
     * Beschreibt eine Ausnahme in einer Form, die beim Lesen
     * weiterhilft: Typ, Meldung und - falls vorhanden - die Ursache.
     *
     * Bei Netzwerkfehlern steckt die eigentliche Information fast
     * immer in der Cause-Kette, nicht in der aeussersten Exception.
     * "Unable to resolve host" etwa ist die Ursache von
     * "IOException", nicht dessen Meldung.
     */
    private fun describe(e: Throwable): String {
        val head = e.javaClass.simpleName +
            (e.message?.let { ": $it" } ?: "")
        val cause = e.cause
        if (cause == null || cause === e) return head
        return "$head <- ${cause.javaClass.simpleName}: ${cause.message ?: "ohne Meldung"}"
    }

    /**
     * Oeffnet eine Verbindung.
     *
     * Mit Adresse wird direkt diese IP angesprochen. Der Hostname
     * bleibt im Host-Header, damit die Zertifikatspruefung und
     * virtuelle Hosts funktionieren.
     */
    private fun openConnection(
        url: String,
        address: java.net.InetAddress?
    ): HttpURLConnection {
        val target = if (address != null) {
            val parsed = URL(url)
            "${parsed.protocol}://${address.hostAddress}${parsed.port.let { if (it == -1) "" else ":$it" }}${parsed.file}"
        } else {
            url
        }

        val connection = URL(target).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "*/*")
        if (address != null) {
            // Ohne Host-Header wuerde der Server die Anfrage unter der
            // IP-Adresse fuehren und ein anderes Zertifikat liefern.
            hostOf(url)?.let { connection.setRequestProperty("Host", it) }
        }
        connection.setInstanceFollowRedirects(true)
        return connection
    }

    /**
     * Liest das gecachte Favicon als Bitmap. Gibt null zurueck, wenn noch
     * keines geladen wurde oder die Datei kaputt ist.
     */
    fun loadBitmap(context: Context, url: String): Bitmap? {
        val host = hostOf(url) ?: return null
        val file = File(File(context.applicationContext.cacheDir, CACHE_DIR), fileNameFor(host))
        return loadBitmap(file)
    }

    /**
     * Liest eine Bitmap direkt aus einer Datei.
     */
    fun loadBitmap(file: File): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Favicon konnte nicht gelesen werden: ${file.name}", e)
            null
        }
    }

    /**
     * Dateiname aus dem Host. Nicht der Host selbst, sondern ein Hash -
     * so sind keine Hostnamen im Dateisystem sichtbar.
     */
    private fun fileNameFor(host: String): String {
        val hash = host.hashCode().toUInt().toString(16)
        return "favicon_$hash.png"
    }
}
