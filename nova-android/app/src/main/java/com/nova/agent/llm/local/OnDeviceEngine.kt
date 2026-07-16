package com.nova.agent.llm.local

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback

/**
 * LiteRT-LM sarmalayıcısı (litertlm-android 0.13.x).
 *
 * Tasarım kararları (Faz 1 spec'i):
 * - Motor model başına bir kez yüklenir ve bellekte tutulur; initialize()
 *   saniyeler sürebilir, bu yüzden yalnız arka plan thread'inde çağrılır.
 * - Her istek TAZE bir Conversation kurar; sohbet geçmişi initialMessages ile
 *   verilir. Böylece iptal edilen/yarım kalan yanıt sonraki bağlama sızamaz.
 * - Bu sürümde güvenilir bir akış-iptal API'si garanti edilmediği için iptal,
 *   cancelled bayrağı + konuşmayı kapatma ile yapılır; motor yüklü kalır.
 * - Tüm hatalar Throwable düzeyinde yakalanır (x86 emülatörde
 *   UnsatisfiedLinkError dahil) ve Türkçe, dürüst mesajlara çevrilir.
 */
class OnDeviceEngine(private val appContext: Context) {

    interface Callbacks {
        fun onToken(text: String)
        fun onDone()
        fun onError(message: String)
    }

    private val lock = Any()
    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var activeConversation: Conversation? = null

    @Volatile
    private var cancelled = false

    val isLoaded: Boolean get() = synchronized(lock) { engine != null }
    val loadedModelPath: String? get() = synchronized(lock) { loadedPath }

    /** Bloklayıcı; yalnız arka plan dispatcher'ında çağır. */
    fun ensureLoaded(modelPath: String): kotlin.Result<Unit> {
        synchronized(lock) {
            if (engine != null && loadedPath == modelPath) return kotlin.Result.success(Unit)
        }
        unload()
        return try {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath,
            )
            val created = Engine(config)
            created.initialize()
            synchronized(lock) {
                engine = created
                loadedPath = modelPath
            }
            kotlin.Result.success(Unit)
        } catch (t: Throwable) {
            unload()
            kotlin.Result.failure(t)
        }
    }

    /**
     * Akışlı üretim başlatır. [history] = (rol, içerik) çiftleri; son kullanıcı
     * mesajı [prompt] olarak ayrıca verilir. Callback'ler native thread'den gelir;
     * çağıran ana thread'e kendisi geçmelidir.
     */
    fun generate(
        history: List<Pair<String, String>>,
        prompt: String,
        thinking: Boolean,
        cb: Callbacks,
    ) {
        val current = synchronized(lock) { engine }
        if (current == null) {
            cb.onError("Model yüklü değil")
            return
        }
        cancelled = false
        try {
            val initial = history
                .filter { (_, content) -> content.isNotBlank() }
                .map { (role, content) ->
                    if (role == "user") Message.user(content) else Message.model(content)
                }
            val conversation = current.createConversation(
                ConversationConfig(initialMessages = initial),
            )
            synchronized(lock) { activeConversation = conversation }

            conversation.sendMessageAsync(
                prompt,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        if (cancelled) return
                        // 0.13.1 API: metin, Message.contents içindeki Content.Text parçalarındadır.
                        val text = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString(separator = "") { it.text }
                        if (text.isNotEmpty()) cb.onToken(text)
                    }

                    override fun onDone() {
                        if (cancelled) return
                        closeActiveConversation()
                        cb.onDone()
                    }

                    override fun onError(throwable: Throwable) {
                        if (cancelled) return
                        closeActiveConversation()
                        cb.onError(describeError(throwable))
                    }
                },
                mapOf("enable_thinking" to thinking),
            )
        } catch (t: Throwable) {
            closeActiveConversation()
            cb.onError(describeError(t))
        }
    }

    /**
     * Aktif üretimi iptal eder: önce gerçek native iptal (cancelProcess),
     * sonra konuşma atılır; yarım yanıt hiçbir yerde saklanmaz. Motor yüklü
     * kalır, sonraki istek taze konuşma kurar.
     */
    fun cancel() {
        cancelled = true
        val conversation = synchronized(lock) { activeConversation }
        if (conversation != null) runCatching { conversation.cancelProcess() }
        closeActiveConversation()
    }

    fun unload() {
        cancelled = true
        closeActiveConversation()
        val old = synchronized(lock) {
            val e = engine
            engine = null
            loadedPath = null
            e
        }
        if (old != null) runCatching { old.close() }
    }

    private fun closeActiveConversation() {
        val conversation = synchronized(lock) {
            val c = activeConversation
            activeConversation = null
            c
        }
        if (conversation != null) runCatching { conversation.close() }
    }

    companion object {
        fun describeError(t: Throwable): String = when (t) {
            is UnsatisfiedLinkError ->
                "Bu cihaz mimarisi yerel motoru desteklemiyor (ARM64 telefon gerekir)."
            is OutOfMemoryError ->
                "Bellek yetersiz: model bu cihaz için çok büyük."
            else -> t.message?.takeIf { it.isNotBlank() } ?: "Yerel motor hatası"
        }
    }
}
