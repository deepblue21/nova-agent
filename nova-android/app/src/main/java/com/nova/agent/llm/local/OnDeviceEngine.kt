package com.nova.agent.llm.local

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ToolProvider
import com.nova.agent.llm.ThinkingText

/**
 * LiteRT-LM sarmalayıcısı (litertlm-android 0.13.x).
 *
 * Tasarım kararları (Faz 1 spec'i + donma düzeltmesi):
 * - Motor model başına bir kez yüklenir ve bellekte tutulur; initialize()
 *   saniyeler sürebilir, bu yüzden yalnız arka plan thread'inde çağrılır.
 * - Konuşma TURLAR ARASINDA CANLI TUTULUR: sohbet kaldığı yerden devam
 *   ediyorsa aynı Conversation'a yeni mesaj gönderilir (KV önbelleği korunur).
 *   Aksi halde (geçmiş düzenlendi, iptal/hata oldu, kişilik/araçlar değişti)
 *   konuşma kapatılıp initialMessages ile taze kurulur. Eski "her istekte taze
 *   konuşma" tasarımı, büyük modellerde (Gemma E4B) 2. sorudan itibaren tüm
 *   geçmişin yeniden işlenmesine ve dakikalarca süren donmalara yol açıyordu.
 * - Conversation.close() ASLA native callback thread'inde çağrılmaz (kilitlenme
 *   riski). Hata/iptalde oturum yalnız "bozuk" işaretlenir; kapatma bir sonraki
 *   generate/unload çağrısında arka plan thread'inde yapılır.
 * - İptal: cancelled bayrağı + cancelProcess; yarım yanıt sonraki bağlama
 *   sızamaz çünkü bozuk oturum yeniden kurulur.
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

    /**
     * Canlı konuşmanın gördüğü bağlamın normalize kopyası: ViewModel'in bir
     * sonraki turda geri göndereceği (rol, içerik) çiftleriyle birebir aynı
     * biçimde tutulur; eşleşirse konuşma yeniden kullanılır.
     */
    private val transcript = mutableListOf<Pair<String, String>>()
    private var sessionSystem: String = ""
    private var sessionHasTools: Boolean = false
    private var sessionBroken = true

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
     * mesajı [prompt] olarak ayrıca verilir. [tools] boş değilse konuşma araç
     * kullanımıyla kurulur (otomatik araç çağırma; hatalar modele metin döner).
     * Callback'ler native thread'den gelir; çağıran ana thread'e kendisi geçmelidir.
     * Bloklayıcı kurulum içerdiği için yalnız arka plan dispatcher'ında çağır.
     */
    fun generate(
        history: List<Pair<String, String>>,
        prompt: String,
        thinking: Boolean,
        tools: List<ToolProvider> = emptyList(),
        systemInstruction: String = "",
        cb: Callbacks,
    ) {
        val current = synchronized(lock) { engine }
        if (current == null) {
            cb.onError("Model yüklü değil")
            return
        }
        cancelled = false
        val system = systemInstruction.trim()
        val wantTools = tools.isNotEmpty()
        val cleanHistory = history.filter { (_, content) -> content.isNotBlank() }

        try {
            val conversation = obtainConversation(current, cleanHistory, system, wantTools, tools)
            val raw = StringBuilder()

            conversation.sendMessageAsync(
                prompt,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        if (cancelled) return
                        // 0.13.1 API: metin, Message.contents içindeki Content.Text parçalarındadır.
                        val text = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString(separator = "") { it.text }
                        if (text.isNotEmpty()) {
                            raw.append(text)
                            cb.onToken(text)
                        }
                    }

                    override fun onDone() {
                        if (cancelled) return
                        // Konuşma canlı kalır (KV önbelleği). Kapatma YOK:
                        // native callback thread'inde close() kilitlenebilir.
                        synchronized(lock) {
                            transcript.add("user" to prompt)
                            transcript.add("assistant" to normalizeAssistantText(raw.toString()))
                        }
                        cb.onDone()
                    }

                    override fun onError(throwable: Throwable) {
                        if (cancelled) return
                        markSessionBroken()
                        cb.onError(describeError(throwable))
                    }
                },
                mapOf("enable_thinking" to thinking),
            )
        } catch (t: Throwable) {
            markSessionBroken()
            cb.onError(describeError(t))
        }
    }

    /**
     * Mevcut konuşma bağlamla eşleşiyorsa onu döndürür (hızlı yol, yeniden
     * prefill yok); eşleşmiyorsa eskisini kapatıp tazesini kurar.
     */
    private fun obtainConversation(
        current: Engine,
        cleanHistory: List<Pair<String, String>>,
        system: String,
        wantTools: Boolean,
        tools: List<ToolProvider>,
    ): Conversation {
        val reusable = synchronized(lock) {
            if (!sessionBroken &&
                activeConversation != null &&
                sessionSystem == system &&
                sessionHasTools == wantTools &&
                transcript == cleanHistory
            ) {
                activeConversation
            } else {
                null
            }
        }
        if (reusable != null) return reusable

        closeActiveConversation()
        val fresh = current.createConversation(
            ConversationConfig(
                systemInstruction = system.takeIf { it.isNotEmpty() }?.let { Contents.of(it) },
                initialMessages = cleanHistory.map { (role, content) ->
                    if (role == "user") Message.user(content) else Message.model(content)
                },
                tools = tools,
            ),
        )
        synchronized(lock) {
            activeConversation = fresh
            transcript.clear()
            transcript.addAll(cleanHistory)
            sessionSystem = system
            sessionHasTools = wantTools
            sessionBroken = false
        }
        return fresh
    }

    /**
     * Aktif üretimi iptal eder: önce gerçek native iptal (cancelProcess),
     * sonra oturum bozuk işaretlenir; yarım yanıt hiçbir yerde saklanmaz.
     * Kapatma bir sonraki generate/unload'da arka planda yapılır. Motor yüklü
     * kalır, sonraki istek taze konuşma kurar.
     */
    fun cancel() {
        cancelled = true
        val conversation = synchronized(lock) { activeConversation }
        if (conversation != null) runCatching { conversation.cancelProcess() }
        markSessionBroken()
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

    private fun markSessionBroken() {
        synchronized(lock) {
            sessionBroken = true
            transcript.clear()
        }
    }

    private fun closeActiveConversation() {
        val conversation = synchronized(lock) {
            val c = activeConversation
            activeConversation = null
            sessionBroken = true
            transcript.clear()
            c
        }
        if (conversation != null) runCatching { conversation.close() }
    }

    companion object {
        /**
         * Ham model çıktısını, ViewModel'in geçmişe kaydettiği biçime çevirir
         * (finishLocal ile birebir): düşünce bloğu ayrılır, boşsa "(boş yanıt)".
         * Eşleşme bu sayede string karşılaştırmasıyla güvenilir çalışır.
         */
        fun normalizeAssistantText(raw: String): String {
            val (_, content) = ThinkingText.split(raw)
            return content.ifBlank { "(boş yanıt)" }
        }

        fun describeError(t: Throwable): String = when (t) {
            is UnsatisfiedLinkError ->
                "Bu cihaz mimarisi yerel motoru desteklemiyor (ARM64 telefon gerekir)."
            is OutOfMemoryError ->
                "Bellek yetersiz: model bu cihaz için çok büyük."
            else -> t.message?.takeIf { it.isNotBlank() } ?: "Yerel motor hatası"
        }
    }
}
