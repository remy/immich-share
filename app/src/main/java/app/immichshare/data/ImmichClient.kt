package app.immichshare.data

import app.immichshare.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ImmichClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * SPEC §8: uploads can be hundreds of megabytes over mobile, so the write
     * timeout is generous and `callTimeout` is deliberately left unset.
     */
    fun create(
        host: String,
        apiKey: String,
        accessHeaders: AccessHeaders = AccessHeaders(),
    ): ImmichApi {
        val extra = accessHeaders.asMap()

        val auth = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("x-api-key", apiKey)
                .header("Accept", "application/json")
            // Proxy credentials go on every request, including the multipart
            // upload — the proxy rejects at the edge, before Immich is reached.
            extra.forEach { (name, value) -> builder.header(name, value) }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Never log credentials, whatever the level.
            redactHeader("x-api-key")
            accessHeaders.sensitiveHeaderNames().forEach(::redactHeader)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()

        return Retrofit.Builder()
            .baseUrl(host.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ImmichApi::class.java)
    }
}
