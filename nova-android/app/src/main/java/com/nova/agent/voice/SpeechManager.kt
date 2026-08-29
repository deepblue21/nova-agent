package com.nova.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Tarayıcı yerine cihazın yerleşik konuşma motorlarını kullanır.
 * STT: SpeechRecognizer (Google), TTS: TextToSpeech. Türkçe (tr-TR).
 * İleride gateway /stt + /tts'e geçirilebilir (Whisper + sunucu TTS).
 */
class SpeechManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var preferOfflineActive = false

    fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("tr", "TR")
                ttsReady = true
            }
        }
    }

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Dinlemeyi başlatır. Geri çağrılar ana iş parçacığında gelir.
     * [preferOffline] true ise cihaz-üstü tanıma paketi tercih edilir (yerel/çevrimdışı
     * politikalarda ses de ağa çıkmasın diye). Offline paket yoksa sistem ağ hatası
     * verebilir; bu durumda mesaj kullanıcıya dürüstçe açıklanır.
     */
    fun startListening(
        onRms: (Float) -> Unit,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onEnd: (error: String?) -> Unit,
        preferOffline: Boolean = false,
    ) {
        stopListening()
        preferOfflineActive = preferOffline
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB ~ -2..10 → 0..1
                onRms(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onEnd(errorText(error)) }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.stringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { if (it.isNotBlank()) onPartial(it) }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.stringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onResult(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Yerel/çevrimdışı politikalarda cihaz-üstü tanıma paketini tercih et (API 23+).
            if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        rec.startListening(intent)
    }

    fun stopListening() {
        recognizer?.let { try { it.stopListening() } catch (_: Exception) {}; try { it.destroy() } catch (_: Exception) {} }
        recognizer = null
    }

    /**
     * Yanıtı seslendirir — S1'de parçalı hâle getirildi.
     *
     * Eskiden metin tek seferde `speak`'e veriliyordu. Cihazın
     * `getMaxSpeechInputLength()` sınırını (çoğu cihazda 4000) aşan yanıtta
     * `speak` ERROR döner ve **hiçbir geri çağrı gelmez**: `onDone` hiç
     * çağrılmadığı için orb sonsuza dek "Konuşuyorum"da kalır, ses de çıkmaz.
     * Dönüş değeri de kontrol edilmiyordu, yani hata tümüyle sessizdi.
     *
     * Artık metin [TtsChunker] ile sınırın altına bölünüp kuyruğa veriliyor;
     * `onDone` yalnızca SON parça bitince, `onStart` yalnızca İLK parça
     * başlayınca bir kez çağrılıyor. Her çıkış yolu `onDone`'a bağlanır —
     * çağrı arayüzü "bir gün mutlaka biter" sözünü tutmak zorunda.
     */
    fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
        val engine = tts ?: run { onDone(); return }
        if (!ttsReady) { onDone(); return }

        val limit = (runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrNull() ?: 0)
            .let { if (it > 200) it - 100 else TtsChunker.FALLBACK_LIMIT }
        val chunks = TtsChunker.chunk(text, limit)
        if (chunks.isEmpty()) { onDone(); return }

        val lastId = utteranceId(chunks.lastIndex)
        var started = false
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            onDone()
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!started) { started = true; onStart() }
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastId) finishOnce()
            }
            @Deprecated("deprecated") override fun onError(utteranceId: String?) { finishOnce() }
            override fun onError(utteranceId: String?, errorCode: Int) { finishOnce() }
        })

        chunks.forEachIndexed { index, chunk ->
            if (finished) return
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(chunk, mode, null, utteranceId(index)) == TextToSpeech.ERROR) {
                finishOnce()
                return
            }
        }
    }

    private fun utteranceId(index: Int) = "nova-utt-$index"

    fun stopSpeaking() { try { tts?.stop() } catch (_: Exception) {} }

    fun destroy() {
        stopListening()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ttsReady = false
    }

    private fun Bundle.stringArrayList(key: String): ArrayList<String>? =
        getStringArrayList(key)

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Anlaşılamadı"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses algılanmadı"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni yok"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            if (preferOfflineActive) {
                "Çevrimdışı ses paketi yok. Ayarlar > Sistem > Diller/Ses ile Türkçe çevrimdışı tanımayı indir."
            } else {
                "Ağ hatası"
            }
        else -> "Tanıma hatası ($code)"
    }
}
