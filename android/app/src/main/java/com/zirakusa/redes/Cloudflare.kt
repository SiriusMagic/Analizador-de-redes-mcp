package com.zirakusa.redes

import com.zirakusa.redes.data.Origin
import com.zirakusa.redes.data.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The speed test against Cloudflare's public speed server (the one behind speed.cloudflare.com), at its fixed
 * addresses: nothing else is asked and nothing about the user is sent (only random bytes on upload).
 */
object Cloudflare : Transport {
    const val HOST = "speed.cloudflare.com"
    private const val DOWN = "https://$HOST/__down?bytes="
    private const val UP = "https://$HOST/__up"
    private const val PIECE = 64 * 1024
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS).followRedirects(false).build()
    private val noise = Random.nextBytes(PIECE)

    /** «Latencia › Internet»: one tiny request like the test's pings, or null when it does not answer in 3 s. */
    suspend fun latency(): Double? = withTimeoutOrNull(3_000) { runCatching { ping() }.getOrNull() }

    override suspend fun ping(): Double = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        http.newCall(Request.Builder().url(DOWN + "0").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.bytes()
        }
        (System.nanoTime() - start) / 1_000_000.0
    }

    override suspend fun download(bytes: Long, got: (Int) -> Unit): Origin = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(DOWN + bytes).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val source = r.body.byteStream()
            val buf = ByteArray(PIECE)
            while (true) {
                ensureActive()
                val n = source.read(buf)
                if (n < 0) break
                got(n)
            }
            // Only the place, your public address and your provider's network number; the other headers (city, position…) are not read.
            Origin.of(r.header("cf-meta-colo"), r.header("cf-meta-ip"), r.header("cf-meta-asn"))
        }
    }

    override suspend fun upload(bytes: Long, sent: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val context = coroutineContext
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = bytes
            override fun writeTo(sink: BufferedSink) {
                var left = bytes
                while (left > 0) {
                    context.ensureActive()
                    val n = minOf(left, PIECE.toLong()).toInt()
                    sink.write(noise, 0, n); sink.flush()
                    sent(n); left -= n
                }
            }
        }
        http.newCall(Request.Builder().url(UP).post(body).build()).execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}") }
    }
}
