package dev.zbxmon.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Thrown when the server certificate does not match the pinned fingerprint. */
class PinMismatchException : CertificateException("TLS fingerprint mismatch")

object Tls {
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    private fun norm(s: String) = s.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun pinned(pin: String) = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isEmpty() || norm(sha256(chain[0])) != norm(pin)) throw PinMismatchException()
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val cache = ConcurrentHashMap<String, OkHttpClient>()

    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun client(p: Profile): OkHttpClient {
        val pin = norm(p.pinSha256)
        if (pin.isEmpty()) return base
        return cache.getOrPut(pin) {
            val tm = pinned(p.pinSha256)
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
            // With a pin, the certificate fingerprint IS the authentication; the hostname is not checked.
            base.newBuilder().sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { _, _ -> true }.build()
        }
    }

    /**
     * Connects without validating, only to READ the leaf certificate fingerprint.
     * The user must verify the fingerprint out of band before saving it.
     */
    suspend fun fetchFingerprint(url: String): String = withContext(Dispatchers.IO) {
        val u = try { URI(url.trim()) } catch (e: Exception) { throw ZabbixException(ZabbixException.Kind.BAD_URL) }
        val host = u.host ?: throw ZabbixException(ZabbixException.Kind.BAD_URL)
        val port = if (u.port > 0) u.port else 443
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        (ctx.socketFactory.createSocket(host, port) as SSLSocket).use { s ->
            s.soTimeout = 10_000
            s.startHandshake()
            sha256(s.session.peerCertificates[0] as X509Certificate)
        }
    }
}
