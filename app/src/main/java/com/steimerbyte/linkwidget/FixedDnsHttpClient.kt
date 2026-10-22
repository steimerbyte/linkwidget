package com.steimerbyte.linkwidget

import net.mm2d.touchicon.http.HttpClientAdapter
import net.mm2d.touchicon.http.HttpResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * HTTP-Client, der eine bereits aufgeloeste Adresse verwendet.
 *
 * Warum das noetig ist: Auf dem Geraet schlug die Aufloesung des
 * Hostnamens ueber den System-Resolver fehl, obwohl der Name ueber
 * oeffentliche Resolver einwandfrei aufloesbar war. Ein Aufruf von
 * `URL.openConnection()` loest den Namen aber erneut auf - und
 * scheitert wieder.
 *
 * Deshalb wird die Adresse vorher selbst ermittelt und hier
 * hinterlegt. OkHttp fragt dann nicht mehr nach.
 *
 * Wichtig: Der Hostname wird **nicht** durch die IP ersetzt. Er
 * bleibt in der URL stehen, damit SNI und Host-Header weiterhin
 * den Hostnamen tragen. Sonst waere die Zertifikatspruefung nicht
 * mehr moeglich, und die Verbindung wuerde an der TLS-Pruefung
 * scheitern statt am DNS.
 */
class FixedDnsHttpClient(
    private val host: String,
    private val address: InetAddress,
    userAgent: String
) : HttpClientAdapter {

    override var userAgent: String = userAgent
    override var headers: Map<String, String> = emptyMap()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(FixedDns(host, address))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
     * Liefert die hinterlegte Adresse fuer den einen Host und
     * delegiert alle anderen Anfragen an das System.
     */
    private class FixedDns(
        private val host: String,
        private val address: InetAddress
    ) : okhttp3.Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.equals(host, ignoreCase = true)) {
                return listOf(address)
            }
            return okhttp3.Dns.SYSTEM.lookup(hostname)
        }
    }

    /**
     * Duenne Huelle um die OkHttp-Antwort, passend zum Adapter der
     * Bibliothek.
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
