package com.nova.agent.feature.pairing

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.agent.net.DiscoveredGateway
import com.nova.agent.net.NsdGatewayDiscovery
import com.nova.agent.net.PairingClient
import com.nova.agent.net.PairingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Call

/**
 * Eşleme akışının Android kabuğu: mDNS keşfi + kod takası.
 *
 * Karar veren hiçbir şey burada değil — form kuralları [PairingForm],
 * çözümleme `GatewayDiscovery`/`PairingResponses`, şifresiz bağlantı politikası
 * `NetworkPolicy` içinde ve hepsi saf/testli. Bu sınıf yalnız yaşam döngüsü
 * yönetir: keşfi başlat/durdur, çağrıyı iptal et, state'i ana thread'de güncelle.
 *
 * **Anahtar gizliliği:** takastan dönen `nv_` anahtarı state'te TUTULMAZ ve
 * loglanmaz; doğrudan [onPaired] geri çağrısına verilip oradan ayarlara yazılır.
 */
class PairingController(
    app: Application,
    private val scope: CoroutineScope,
    private val onMain: (block: () -> Unit) -> Unit,
    /** Takas başarılı: (baseUrl, apiKey). Anahtar burada tüketilir, saklanmaz. */
    private val onPaired: (baseUrl: String, apiKey: String) -> Unit,
) {
    private val discovery = NsdGatewayDiscovery(app)
    private val client = PairingClient()

    private var discoveryJob: Job? = null
    private var settleJob: Job? = null
    private var claimCall: Call? = null

    var state by mutableStateOf(PairingUiState())
        private set

    /**
     * Keşfi başlatır. Panel her açıldığında çağrılabilir; zaten çalışıyorsa
     * yeniden başlatılmaz (mDNS taraması pil harcar).
     */
    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        onMain { state = state.copy(scanning = true) }

        discoveryJob = scope.launch {
            discovery.discover().collectLatest { list ->
                onMain { applyDiscovered(list) }
            }
            // Akış kapandı (cihazda NSD yok ya da başlatılamadı): tarama bitti.
            onMain { state = state.copy(scanning = false) }
        }

        // mDNS keşfi normalde HİÇ tamamlanmaz — iptal edilene kadar dinler.
        // Bu yüzden "tarama bitti" kararı akışın sonunu bekleyemez; yoksa
        // hiçbir şey bulunamadığında kullanıcı sonsuza kadar "aranıyor…"
        // görürdü. Belirli bir süre sonra taramayı DİNLEMEYİ bırakmadan
        // "yerleşmiş" sayarız: geç gelen bir PC listeye yine düşer.
        settleJob = scope.launch {
            delay(DISCOVERY_SETTLE_MS)
            onMain { state = state.copy(scanning = false) }
        }
    }

    /**
     * Kullanıcının "Yeniden tara"sı — düğme ÖLÜYDÜ.
     *
     * Düğme doğrudan [startDiscovery]'ye bağlıydı ve o da `discoveryJob` aktifse
     * ilk satırda dönüyordu. mDNS keşfi NORMALDE HİÇ BİTMEZ, yani job her zaman
     * aktifti: düğmeye basmak hiçbir şey yapmıyordu — ne yeni tarama, ne de
     * "arıyorum" geri bildirimi. PC'sini yeni açan kullanıcı için tek çıkış
     * paneli kapatıp açmaktı.
     *
     * Yeniden tarama açık bir kullanıcı isteğidir: mevcut taramayı kapatıp
     * baştan başlatır ve listeyi temizler (kapanmış PC'ler öylece kalmasın).
     */
    fun rescan() {
        stopDiscovery()
        state = state.copy(found = emptyList(), selectedBaseUrl = null)
        startDiscovery()
    }

    /** Panel kapanınca çağrılır: mDNS taraması arka planda sürmemeli. */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        settleJob?.cancel()
        settleJob = null
        state = state.copy(scanning = false)
    }

    private fun applyDiscovered(list: List<DiscoveredGateway>) {
        state = state.copy(
            // Bir şey bulunduysa bekleme metnini sürdürmenin anlamı yok.
            scanning = if (list.isNotEmpty()) false else state.scanning,
            found = list,
            selectedBaseUrl = PairingForm.reconcileSelection(state.selectedBaseUrl, list),
        )
    }

    private companion object {
        /**
         * Yerel ağdaki bir yanıt bu süre içinde gelir; sonrasında "bulunamadı"
         * demek dürüst. Keşif arka planda dinlemeye devam eder.
         */
        const val DISCOVERY_SETTLE_MS = 6_000L

        /**
         * Kod alanı üst sınırı. 8 karakterlik koda değil, yapıştırılan tam
         * `horus://pair?...` bağlantısına göre belirlendi (adres + ad + kod).
         */
        const val MAX_INPUT_CHARS = 512
    }

    fun select(gateway: DiscoveredGateway) {
        state = state.copy(selectedBaseUrl = gateway.baseUrl)
    }

    fun updateCode(raw: String) {
        // Ham metin saklanır (kullanıcı ne yazdıysa onu görsün); yorumlama
        // gönderim anında yapılır. Sınır, yapıştırılan tam eşleme
        // bağlantısını da alacak kadar geniş olmalı — kısa bir kod sınırı
        // URI'yi kırpar ve sessizce geçersiz kılardı.
        state = state.copy(code = raw.take(MAX_INPUT_CHARS))
    }

    /**
     * Kodu (ya da yapıştırılan eşleme bağlantısını) takas eder. Başarıda
     * anahtar [onPaired]'e geçer; hata dürüstçe gösterilir.
     */
    fun submit() {
        // Hedef, düz kodda seçili PC; yapıştırılan bağlantıda onun kendi adresi.
        val target = PairingForm.resolveTarget(state) ?: return
        if (state.busy) return

        claimCall?.cancel()
        state = state.copy(phase = PairingPhase.Claiming)

        claimCall = client.claim(target.baseUrl, target.code) { result ->
            onMain {
                claimCall = null
                when (result) {
                    is PairingResult.Paired -> {
                        // Anahtar state'e YAZILMAZ; doğrudan tüketilir.
                        onPaired(result.baseUrl, result.apiKey)
                        state = state.copy(
                            code = "",
                            phase = PairingPhase.Paired(result.label, result.baseUrl),
                        )
                    }

                    is PairingResult.Failure ->
                        state = state.copy(
                            phase = PairingPhase.Failed(result.message, result.hint),
                        )
                }
            }
        }
    }

    /** Hata/başarı mesajını temizler; kullanıcı yeniden denemek istediğinde. */
    fun clearPhase() {
        state = state.copy(phase = PairingPhase.Idle)
    }

    /** ViewModel temizlenirken çağrılır. */
    fun dispose() {
        stopDiscovery()
        claimCall?.cancel()
        claimCall = null
    }
}
