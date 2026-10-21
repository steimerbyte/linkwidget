package com.steimerbyte.linkwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Laedt das Favicon einer Website direkt von der Website selbst.
 *
 * Vorgehen - so wie es ein Browser macht:
 *  1. Die Seite wird abgerufen
 *  2. Aus dem <head> wird das passende <link rel="icon"> ausgelesen
 *  3. Das Icon wird von der im HTML genannten Adresse geladen
 *
 * Es wird KEIN Drittanbieter-Dienst eingeschaltet: Die aufgerufene Domain
 * ist ausschliesslich die, die der Benutzer selbst eingegeben hat. Weder
 * an Google noch an sonst wen wird etwas weitergegeben.
 *
 * Relativierte Pfade im HTML werden gegen die Seiten-URL aufgeloest.
 */
object FaviconLoader {

    private const val TAG = "FaviconLoader"
    private const val CACHE_DIR = "favicons"
    private const val TIMEOUT_MS = 8000
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** Groesse des HTML-Dokuments, das gelesen wird. Reicht fuer den head. */
    private const val MAX_HTML_BYTES = 512 * 1024

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Reihenfolge der Praeferenz. Mehrere rel-Varianten kommen in der
     * Praxis vor ("icon", "shortcut icon", "fluid-icon", "apple-touch-icon").
     * "apple-touch-icon" ist ein guter Fallback, weil es ein grosses,
     * quadratisches PNG ist, das auch klein gut aussieht.
     */
    private val ICON_LINK_PATTERN: Pattern = Pattern.compile(
        """<link\s[^>]*rel\s*=\s*["']([^"']*)["'][^>]*>""",
        Pattern.CASE_INSENSITIVE
    )

    private val HREF_PATTERN: Pattern = Pattern.compile(
        """href\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Reihenfolge, in der die rel-Werte bevorzugt werden, wenn die
     * Groesse gleich ist. "fluid-icon" ist modern und gut designt,
     * "apple-touch-icon" ist meist ein grosses quadratisches PNG.
     */
    private val REL_PREFERENCE = listOf(
        "fluid-icon",
        "apple-touch-icon",
        "shortcut icon",
        "icon",
        "mask-icon"
    )

    private val SIZES_PATTERN: Pattern = Pattern.compile(
        """sizes\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

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
        val appContext = context.applicationContext
        executor.execute {
            try {
                val host = hostOf(url)
                if (host != null) {
                    val cacheDir = File(appContext.cacheDir, CACHE_DIR)
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val target = File(cacheDir, fileNameFor(host))

                    // Schon im Cache? Dann nichts neu laden.
                    if (!target.exists() || target.length() == 0L) {
                        downloadFavicon(url, target)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Favicon-Abruf fehlgeschlagen fuer $url", e)
            }
            onDone()
        }
    }

    /**
     * Laedt das Favicon der Website.
     *
     * Reihenfolge:
     *  1. Das im HTML genannte Icon
     *  2. Bei SVG oder ICO: das groesste dekodierbare Bild daraus
     *  3. Bei SVG (nicht dekodierbar): /favicon.ico der Domain
     *  4. Notfalls: /favicon.png der Domain
     */
    private fun downloadFavicon(pageUrl: String, target: File) {
        val page = fetchString(pageUrl)

        val iconUrl = if (page != null) findIconUrl(page, pageUrl) else null

        if (iconUrl != null) {
            val bytes = fetchBytes(iconUrl)
            if (bytes != null && bytes.isNotEmpty()) {
                when (ImageFormat.detect(bytes)) {
                    ImageFormat.Kind.PNG,
                    ImageFormat.Kind.JPEG,
                    ImageFormat.Kind.GIF,
                    ImageFormat.Kind.WEBP -> {
                        if (saveIfDecodable(bytes, target)) return
                    }

                    ImageFormat.Kind.ICO -> {
                        // ICO kann Android nicht dekodieren, aber die
                        // enthaltenen Bilder schon. Das groesste wird
                        // herausgeloest.
                        val extracted = IcoExtractor.extractLargestImage(bytes)
                        if (extracted != null && saveIfDecodable(extracted, target)) return
                    }

                    ImageFormat.Kind.SVG -> {
                        // SVG kann die Android-Plattform nicht dekodieren.
                        // Auf die Standard-Icon-Adresse der Domain
                        // ausweichen.
                        Log.d(TAG, "SVG-Icon wird uebersprungen: $iconUrl")
                    }

                    ImageFormat.Kind.UNKNOWN -> {
                        Log.d(TAG, "Unbekanntes Format von $iconUrl")
                    }
                }
            }
        }

        // Standard-Fallbacks der Domain probieren.
        val base = try {
            val parsed = URL(pageUrl)
            "${parsed.protocol}://${parsed.host}"
        } catch (e: Exception) {
            null
        } ?: return

        for (fallback in listOf("/favicon.ico", "/favicon.png")) {
            val bytes = fetchBytes(base + fallback) ?: continue
            if (bytes.isEmpty()) continue

            val payload = if (ImageFormat.detect(bytes) == ImageFormat.Kind.ICO) {
                IcoExtractor.extractLargestImage(bytes)
            } else {
                bytes
            } ?: continue

            if (saveIfDecodable(payload, target)) return
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
     * Sucht im HTML die Icon-Adresse.
     *
     * Es werden ALLE Icon-Kandidaten gesammelt, nicht nur der erste
     * passende. Das ist wichtig, weil viele Seiten mehrere Icons
     * unterschiedlicher Groesse anbieten:
     *
     *   <link rel="icon" href="/favicon.ico" sizes="16x16"/>
     *   <link rel="icon" href="/icons/icon-192.png" sizes="192x192"/>
     *
     * Der Browser nimmt das groesste passende. Genau das wird hier
     * nachgebildet - sonst gewinnt das 16x16-Icon und das Widget zeigt
     * ein pixeliges Miniaturbild statt des echten Logos.
     *
     * Die Reihenfolge der Prioritaet:
     *  1. Groesse des Icons, wenn per "sizes" angegeben
     *  2. rel-Praeferenz (apple-touch-icon ist meist ein grosses PNG)
     *  3. Dokumentreihenfolge
     */
    private fun findIconUrl(html: String, pageUrl: String): String? {
        val candidates = mutableListOf<IconCandidate>()

        val matcher = ICON_LINK_PATTERN.matcher(html)
        while (matcher.find()) {
            val rel = matcher.group(1)?.trim()?.lowercase() ?: continue
            val tag = matcher.group(0)

            val hrefMatcher = HREF_PATTERN.matcher(tag)
            if (!hrefMatcher.find()) continue
            val href = hrefMatcher.group(1)?.trim() ?: continue
            if (href.isEmpty() || href.startsWith("data:")) continue

            // Der Rang ist die Position in REL_PREFERENCE - nicht die
            // Position im rel-Attribut des Tags. "indexOfFirst" auf
            // "rels" liefert die Stelle im Tag (immer 0 oder 1) und
            // nicht die Rangfolge.
            val rels = rel.split(" ").filter { it.isNotBlank() }
            val relRank = rels
                .mapNotNull { candidate -> REL_PREFERENCE.indexOf(candidate).takeIf { it >= 0 } }
                .minOrNull() ?: REL_PREFERENCE.size

            // Aus der sizes-Angabe die Kantenlaenge in Pixeln ableiten.
            val size = parseSizes(tag)

            candidates.add(
                IconCandidate(
                    href = href,
                    resolved = resolveUrl(href, pageUrl),
                    declaredSize = size,
                    relRank = relRank,
                    order = candidates.size
                )
            )
        }

        if (candidates.isEmpty()) return null

        // Bester Kandidat: groesste angegebene Groesse, dann rel-Rang,
        // dann Dokumentreihenfolge.
        val best = candidates
            .filter { it.resolved != null }
            .sortedWith(
                compareByDescending<IconCandidate> { it.declaredSize }
                    .thenBy { it.relRank }
                    .thenBy { it.order }
            )
            .firstOrNull()
            ?: return null

        return best.resolved
    }

    /**
     * Liest die Pixelgroesse aus einem sizes-Attribut.
     * "16x16" -> 16, "192x192" -> 192, "any" -> 0 (unbekannt).
     */
    private fun parseSizes(tag: String): Int {
        val matcher = SIZES_PATTERN.matcher(tag)
        if (!matcher.find()) return 0
        val value = matcher.group(1)?.trim()?.lowercase() ?: return 0
        if (value == "any") return 0
        // Nur den groessten Wert einer Liste nehmen, z.B. "16x16 32x32".
        val dims = Regex("""(\d+)\s*[xX]\s*(\d+)""")
            .findAll(value)
            .map { match -> match.groupValues[1].toIntOrNull() ?: 0 }
            .filter { dimension -> dimension > 0 }
            .toList()
        return dims.maxOrNull() ?: 0
    }

    /**
     * Ein moeglicher Icon-Kandidat aus dem HTML.
     */
    private data class IconCandidate(
        val href: String,
        val resolved: String?,
        val declaredSize: Int,
        val relRank: Int,
        val order: Int
    )

    /**
     * Laedt eine URL als String (fuer das HTML-Dokument).
     */
    private fun fetchString(url: String): String? {
        return try {
            val connection = openConnection(url)
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { stream ->
                val bytes = stream.readNBytes(MAX_HTML_BYTES)
                String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Seite konnte nicht geladen werden: $url", e)
            null
        }
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
        connection.instanceFollowRedirects = true
        return connection
    }

    /**
     * Liest das gecachte Favicon als Bitmap. Gibt null zurueck, wenn noch
     * keines geladen wurde oder die Datei kaputt ist.
     */
    fun loadBitmap(context: Context, url: String): Bitmap? {
        val host = hostOf(url) ?: return null
        val file = File(File(context.applicationContext.cacheDir, CACHE_DIR), fileNameFor(host))
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Favicon konnte nicht gelesen werden: $host", e)
            null
        }
    }


    /**
     * Macht aus einem relativen Pfad eine absolute URL.
     * Sichtbar fuer Tests.
     */
    fun resolveUrl(href: String, baseUrl: String): String? {
        return try {
            // Die Basis ist bewusst die Wurzel der Domain, nicht das
            // Verzeichnis der Seite: Webseiten referenzieren Icons haeufig
            // als "icon.png" oder "assets/icon.png", gemeint ist aber das
            // Wurzelverzeichnis. Gegen das Seitenverzeichnis aufgeloest
            // wuerde daraus /page/assets/icon.png - ein 404.
            val base = URL(baseUrl)
            val root = "${base.protocol}://${base.host}"
            URL(URL(root), href).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Testbarer Zugang zur Icon-Auswahl.
     */
    fun findIconUrlForTest(html: String, pageUrl: String): String? =
        findIconUrl(html, pageUrl)

    /**
     * Testbarer Zugang zur sizes-Auswertung.
     */
    fun parseSizesForTest(tag: String): Int = parseSizes(tag)
    /**
     * Dateiname aus dem Host. Nicht der Host selbst, sondern ein Hash -
     * so sind keine Hostnamen im Dateisystem sichtbar.
     */
    private fun fileNameFor(host: String): String {
        val hash = host.hashCode().toUInt().toString(16)
        return "favicon_$hash.png"
    }
}
