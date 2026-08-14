package dev.mockarr.core.routing

import okhttp3.Interceptor
import okhttp3.Response

/** Public OSM-ecosystem servers require an identifying User-Agent. */
internal class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder().header("User-Agent", userAgent).build(),
    )
}

/** Public-server etiquette: never fire requests closer together than the interval. */
internal class MinIntervalInterceptor(private val minIntervalMillis: Long) : Interceptor {
    private val lock = Any()
    private var lastRequestAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val wait = lastRequestAt + minIntervalMillis - System.currentTimeMillis()
            if (wait > 0) {
                Thread.sleep(wait)
            }
            lastRequestAt = System.currentTimeMillis()
        }
        return chain.proceed(chain.request())
    }
}
