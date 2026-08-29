package com.nova.agent.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Yerel ağdaki Horus Gateway'lerini mDNS/DNS-SD ile bulur.
 *
 * Karar mantığı burada DEĞİL — TXT çözümlemesi, yinelenen ayıklama ve sıralama
 * [GatewayDiscovery] içinde saf ve testlidir. Bu dosya yalnız Android
 * `NsdManager` API'sinin kabuğu.
 *
 * Dürüst sınırlar:
 *  • NSD yalnız aynı yerel ağda çalışır. Mobil veride hiçbir şey bulunmaz —
 *    bu bir hata değildir, kullanıcıya QR yolu gösterilir.
 *  • Bazı ev yönlendiricileri istemci yalıtımı (AP isolation) veya multicast
 *    filtresi uygular; o durumda liste boş kalır. Taklit etmiyoruz.
 *  • API 34 öncesinde `resolveService` aynı anda tek çözümlemeyi güvenilir
 *    yapar; bu yüzden çözümlemeler sıraya alınır.
 */
class NsdGatewayDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsd: NsdManager? =
        appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /** Keşif akışı: her değişimde birleşik, sıralı liste yayınlar. */
    fun discover(): Flow<List<DiscoveredGateway>> = callbackFlow {
        val manager = nsd
        if (manager == null) {
            // Cihazda NSD yok: boş liste yayınla, akışı kapat. Sessiz kalma.
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val found = LinkedHashMap<String, DiscoveredGateway>()

        // mDNS servis adı -> baseUrl. `onServiceLost` YALNIZ servis adını bilir;
        // eşleştirmeyi `displayName` üzerinden yapmak çalışmıyordu çünkü o ad
        // TXT'teki `name` alanından geliyor ve servis adıyla aynı olmak zorunda
        // değil. Sonuç: kapanan PC listede kalıyor, kullanıcı ölü bir kayıt için
        // boşuna eşleme kodu yakıyordu.
        val byServiceName = LinkedHashMap<String, String>()

        val resolveExecutor = Executors.newSingleThreadExecutor()

        // API 34+ `registerServiceInfoCallback` HİÇ geri alınmıyordu. Panel her
        // açılışta bulunan her servis için yeni bir kayıt bırakıyor, kayıtlar
        // süreç ölene kadar birikiyor ve kapanmış akışa veri göndermeye devam
        // ediyordu — "arka planda sürmemeli" sözleşmesinin tam tersi.
        val registered = java.util.concurrent.CopyOnWriteArrayList<NsdManager.ServiceInfoCallback>()

        fun publish() {
            trySend(GatewayDiscovery.merge(found.values.toList()))
        }

        fun accept(serviceName: String?, info: NsdServiceInfo) {
            val gateway = GatewayDiscovery.fromService(
                serviceName = serviceName ?: info.serviceName,
                host = hostOf(info),
                port = info.port,
                txt = txtOf(info),
            ) ?: return
            found[gateway.baseUrl] = gateway
            (serviceName ?: info.serviceName)?.let { byServiceName[it] = gateway.baseUrl }
            publish()
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.orEmpty().contains(GatewayDiscovery.SERVICE_TYPE)) return
                val serviceName = info.serviceName
                resolve(manager, info, resolveExecutor, registered) { resolved ->
                    accept(serviceName, resolved)
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                val name = info.serviceName ?: return
                val baseUrl = byServiceName.remove(name)
                val removed = if (baseUrl != null) {
                    found.remove(baseUrl) != null
                } else {
                    // Servis adı hiç eşleşmediyse (çözümleme tamamlanmadan
                    // kaybolduysa) eski davranışa düş: en azından bir şey dene.
                    found.entries.removeAll { it.value.displayName == name }
                }
                if (removed) publish()
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Başlatılamadıysa akışı boş bırakıp kapat; kullanıcı QR'a düşsün.
                trySend(emptyList())
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        manager.discoverServices(
            GatewayDiscovery.NSD_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )

        awaitClose {
            runCatching { manager.stopServiceDiscovery(discoveryListener) }
            registered.forEach { runCatching { manager.unregisterServiceInfoCallback(it) } }
            registered.clear()
            resolveExecutor.shutdownNow()
        }
    }

    private fun resolve(
        manager: NsdManager,
        info: NsdServiceInfo,
        executor: java.util.concurrent.ExecutorService,
        registered: MutableList<NsdManager.ServiceInfoCallback>,
        onResolved: (NsdServiceInfo) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= 34) {
            // API 34+: resolveService yerine sürekli bilgi geri çağrısı.
            // Kayıt LİSTELENİR: akış kapanırken geri alınabilmesi gerekiyor.
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    registered.remove(this)
                }
                override fun onServiceUpdated(updated: NsdServiceInfo) = onResolved(updated)
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() {
                    registered.remove(this)
                }
            }
            registered.add(callback)
            runCatching { manager.registerServiceInfoCallback(info, executor, callback) }
                .onFailure { registered.remove(callback) }
            return
        }

        // API 34 ÖNCESİ: `resolveService` ASENKRONDUR. Eski kod onu tek iş
        // parçacıklı bir havuza atıyordu ama iş parçacığı çağrıyı BAŞLATIP
        // hemen dönüyordu; yani sıraya alma diye bir şey yoktu. İki PC arka
        // arkaya bulunduğunda ikinci çağrı `FAILURE_ALREADY_ACTIVE` (3) alıyor,
        // `onResolveFailed` de onu sessizce yutuyordu: ikinci PC listeye HİÇ
        // düşmüyordu. Şimdi iş parçacığı çözümleme bitene kadar bekliyor, yani
        // kuyruk gerçekten sıralı.
        @Suppress("DEPRECATION")
        executor.execute {
            val done = java.util.concurrent.CountDownLatch(1)
            runCatching {
                manager.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                            done.countDown()
                        }
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            onResolved(resolved)
                            done.countDown()
                        }
                    },
                )
            }.onFailure { done.countDown() }
            // Cevap gelmezse sonsuza kadar kuyruğu kilitlemeyiz.
            runCatching { done.await(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        }
    }

    private companion object {

        /** Tek çözümlemenin kuyruğu kilitleyebileceği en uzun süre. */
        const val RESOLVE_TIMEOUT_SECONDS = 8L

        /** API 34'te `host` kullanımdan kalktı; `hostAddresses` ilk IPv4'ü tercih edilir. */
        fun hostOf(info: NsdServiceInfo): String? {
            if (Build.VERSION.SDK_INT >= 34) {
                val addresses = runCatching { info.hostAddresses }.getOrNull().orEmpty()
                val ipv4 = addresses.firstOrNull { it.address?.size == 4 }
                (ipv4 ?: addresses.firstOrNull())?.hostAddress?.let { return it }
            }
            @Suppress("DEPRECATION")
            return info.host?.hostAddress
        }

        /** TXT kayıtlarını UTF-8 dizgelere çevirir. Bozuk değerler atlanır. */
        fun txtOf(info: NsdServiceInfo): Map<String, String> {
            val attrs = runCatching { info.attributes }.getOrNull() ?: return emptyMap()
            val out = LinkedHashMap<String, String>(attrs.size)
            for ((key, value) in attrs) {
                if (key.isNullOrBlank()) continue
                out[key] = value?.toString(Charsets.UTF_8) ?: ""
            }
            return out
        }
    }
}
