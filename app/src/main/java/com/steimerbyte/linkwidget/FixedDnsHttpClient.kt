package com.steimerbyte.linkwidget

import net.mm2d.touchicon.http.HttpClientAdapter
import net.mm2d.touchicon.http.HttpResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * HTTP-Client fuer die Bibliothek "touch-icon-extractor".
 *
 * Die Bibliothek bringt einen eigenen HTTP-Client mit, der den
 * System-Resolver befragt. Auf dem Geraet loest der System-Resolver
 * Hostnamen nicht zuverlaessig auf - der Abruf wuerde dort wieder
 * scheitern.
 *
 * Deshalb bekommt die Bibliothek den fertigen OkHttp-Client
 * uebergeben. Dessen Resolver ist in [FaviconLoader] gesetzt und
 * loest selbst auf.
 *
 * Der Client bekommt die Bibliothek als [HttpClientAdapter], weil
 * sie mit einem eigenen Adapter arbeiten kann. Zwei Anmerkungen:
 *
 *  - Der Hostname wird **nicht** durch die IP ersetzt. TLS-SNI wird
 *    daraus gebildet; mit IP als Hostname antwortet Nginx mit
 *    TLSV1_ALERT_UNRECOGNIZED_NAME. Ein `Host`-Header hilft hier
 *    nicht, weil er erst nach dem TLS-Handshake ausgewertet wird.
 *  - Die Bibliothek ruft Manifeste nur vom Origin der Seite ab.
 *    Deshalb muss der Resolver auch Subdomains abdecken, auf denen
 *    Icons liegen koennen.
 */
class FixedDnsHttpClient(
    client: OkHttpClient,
    userAgent: String
) : HttpClientAdapter {

    override var userAgent: String = userAgent
    override var headers: Map<String, String> = emptyMap()

    private val client: OkHttpClient = client

    @Throws(IOException::class)
    override fun head(url: String): HttpResponse {
        val request = Request.Builder()
            .head()
            .url(url)
            .applyHeaders()
            .build()
        return execute(request)
    }

    @Throws(IOException::class)
    override fun get(url: String): HttpResponse {
        val request = Request.Builder()
            .get()
            .url(url)
            .applyHeaders()
            .build()
        return execute(request)
    }

    private fun Request.Builder.applyHeaders(): Request.Builder {
        headers.forEach { (key, value) -> header(key, value) }
        if (userAgent.isNotEmpty()) {
            header("User-Agent", userAgent)
        }
        return this
    }

    private fun execute(request: Request): HttpResponse {
        val response = client.newCall(request).execute()
        return OkHttpResponse(response)
    }

    /**
     * Duenne Huelle um die OkHttp-Antwort, passend zur Schnittstelle
     * der Bibliothek.
     */
    private class OkHttpResponse(
        private val response: okhttp3.Response
    ) : HttpResponse {

        override val isSuccess: Boolean
            get() = response.isSuccessful

        override fun header(name: String): String? = response.header(name)

        @Throws(IOException::class)
        override fun bodyString(limit: Int): String? {
            val bytes = bodyBytes(limit) ?: return null
            return String(bytes, Charsets.UTF_8)
        }

        @Throws(IOException::class)
        override fun bodyBytes(limit: Int): ByteArray? {
            val source = response.body ?: return null
            if (limit <= 0) return source.bytes()
            val buffer = java.io.ByteArrayOutputStream()
            val stream = source.source()
            val chunk = ByteArray(8192)
            var total = 0
            while (total < limit) {
                val read = stream.read(chunk, 0, minOf(chunk.size, limit - total))
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                total += read
            }
            return buffer.toByteArray()
        }

        override fun close() {
            response.close()
        }
    }
}