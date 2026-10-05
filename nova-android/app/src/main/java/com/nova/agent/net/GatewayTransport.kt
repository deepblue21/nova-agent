package com.nova.agent.net

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/** Network interceptors run for each redirect before its HTTP body is written. */
internal fun OkHttpClient.withGatewayPolicy(): OkHttpClient = newBuilder()
    .addNetworkInterceptor { chain ->
        val url = chain.request().url
        if (!NetworkPolicy.allowsHost(url.scheme, url.host)) {
            throw IOException("Şifresiz uzak bağlantı engellendi; HTTPS adresi kullanın")
        }
        chain.proceed(chain.request())
    }.build()

/** OkHttp SSE cancels Call.timeout after headers; enforce an independent deadline. */
internal class BoundedEventSourceFactory(
    private val delegate: EventSource.Factory,
    private val timeoutMs: Long = 360_000,
) : EventSource.Factory {
    override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
        val finished = AtomicBoolean(false)
        val upstream = AtomicReference<EventSource?>()
        val deadline = AtomicReference<ScheduledFuture<*>?>()
        val source = object : EventSource {
            override fun request() = request
            override fun cancel() {
                finished.set(true)
                deadline.get()?.cancel(false)
                upstream.get()?.cancel()
            }
        }
        fun finish(): Boolean {
            val first = finished.compareAndSet(false, true)
            if (first) deadline.get()?.cancel(false)
            return first
        }
        deadline.set(timer.schedule({
            if (finish()) {
                upstream.get()?.cancel()
                listener.onFailure(source, SocketTimeoutException("Yanıt süresi aşıldı"), null)
            }
        }, timeoutMs, TimeUnit.MILLISECONDS))
        try {
            upstream.set(delegate.newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    if (!finished.get()) listener.onOpen(source, response)
                }
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (!finished.get()) listener.onEvent(source, id, type, data)
                }
                override fun onClosed(eventSource: EventSource) {
                    if (finish()) listener.onClosed(source)
                }
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (finish()) listener.onFailure(source, t, response)
                }
            }))
            if (finished.get()) upstream.get()?.cancel()
        } catch (e: Exception) {
            source.cancel()
            throw e
        }
        return source
    }

    companion object {
        private val timer = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "nova-stream-deadline").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
