package com.nova.agent

import com.nova.agent.llm.EngineRouter
import com.nova.agent.llm.ExecutionPolicy
import com.nova.agent.llm.HybridInputs
import com.nova.agent.llm.RouteDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRouterTest {

    @Test
    fun `gateway only her zaman gateway'e gider`() {
        val decision = EngineRouter.decide(ExecutionPolicy.GATEWAY_ONLY, "qwen3-0.6b-int4", true)
        assertEquals(RouteDecision.Gateway, decision)
    }

    @Test
    fun `local first kurulu modelle telefona gider`() {
        val decision = EngineRouter.decide(ExecutionPolicy.LOCAL_FIRST, "qwen3-0.6b-int4", true)
        assertTrue(decision is RouteDecision.Local)
        assertEquals("qwen3-0.6b-int4", (decision as RouteDecision.Local).modelId)
    }

    @Test
    fun `local first model yoksa SESSIZCE gateway'e gitmez`() {
        val decision = EngineRouter.decide(ExecutionPolicy.LOCAL_FIRST, "qwen3-0.6b-int4", false)
        assertTrue(decision is RouteDecision.LocalNeedsSetup)
        assertFalse(decision is RouteDecision.Gateway)
    }

    @Test
    fun `bilinmeyen politika id'si varsayilan olarak gateway olur`() {
        assertEquals(ExecutionPolicy.GATEWAY_ONLY, ExecutionPolicy.fromId("bilinmeyen"))
        assertEquals(ExecutionPolicy.GATEWAY_ONLY, ExecutionPolicy.fromId(null))
    }

    @Test
    fun `faz 3 ile tum politikalar secilebilir`() {
        assertTrue(ExecutionPolicy.GATEWAY_ONLY.selectableNow)
        assertTrue(ExecutionPolicy.LOCAL_FIRST.selectableNow)
        assertTrue(ExecutionPolicy.LOCAL_ONLY.selectableNow)
        assertTrue(ExecutionPolicy.HYBRID.selectableNow)
    }

    @Test
    fun `cevrimdisi politikada gateway devri onerilmez`() {
        assertFalse(ExecutionPolicy.LOCAL_ONLY.allowsGatewayFallback)
        assertTrue(ExecutionPolicy.LOCAL_FIRST.allowsGatewayFallback)
        assertTrue(ExecutionPolicy.GATEWAY_ONLY.allowsGatewayFallback)
    }

    @Test
    fun `cevrimdisi model yoksa kurulum ister gateway'e ASLA gitmez`() {
        val decision = EngineRouter.decide(ExecutionPolicy.LOCAL_ONLY, "qwen3-0.6b-int4", false)
        assertTrue(decision is RouteDecision.LocalNeedsSetup)
        assertFalse(decision is RouteDecision.Gateway)
    }

    @Test
    fun `cevrimdisi kurulu modelle telefonda calisir`() {
        val decision = EngineRouter.decide(ExecutionPolicy.LOCAL_ONLY, "qwen3-0.6b-int4", true)
        assertTrue(decisi