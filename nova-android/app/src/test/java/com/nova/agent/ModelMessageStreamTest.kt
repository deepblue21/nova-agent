package com.nova.agent

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.nova.agent.llm.ThinkingText
import com.nova.agent.llm.local.ModelMessageStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMessageStreamTest {
    @Test fun thoughtOnlyChunksAreDeliveredBeforeTheAnswer() {
        val stream = ModelMessageStream()
        val first = stream.append(Message.model(channels = mapOf("thought" to "Step one")))
        assertTrue(first.isNotEmpty()) // The watchdog sees activity without waiting for final content.
        val raw = first + stream.append(Message.model(channels = mapOf("thought" to ", step two"))) +
            stream.append(Message.model(contents = Contents.of("Answer")))
        assertEquals("Step one, step two" to "Answer", ThinkingText.split(raw))
    }

    @Test fun mixedChunkSeparatesReasoningAndFinalText() {
        val raw = ModelMessageStream().append(Message.model(
            contents = Contents.of("42"), channels = mapOf("thought" to "Calculate")))
        assertEquals("Calculate" to "42", ThinkingText.split(raw))
    }

    @Test fun legacyInlineThinkingIsUnchangedAndInterruptedThoughtIsNotAnAnswer() {
        val legacy = "<think>Check</think>Done"
        assertEquals(legacy, ModelMessageStream().append(Message.model(contents = Contents.of(legacy))))
        val stream = ModelMessageStream()
        val partial = stream.append(Message.model(channels = mapOf("thought" to "Still thinking")))
        assertEquals("Still thinking" to "", ThinkingText.split(partial))
        assertEquals("", stream.append(Message.model()))
    }
}
