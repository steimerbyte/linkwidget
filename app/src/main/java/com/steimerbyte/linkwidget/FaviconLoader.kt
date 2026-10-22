package com.steimerbyte.linkwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Laedt das Favicon einer Website direkt von der Website selbst.
 *
 * Die Icon-Kandidaten liefert die Bibliothek "touch-icon-extractor"
 * (net.mm2d:touchicon). Sie parst das HTML, wertet das Web-App-
 * Manifest aus und sortiert die Kandidaten nach Groesse und
 * rel-Prioritaet.
 *
 * Was in dieser Datei dazukommt, kann die Bibliothek nicht: das Icon
 * herunterladen und in ein Format bringen, das Android dekodieren
 * kann. Android kann kein SVG und kein ICO - beides kommt aber in
 * freier Wildbahn vor.
 *
 * Es wird KEIN Drittanbieter-Dienst eingeschaltet: Die aufgerufene
 * Domain ist ausschliesslich die, die der Benutzer selbst eingegeben
 * hat.
 *
 * **Alle HTTP-Zugriffe laufen ueber OkHttp**, nicht ueber
 * `HttpURLConnection`. Grund: Nur OkHttp laesst sich ein eigener
 * Resolver geben, der den System-Resolver umgeht. Auf dem Geraet
 * loest der System-Resolver nicht zuverlaessig auf, und
 * `HttpURLConnection` wuerde bei jedem Aufruf wieder genau ihn
 * befragen.
 *
 * Und: Der Hostname wird **nie** durch die IP ersetzt. TLS-SNI wird
 * daraus gebildet; mit IP als Hostname antwortet Nginx mit
 * TLSV1_ALERT_UNRECOGNIZED_NAME. Die feste Adresse steckt
 * ausschliesslich in der Namensaufloesung.
 */
object FaviconLoader {

    private const val TAG = "FaviconLoader"
    private const val CACHE_DIR = "favicons"
    private const val TIMEOUT_MS = 10000
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Maximale Groesse des HTML-Dokuments, das gelesen wird. */
    private const val MAX_HTML_BYTES = 512 * 1024

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Ermittelt den Host aus einer URL. Ohne Host (z.B. bei mailto)
     * wird null zurueckgegeben - dann gibt es kein Favicon.
     */
    fun hostOf(url: String): String? {
        return try {
            val parsed = java.net.URL(url)
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
     * erkannt wurde und ob am Ende ein Bild dasteht.
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
                // wuerde daraus "Seite liefert kein Icon".
                val client = clientFor(host, url, onProgress)
                if (client == null) {
                    onResult(FetchResult.Failed("Host nicht erreichbar"))
                    return@execute
                }

                val candidates = findIconCandidates(url, host, client, onProgress)
                if (candidates.isEmpty()) {
                    onProgress("keine Icon-Kandidaten - Fallback auf Standardpfade")
                }

                val outcome = downloadFaviconWithReport(
                    pageUrl = url,
                    candidates = candidates,
                    target = target,
                    client = client,
                    onProgress = onProgress
                )
                onResult(outcome)
            } catch (e: Exception) {
                Log.w(TAG, "Favicon-Abruf fehlgeschlagen fuer $url", e)
                onProgress("Fehler: ${describe(e)}")
                onResult(FetchResult.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * Ergebnis eines Abrufs.
     */
    sealed class FetchResult {
        data class Success(val bitmap: Bitmap?, val fileName: String) : FetchResult()
        data class Failed(val reason: String) : FetchResult()
    }

    /**
     * Baut den HTTP-Client und prueft, ob der Host erreichbar ist.
     *
     * Der Client loest jeden Host der Domain selbst auf. Icons liegen
     * haeufig nicht auf demselben Host wie die Seite - dann waere ein
     * Client, der nur einen Host umleitet, genau wieder vom
     * System-Resolver abhaengig.
     */
    private fun clientFor(
        host: String,
        url: String,
        onProgress: (String) -> Unit
    ): OkHttpClient? {
        onProgress("--- Umgebung ---")
        onProgress("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        onProgress("Geraet ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        onProgress("Ziel $url")

        onProgress("--- DNS ---")
        val addresses = DohResolver.lookup(host) { onProgress(it) }

        if (addresses == null || addresses.isEmpty()) {
            onProgress("DNS  keine Adresse erhalten")
            onProgress("")
            return null
        }

        onProgress("HTTP-Versuch ueber $host")
        val client = OkHttpClient.Builder()
            .dns(DomainDns(host))
            .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val reachable = try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                onProgress("HTTP ${response.code} von $host")
                response.code in 200..399
            }
        } catch (e: Exception) {
            onProgress("HTTP FEHLER: ${describe(e)}")
            false
        }

        onProgress(
            if (reachable) "Netzwerkzugriff moeglich"
            else "Netzwerkzugriff nicht moeglich - Icon kann nicht geladen werden"
        )
        onProgress("")

        return if (reachable) client else null
    }

    /**
     * Loest jeden Host auf, der zur Domain des Widgets gehoert.
     *
     * Die Pruefung erfolgt bewusst ueber die Domain: Icons liegen
     * haeufig auf "www.", einem CDN-Host oder einer Subdomain. Ein
     * Abgleich auf exakte Gleichheit wuerde bei
     * `https://cdn.steimercloud.xyz/favicon.png` wieder in den
     * System-Resolver zurueckfallen - und dort scheitert es.
     */
    private class DomainDns(private val host: String) : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            if (belongsToDomain(hostname, host)) {
                DohResolver.lookup(hostname) { }?.let { return it }
            }
            return okhttp3.Dns.SYSTEM.lookup(hostname)
        }

        private fun belongsToDomain(candidate: String, host: String): Boolean {
            val a = candidate.lowercase()
            val b = host.lowercase()
            if (a == b) return true
            if (a == "www.$b" || b == "www.$a") return true
            if (a.endsWith(".$b")) return true
            if (b.endsWith(".$a")) return true
            return false
        }
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
     */
    internal fun findIconCandidates(
        url: String,
        host: String,
        client: OkHttpClient,
        onProgress: (String) -> Unit
    ): List<String> {
        return try {
            val extractor = net.mm2d.touchicon.TouchIconExtractor(
                FixedDnsHttpClient(client, USER_AGENT)
            )
            extractor.userAgent = USER_AGENT
            extractor.downloadLimit = MAX_HTML_BYTES

            onProgress("HTML wird gelesen ...")
            val icons = extractor.fromPage(url, true)

            if (icons.isEmpty()) {
                // Leer heisst nicht "die Seite hat kein Icon": die
                // Bibliothek gibt bei einem Netzfehler ebenfalls eine
                // leere Liste zurueck, ohne zu werfen. Deshalb wird
                // die Seite einmal selbst geholt.
                val probe = fetchHtml(url, client, onProgress)
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
            // Flaeche nach rel-Prioritaet.
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
        client: OkHttpClient,
        onProgress: (String) -> Unit
    ): FetchResult {
        for ((index, candidate) in candidates.withIndex()) {
            onProgress("[$index] $candidate")
            val bytes = fetchBytes(candidate, client, onProgress) ?: continue
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

        // Standard-Fallbacks der Domain probieren.
        val base = try {
            val parsed = java.net.URL(pageUrl)
            "${parsed.protocol}://${parsed.host}"
        } catch (e: Exception) {
            onProgress("Basis-URL nicht bildbar: ${describe(e)}")
            null
        } ?: return FetchResult.Failed("Keine gueltige Adresse")

        for (fallback in listOf("/favicon.ico", "/favicon.png")) {
            onProgress("fallback  $base$fallback")
            val bytes = fetchBytes(base + fallback, client, onProgress) ?: continue
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
     * Holt das HTML einer Seite und gibt es als Text zurueck, oder
     * null, wenn die Seite nicht erreichbar ist.
     *
     * Der Rueckgabewert null bedeutet ausdruecklich: die Seite wurde
     * nicht gelesen. Das ist etwas voellig anderes als "die Seite
     * enthaelt kein Icon" und in der Ausgabe getrennt ausgewiesen.
     */
    private fun fetchHtml(
        url: String,
        client: OkHttpClient,
        onProgress: (String) -> Unit
    ): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onProgress("      HTTP ${response.code} (Seite nicht als HTML erreichbar)")
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                String(bytes.copyOf(MAX_HTML_BYTES), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            onProgress("      FEHLER: ${describe(e)}")
            null
        }
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
     * Die URL wird nicht veraendert: der Hostname muss erhalten
     * bleiben, weil TLS-SNI daraus gebildet wird. Die aufgeloeste
     * Adresse steckt ausschliesslich im Resolver des Clients.
     */
    private fun fetchBytes(
        url: String,
        client: OkHttpClient,
        onProgress: (String) -> Unit
    ): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onProgress("      HTTP ${response.code}")
                    return null
                }
                response.body?.bytes()
            }
        } catch (e: Exception) {
            // Die Ursache wird ausgegeben, nicht verschluckt.
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
     */
    private fun describe(e: Throwable): String {
        val head = e.javaClass.simpleName +
            (e.message?.let { ": $it" } ?: "")
        val cause = e.cause
        if (cause == null || cause === e) return head
        return "$head <- ${cause.javaClass.simpleName}: ${cause.message ?: "ohne Meldung"}"
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