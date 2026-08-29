package com.nova.agent

import com.nova.agent.data.AppSettings
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Açılışta bağlantı sondası kuralı.
 *
 * Bu test, **emülatörde çalışan uygulamaya bakılarak** bulunan bir hatadan
 * doğdu: temiz kurulumda ilk ekranda "Bağlantı reddedildi: adres bulundu ama
 * bu portta dinleyen yok" yazıyordu. Sebep, varsayılan `baseUrl`in
 * (`10.0.2.2:8088`) hiçbir zaman boş olmaması ve açılışta koşulsuz sonda
 * atılmasıydı — kullanıcı hiç kurmadığı bir bağlantı için hata görüyordu.
 */
class ConnectionStartupProbeTest {

    /** Asıl regresyon: varsayılan ayarlarla sonda ATILMAMALI. */
    @Test
    fun `temiz kurulumda acilista sonda atilmaz`() {
        val fresh = AppSettings()

        // B5'ten sonra adres de boş. Sonda kuralı yine belirtece bakıyor:
        // adres bir gün dolarsa (eşleme, elle giriş) bu test hâlâ doğru şeyi korur.
        assertTrue("varsayılan adres boş — B5", fresh.baseUrl.isBlank())
        assertTrue("varsayılan belirteç boş", fresh.token.isBlank())
        assertFalse(
            GatewayConnectionUiState.shouldProbeOnStart(fresh.baseUrl, fresh.token),
        )
    }

    @Test
    fun `belirtec varsa sonda atilir`() {
        assertTrue(
            GatewayConnectionUiState.shouldProbeOnStart(
                baseUrl = "http://192.168.1.5:8088/v1",
                token = "nv_abc_secret",
            ),
        )
    }

    @Test
    fun `adres bossa sonda atilmaz`() {
        assertFalse(GatewayConnectionUiState.shouldProbeOnStart("", "nv_abc_secret"))
        assertFalse(GatewayConnectionUiState.shouldProbeOnStart("   ", "nv_abc_secret"))
    }

    @Test
    fun `belirtec bossa sonda atilmaz`() {
        assertFalse(
            GatewayConnectionUiState.shouldProbeOnStart("http://192.168.1.5:8088/v1", ""),
        )
    }

    /**
     * Kurulmamış bağlantı bir HATA değil, bilgidir: kullanıcıya bu adımın
     * çevrimdışı kullanım için gerekmediği söylenmeli.
     */
    @Test
    fun `kurulmamis durum hata gibi gorunmez ve yol gosterir`() {
        val state = GatewayConnectionUiState.notConfigured()

        assertEquals(GatewayConnectionStatus.UNKNOWN, state.status)
        assertFalse("hata dili kullanılmamalı", state.message.contains("reddedildi"))
        assertTrue(state.hint.contains("gerekmez"))
        assertTrue("nereye gidileceği yazmalı", state.hint.contains("Ayarlar"))
    }
}
