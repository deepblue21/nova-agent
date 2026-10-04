package com.nova.agent

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nova.agent.data.AppSettings
import com.nova.agent.data.SettingsStore
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.local.LocalModelCatalog
import com.nova.agent.net.GatewayConnectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real ViewModel without a model download or an external Gateway. */
@RunWith(AndroidJUnit4::class)
class PhoneFirstRoutingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val settingsStore = SettingsStore(app)
    private val owner = ViewModelStore()
    private lateinit var originalSettings: AppSettings
    private lateinit var vm: NovaViewModel

    @Before
    fun prepareLocalOnlyTestEnvironment() = runBlocking {
        originalSettings = settingsStore.load()
        settingsStore.save(AppSettings(baseUrl = "http://127.0.0.1:1/v1", persona = "phone-routing-test"))
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(app))[NovaViewModel::class.java]
        }
        withTimeout(10_000) {
            var loaded = false
            while (!loaded) {
                instrumentation.runOnMainSync { loaded = vm.settings.persona == "phone-routing-test" }
                if (!loaded) delay(10)
            }
        }
        instrumentation.runOnMainSync {
            // Do not modify any installed model: select a missing catalog entry.
            val missingModel = LocalModelCatalog.entries.first { !vm.local.isInstalled(it.id) }
            vm.setLocalModel(missingModel.id)
            vm.setExecutionPolicy(ExecutionPolicy.LOCAL_FIRST)
        }
    }

    @After
    fun restoreSettingsAndReleaseViewModel() = runBlocking {
        instrumentation.runOnMainSync {
            if (::vm.isInitialized) {
                // A failed assertion must not persist the synthetic conversation.
                vm.messages.clear()
            }
            owner.clear()
        }
        if (::originalSettings.isInitialized) settingsStore.save(originalSettings)
    }

    @Test
    fun selectingOfflineInvalidatesPendingGatewayConsent() {
        instrumentation.runOnMainSync {
            vm.send("Telefon yönlendirme testi")
            assertNotNull(vm.pendingFallback)
            vm.setExecutionPolicy(ExecutionPolicy.LOCAL_ONLY)
            assertNull(vm.pendingFallback)
            vm.approveFallback()
            assertFalse(vm.busy)
            assertEquals(listOf("user"), vm.messages.map { it.role })
        }
    }

    @Test
    fun unavailableGatewayDoesNotConsumeConsentOrStartRemoteGeneration() {
        instrumentation.runOnMainSync {
            vm.send("Bağlantısız devir testi")
            val pending = vm.pendingFallback
            assertNotNull(pending)
            assertEquals(GatewayConnectionStatus.UNKNOWN, vm.connectionState.status)
            vm.approveFallback()
            assertEquals(pending, vm.pendingFallback)
            assertFalse(vm.busy)
            assertEquals(listOf("user"), vm.messages.map { it.role })
        }
    }
}
