package com.nova.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nova.agent.data.GatewayCatalog
import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CANLI uçtan uca entegrasyon testi — çalışan bir gateway (+Ollama) gerektirir.
 *
 * Emülatörden host'un localhost'una `10.0.2.2` ile ulaşır (AppSettings varsayılanı).
 * Gerçek üretim ağ kodunu (GatewayConnectionClient) kullanır; mock yoktur.
 *
 * Standart bağlı test turunda deterministik kalmak için atlanır. Canlı doğrulama:
 * `-Pandroid.testInstrumentationRunnerArguments.runLiveGateway=true`
 *
 * Çakışan/kimlik doğrulamalı bir yerel Gateway varsa adres ve token değiştirilebilir:
 * `-Pandroid.testInstrumentationRunnerArguments.liveGatewayBaseUrl=http://10.0.2.2:18089/v1`
 * `-Pandroid.testInstrumentationRunnerArguments.liveGatewayToken=<token>`
 */
@RunWith(AndroidJUnit4::class)
class LiveGatewayOllamaConnectionTest {
    private val instrumentationArgs
        get() = InstrumentationRegistry.getArguments()

    private val baseUrl: String
        get() = instrumentationArgs.getString("liveGatewayBaseUrl")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "http://10.0.2.2:8088/v1"

    private val token: String
        get() = instrumentationArgs.getString("liveGatewayToken").orEmpty().trim()

    @Test
    fun gatewayReachableFromEmulator() {
        assumeLiveGatewayRequested()
        val client = GatewayConnectionClient()
        val latch = CountDownLatch(1)
        var result: GatewayConnectionResult? = null

        client.test(baseUrl, token) { r ->
            result = r
            latch.countDown()
        }

        assertTrue(
            "Gateway 15 sn icinde yanit vermedi — 10.0.2.2:8088 acik mi? (docker compose / start-horus)",
            latch.await(15, TimeUnit.SECONDS),
        )
        val r = result
        // Gercek bir HTTP yaniti geldiyse ag yolu calisiyor demektir (Ready ya da AuthRequired).
        // Failure = emulator host gateway'ine ulasamadi (baglanti reddi / timeout / DNS).
        assertTrue(
            "Beklenen Ready ya da AuthRequired; gelen: $r",
            r is GatewayConnectionResult.Ready || r is GatewayConnectionResult.AuthRequired,
        )
    }

    @Test
    fun liveCatalogContainsOllamaModels() {
        assumeLiveGatewayRequested()
        val client = GatewayConnectionClient()
        val latch = CountDownLatch(1)
        var catalog: GatewayCatalog? = null

        client.fetchModels(baseUrl, token) { c ->
            catalog = c
            latch.countDown()
        }

        assertTrue("fetchModels 15 sn icinde donmedi", latch.await(15, TimeUnit.SECONDS))
        val c = catalog
        assertNotNull(
            "Katalog bos dondu — gateway /v1/models 200 vermedi (auth mi gerekiyor?).",
            c,
        )
        val ollamaModels = c!!.models.filter { it.model.startsWith("ollama/") }
        assertTrue(
            "Canli katalogda Ollama modeli yok. Gelen modeller: ${c.models.map { it.model }}",
            ollamaModels.isNotEmpty(),
        )
    }

    private fun assumeLiveGatewayRequested() {
        val enabled = instrumentationArgs
            .getString("runLiveGateway")
            .equals("true", ignoreCase = true)
        assumeTrue(
            "Canlı gateway testi yalnız runLiveGateway=true ile çalıştırılır.",
            enabled,
        )
    }
}
