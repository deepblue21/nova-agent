package com.nova.agent.llm.local

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message

/** Bridges LiteRT's separate thought channel into NOVA's existing streaming format. */
internal class ModelMessageStream {
    private var thoughtOpen = false

    fun append(message: Message): String = buildString {
        val thought = message.channels["thought"].orEmpty()
        if (thought.isNotEmpty()) {
            if (!thoughtOpen) append("<think>")
            thoughtOpen = true
            append(thought)
        }
        val text = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        if (text.isNotEmpty()) {
            if (thoughtOpen) append("</think>")
            thoughtOpen = false
            append(text)
        }
    }
}
