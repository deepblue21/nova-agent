package com.nova.agent

import com.nova.agent.data.ChatMessage
import com.nova.agent.data.Conversation
import com.nova.agent.data.ConversationExporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExporterTest {

    private fun convo(vararg msgs: ChatMessage) =
        Conversation("id", "Kotlin soruları", 0, 1_752_000_000_000, msgs.toList())

    @Test
    fun `markdown baslik rol ve icerik icerir`() {
        val md = ConversationExporter.toMarkdown(
            convo(
                ChatMessage("user", "merhaba"),
                ChatMessage("assistant", "selam, nasıl yardım edebilirim?", thoughts = "gizli düşünce"),
            ),
        )
        assertTrue(md.contains("# Kotlin soruları"))
        assertTrue(md.contains("**Sen:** merhaba"))
        assertTrue(md.contains("**NOVA:** selam"))
        // Düşünme paylaşıma dahil edilmez.
        assertFalse(md.contains("gizli düşünce"))
    }

    @Test
    fun `bos mesajlar atlanir ve tek newline ile biter`() {
        val md = ConversationExporter.toMarkdown(
            convo(
                ChatMessage("user", "soru"),
                ChatMessage("assistant", "", streaming = false),
            ),
        )
        assertFalse(md.contains("**NOVA:**"))
        assertTrue(md.endsWith("\n"))
        assertFalse(md.endsWith("\n\n\n"))
    }

    @Test
    fun `bassiz sohbet varsayilan basligi kullanir`() {
        val md = ConversationExporter.toMarkdown(
            Conversation("id", "", 0, 0, listOf(ChatMessage("user", "x"))),
        )
        assertTrue(md.contains("# Sohbet"))
    }
}
