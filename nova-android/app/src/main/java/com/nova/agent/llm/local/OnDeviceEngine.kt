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
import com.google.ai.edge.litertlm.SamplerConfig
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
    private var loadedPreference: BackendPreference? = null
    /** Motor görü backend'i AÇIK kurulmuş mu (Faz 12A). */
    private var loadedVision: Boolean = false
    private var activeConversation: Conversation? = null

    /** Motorun gerçekten yüklendiği backend; tercih değil, ölçülen sonuç. */
    @Volatile
    var activeBackend: ActiveBackend = ActiveBackend.NONE
        private set

    /**
     * Canlı konuşmanın gördüğü bağlamın normalize kopyası: ViewModel'in bir
     * sonraki turda geri göndereceği (rol, içerik) çiftleriyle birebir aynı
     * biçimde tutulur; eşleşirse konuşma yeniden kullanılır.
     */
    private val transcript = mutableListOf<Pair<String, String>>()
    private var sessionSystem: String = ""
    private var sessionHasTools: Boolean = false
    private var sessionSampler: SamplerSettings? = null
    private var sessionBroken = true

    @Volatile
    private var cancelled = false

    val isLoaded: Boolean get() = synchronized(lock) { engine != null }
    val loadedModelPath: String? get() = synchronized(lock) { loadedPath }

    /**
     * `ensureLoaded` bu çağrıyı yeniden yükleme yapmadan karşılar mı.
     *
     * Yol aynı olsa bile **tercih değiştiyse motor yeniden kurulur**; çağıran
     * taraf yükleme süresini bu yüzden yalnız yola bakarak 0 sayamaz, yoksa
     * Modeller ekranındaki yükleme metriği yalan söylerdi.
     */
    /**
     * [vision] anahtarı da kimliğin parçası — Faz 12A.
     *
     * Görü backend'i `EngineConfig` içinde, yani MOTOR KURULURKEN veriliyor;
     * sonradan açılamıyor. Metin için kurulmuş bir motora görsel göndermek
     * sessizce başarısız olurdu. Bu yüzden "görü açık" ayrı bir yüklü-durum
     * sayılır ve gerektiğinde motor yeniden kurulur.
     *
     * Ters yön de kasıtlı: görü açık bir motor metin isteği için YENİDEN
     * kurulmaz (`vision = false` çağrısı, görü açık motoru kabul eder) —
     * her mesajda motor yeniden kurmak saniyeler kaybettirirdi.
     */
    fun isLoadedWith(
        modelPath: String,
        preference: BackendPreference,
        vision: Boolean = false,
    ): Boolean =
        synchronized(lock) {
            engine != null && loadedPath == modelPath && loadedPreference == preference &&
                (loadedVision || !vision)
        }

    /**
     * Modeli [preference] hızlandırmasıyla yükler ve gerçekten kullanılan
     * backend'i döndürür.
     *
     * Tercih Otomatik ise plan sırayla denenir (GPU → CPU): GPU sürücüsü olmayan
     * cihazlarda `Engine.initialize()` atar, bu yakalanır ve CPU denenir. Kullanıcı
     * bir backend'i AÇIKÇA seçtiyse plan tek elemanlıdır — sessizce başka bir
     * backend'e düşülmez, hata dürüstçe yukarı verilir.
     *
     * Bloklayıcı (initialize saniyeler sürebilir); yalnız arka plan
     * dispatcher'ında çağır.
     */
    fun ensureLoaded(
        modelPath: String,
        preference: BackendPreference = BackendPreference.AUTO,
        /** Görsel gönderilecekse true: motor görü backend'iyle kurulur (Faz 12A). */
        vision: Boolean = false,
    ): kotlin.Result<ActiveBackend> {
        // Aynı model + aynı tercih (+ gereken görü) zaten yüklüyse yeniden kurma.
        if (isLoadedWith(modelPath, preference, vision)) return kotlin.Result.success(activeBackend)
        unload()

        var lastError: Throwable? = null
        for (candidate in BackendPlan.attempts(preference)) {
            var attempt: Engine? = null
            val created = try {
                attempt = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = nativeBackend(candidate),
                        // Yazılabilir önbellek 2. yüklemeyi belirgin hızlandırır.
                        cacheDir = appContext.cacheDir.absolutePath,
                        // Görü backend'i metin backend'iyle AYNI seçilir: GPU
                        // denenip CPU'ya düşülen bir cihazda görüyü GPU'da
                        // bırakmak, az önce başarısız olan yolu tekrar denemek
                        // olurdu. null = görü hiç kurulmaz (bugünkü davranış).
                        visionBackend = if (vision) nativeBackend(candidate) else null,
                    ),
                )
                attempt.initialize()
                attempt
            } catch (t: Throwable) {
                lastError = t
                // Yarım kalan motoru kapat: initialize() attıysa nesne zaten
                // kurulmuştu ve GPU bağlamını tutuyor olabilir. Kapatmadan
                // sonraki backend'i denemek sızıntı bırakırdı.
                if (attempt != null) runCatching { attempt.close() }
                continue
            }
            synchronized(lock) {
                engine = created
                loadedPath = modelPath
                loadedPreference = preference
                loadedVision = vision
                activeBackend = candidate
            }
            return kotlin.Result.success(candidate)
        }

        unload()
        val cause = describeError(lastError ?: RuntimeException("Yerel motor hatası"))
        return kotlin.Result.failure(
            BackendLoadException(BackendPlan.failureMessage(preference, cause), lastError),
        )
    }

    /** Saf [ActiveBackend] seçimini LiteRT-LM tipine çevirir. Tek çeviri noktası. */
    private fun nativeBackend(target: ActiveBackend): Backend = when (target) {
        ActiveBackend.GPU -> Backend.GPU()
        ActiveBackend.NPU -> Backend.NPU(
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
        )
        // NONE buraya BackendPlan üzerinden hiç gelmez; gelirse en uyumlu yol.
        ActiveBackend.CPU, ActiveBackend.NONE -> Backend.CPU()
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
        /** null = motora samplerConfig verilme; model kendi varsayılanıyla çalışsın. */
        sampler: SamplerSettings? = null,
        /**
         * JPEG baytları; null = görsel yok (Faz 12A). Dolu verilirse motorun
         * görü backend'iyle yüklenmiş olması ŞARTTIR — bkz. [ensureLoaded].
         */
        imageJpeg: ByteArray? = null,
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
            val conversation =
                obtainConversation(current, cleanHistory, system, wantTools, tools, sampler)
            val raw = StringBuilder()

            // Faz 12A: görsel varsa mesaj çok parçalı gider. Sıra bilinçli —
            // görsel önce, metin sonra: model kartlarındaki örnek de böyle ve
            // "şu resmi açıkla" istemi resmi zaten görmüş olarak okunur.
            val outgoing = if (imageJpeg == null) {
                Contents.of(prompt)
            } else {
                Contents.of(Content.ImageBytes(imageJpeg), Content.Text(prompt))
            }

            conversation.sendMessageAsync(
                outgoing,
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
                            // Döküme görselin DEĞİL, gönderilen metnin kendisi yazılır.
                            // Buradaki liste `obtainConversation` içinde çağıranın
                            // geçmişiyle birebir karşılaştırılıyor; "[görsel] " gibi
                            // bir önek eklemek o karşılaştırmayı her seferinde
                            // bozar ve KV önbelleği boşuna atılırdı.
                            //
                            // BİLİNEN SINIR (Faz 12A): görsel yalnız eklendiği mesaj
                            // için geçerlidir. Konuşma yeniden kurulursa (model
                            // değişimi, oturum hatası) görü bağlamı geri gelmez;
                            // geçmişte görsel taşımak ayrı bir iş.
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
        sampler: SamplerSettings?,
    ): Conversation {
        val reusable = synchronized(lock) {
            if (!sessionBroken &&
                activeConversation != null &&
                sessionSystem == system &&
                sessionHasTools == wantTools &&
                // Örnekleme ayarı konuşma kurulurken sabitlenir; değiştiyse
                // KV önbelleğini korumak yerine taze konuşma kurmak gerekir,
                // yoksa kullanıcı ayarı değiştirir ve hiçbir şey değişmez.
                sessionSampler == sampler &&
                transcript == cleanHistory
            ) {
                activeConversation
            } else {
                null
            }
        }
        if (reusable != null) return reusable

        closeActiveConversation()
        val instruction = system.takeIf { it.isNotEmpty() }?.let { Contents.of(it) }
        val initial = cleanHistory.map { (role, content) ->
            if (role == "user") Message.user(content) else Message.model(content)
        }
        // sampler null iken samplerConfig parametresi HİÇ verilmez: kütüphanenin
        // kendi varsayılanı korunur ve bugünkü davranış birebir aynı kalır.
        val config = if (sampler == null) {
            ConversationConfig(
                systemInstruction = instruction,
                initialMessages = initial,
                tools = tools,
            )
        } else {
            ConversationConfig(
                systemInstruction = instruction,
                initialMessages = initial,
                tools = tools,
                samplerConfig = SamplerConfig(
                    topK = sampler.topK,
                    topP = sampler.topP,
                    temperature = sampler.temperature,
                ),
            )
        }
        val fresh = current.createConversation(config)
        synchronized(lock) {
            activeConversation = fresh
            transcript.clear()
            transcript.addAll(cleanHistory)
            sessionSystem = system
            sessionHasTools = wantTools
            sessionSampler = sampler
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
            loadedPreference = null
            loadedVision = false
            activeBackend = ActiveBackend.NONE
            sessionSampler = null
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
            // Zaten hangi backend'in denendiğini ve ne yapılacağını anlatıyor.
            is BackendLoadException -> t.message ?: "Yerel motor hatası"
            is UnsatisfiedLinkError ->
                "Bu cihaz mimarisi yerel motoru desteklemiyor (ARM64 telefon gerekir)."
            is OutOfMemoryError ->
                "Bellek yetersiz: model bu cihaz için çok büyük."
            else -> t.message?.takeIf { it.isNotBlank() } ?: "Yerel motor hatası"
        }
    }
}

/**
 * Planlanan tüm backend'ler denendi ve hiçbiri yüklenemedi. Mesaj kullanıcıya
 * ne yapacağını söyler; ham native hata [cause] içinde korunur.
 */
class BackendLoadException(message: String, cause: Throwable?) : Exception(message, cause)
