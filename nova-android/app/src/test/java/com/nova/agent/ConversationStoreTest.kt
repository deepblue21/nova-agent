package com.nova.agent

import com.nova.agent.data.ChatMessage
import com.nova.agent.data.Conversation
import com.nova.agent.data.ConversationStore
import com.nova.agent.data.ConversationText
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStoreTest {

    private fun convo(id: String, updated: Long, vararg msgs: ChatMessage) =
        Conversation(id, ConversationText.titleFrom(msgs.toList()), 1, updated, msgs.toList())

    private fun user(t: String) = ChatMessage("user", t)
    private fun asst(t: String) = ChatMessage("assistant", t)

    private fun tempStore(max: Int = 100): ConversationStore {
        val dir = Files.createTempDirectory("convo_test").toFile()
        return ConversationStore(File(dir, "c.json"), max)
    }

    // ---------- başlık / özet ----------

    @Test
    fun `baslik ilk kullanici mesajindan uretilir`() {
        assertEquals("Yeni sohbet", ConversationText.titleFrom(emptyList()))
        assertEquals("Yeni sohbet", ConversationText.titleFrom(listOf(asst("merhaba"))))
        assertEquals("hava nasıl", ConversationText.titleFrom(listOf(user("hava nasıl"), asst("güzel"))))
        val long = "a".repeat(80)
        assertTrue(ConversationText.titleFrom(listOf(user(long))).endsWith("…"))
    }

    @Test
    fun `cok satirli mesaj tek satira indirilir`() {
        assertEquals("bir iki", ConversationText.titleFrom(listOf(user("bir\n   iki"))))
    }

    // ---------- serileştirme ----------

    @Test
    fun `serilestirme cift yonlu ve mesajlari korur`() {
        val c = convo(
            "id1", 100,
            user("selam"),
            ChatMessage("assistant", "cevap", thoughts = "düşünce", route = "telefon/qwen"),
        )
        val round = ConversationStore.parseList(ConversationStore.serializeList(listOf(c)))
        assertEquals(1, round.size)
        assertEquals("id1", round[0].id)
        assertEquals(2, round[0].messages.size)
        assertEquals("düşünce", round[0].messages[1].thoughts)
        assertEquals("telefon/qwen", round[0].messages[1].route)
    }

    @Test
    fun `bozuk veya bos girdi bos liste dondurur`() {
        assertTrue(ConversationStore.parseList("").isEmpty())
        assertTrue(ConversationStore.parseList("bozuk[").isEmpty())
    }

    // ---------- store ----------

    @Test
    fun `kaydet listele ac sil dongusu`() {
        val store = tempStore()
        store.save(convo("a", 10, user("ilk soru")))
        store.save(convo("b", 20, user("ikinci soru")))
        val list = store.list()
        assertEquals(2, list.size)
        assertEquals("b", list[0].id) // en yeni başta
        assertEquals("ikinci soru", list[0].title)
        assertEquals("ilk soru", store.load("a")!!.title)
        store.delete("a")
        assertEquals(1, store.list().size)
        assertNull(store.load("a"))
    }

    @Test
    fun `ayni id ustune yazar cogaltmaz`() {
        val store = tempStore()
        store.save(convo("a", 10, user("v1")))
        store.save(convo("a", 20, user("v2")))
        assertEquals(1, store.list().size)
        assertEquals("v2", store.load("a")!!.title)
    }

    @Test
    fun `arama baslik ve icerikte eslesir`() {
        val store = tempStore()
        store.save(convo("a", 10, user("kotlin nedir"), asst("bir dil")))
        store.save(convo("b", 20, user("hava durumu")))
        assertEquals(1, store.search("kotlin").size)
        assertEquals(1, store.search("dil").size) // içerikte
        assertEquals(2, store.search("").size)
        assertTrue(store.search("bulunmayan").isEmpty())
    }

    @Test
    fun `sinir asilinca en eskiler duser`() {
        val store = tempStore(max = 2)
        store.save(convo("a", 10, user("a")))
        store.save(convo("b", 20, user("b")))
        store.save(convo("c", 30, user("c")))
        val ids = store.list().map { it.id }
        assertEquals(listOf("c", "b"), ids)
    }
}
