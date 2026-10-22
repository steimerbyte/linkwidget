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

                onProgress("Seite wird abgerufen: $url")

                // Die Icon-Kandidaten liefert die Bibliothek
                // touch-icon-extractor. Sie parst das HTML, wertet das
                // Web-App-Manifest aus und sortiert nach Groesse und
                // rel-Prioritaet - also mehr, als der eigene Code
                // konnte (er fand das 512er-Icon im Manifest nicht).
                val candidates = findIconCandidates(url, onProgress)

                if (candidates.isEmpty()) {
                    onProgress("Keine Icon-Kandidaten gefunden - Fallback")
                } else {
                    onProgress("${candidates.size} Icon-Kandidaten gefunden")
                    candidates.forEach { candidate ->
                        onProgress("  Kandidat: $candidate")
                    }
                }

                val outcome = downloadFaviconWithReport(
                    url,
                    candidates,
                    target,
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
        onProgress: (String) -> Unit
    ): List<String> {
        return try {
            val extractor = TouchIconExtractor()
            extractor.userAgent = USER_AGENT
            extractor.downloadLimit = MAX_HTML_BYTES

            val icons = extractor.fromPage(url, true)
            if (icons.isEmpty()) {
                onProgress("Seite geladen, keine Icons erkannt")
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

            onProgress("Seite geladen, ${icons.size} Icons erkannt")
            sorted.forEach { icon ->
                val size = if (icon.inferArea() > 0) "${icon.inferArea()} px" else "Groesse unbekannt"
                onProgress("  ${icon.rel} $size ${icon.url}")
            }
            sorted.map { it.url }
        } catch (e: Exception) {
            Log.w(TAG, "Icon-Kandidaten konnten nicht ermittelt werden: $url", e)
            onProgress("Kandidaten nicht ermittelbar: ${e.message ?: e.javaClass.simpleName}")
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
        onProgress: (String) -> Unit
    ): FetchResult {
        for (candidate in candidates) {
            onProgress("Lade: $candidate")
            val bytes = fetchBytes(candidate) ?: continue
            if (bytes.isEmpty()) continue

            val kind = ImageFormat.detect(bytes)
            onProgress("Format: $kind (${bytes.size} Bytes)")

            when (kind) {
                ImageFormat.Kind.PNG,
                ImageFormat.Kind.JPEG,
                ImageFormat.Kind.GIF,
                ImageFormat.Kind.WEBP -> {
                    if (saveIfDecodable(bytes, target)) {
                        onProgress("Gespeichert.")
                        return FetchResult.Success(loadBitmap(target), target.name)
                    }
                }

                ImageFormat.Kind.ICO -> {
                    onProgress("ICO wird entpackt ...")
                    val extracted = IcoExtractor.extractLargestImage(bytes)
                    if (extracted != null && saveIfDecodable(extracted, target)) {
                        onProgress("Groesstes Bild aus ICO gespeichert.")
                        return FetchResult.Success(loadBitmap(target), target.name)
                    }
                }

                ImageFormat.Kind.SVG -> {
                    onProgress("SVG kann Android nicht anzeigen - weiter")
                }

                ImageFormat.Kind.UNKNOWN -> {
                    onProgress("Unbekanntes Format - weiter")
                }
            }
        }

        // Standard-Fallbacks der Domain probieren.
        val base = try {
            val parsed = URL(pageUrl)
            "${parsed.protocol}://${parsed.host}"
        } catch (e: Exception) {
            null
        } ?: return FetchResult.Failed("Keine gueltige Adresse")

        for (fallback in listOf("/favicon.ico", "/favicon.png")) {
            onProgress("Fallback: $fallback")
            val bytes = fetchBytes(base + fallback) ?: continue
            if (bytes.isEmpty()) continue

            val payload = if (ImageFormat.detect(bytes) == ImageFormat.Kind.ICO) {
                IcoExtractor.extractLargestImage(bytes)
            } else {
                bytes
            } ?: continue

            if (saveIfDecodable(payload, target)) {
                onProgress("Gespeichert aus $fallback.")
                return FetchResult.Success(loadBitmap(target), target.name)
            }
        }

        onProgress("Kein verwendbares Icon gefunden")
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
     */
    private fun fetchBytes(url: String): ByteArray? {
        return try {
            val connection = openConnection(url)
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Icon konnte nicht geladen werden: $url", e)
            null
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "*/*")
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
