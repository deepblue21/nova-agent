package com.nova.agent.llm.local.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telefonda TAMAMEN çevrimdışı çalışan araç seti (Faz 2 — agentic çekirdek).
 *
 * Kurallar:
 * - Hiçbir araç ağa çıkmaz ve ek Android izni gerektirmez.
 * - Araç hataları LiteRT-LM ToolManager tarafından yakalanıp modele metin
 *   olarak döner; uygulamayı düşüremez.
 * - Fonksiyon adları bilinçli olarak ASCII'dir (şablon/tokenizer güvenliği).
 */
class HorusToolSet(
    private val appContext: Context,
    private val noteStore: NoteStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ToolSet {

    @Tool(description = "Şu anki tarih, saat ve haftanın gününü verir. Tarih/saat sorularında kullan.")
    fun simdikiTarihSaat(): Map<String, Any> {
        val now = Date(clock())
        val tr = Locale("tr", "TR")
        return mapOf(
            "tarih" to SimpleDateFormat("d MMMM yyyy", tr).format(now),
            "saat" to SimpleDateFormat("HH:mm", tr).format(now),
            "gun" to SimpleDateFormat("EEEE", tr).format(now),
        )
    }

    @Tool(
        description = "Matematik ifadesini hesaplar. Desteklenen işlemler: + - * / % ^ ve parantez. " +
            "Örnek: (12.5*4)-3^2",
    )
    fun hesapla(
        @ToolParam(description = "Hesaplanacak ifade, örn. 23*7+1") ifade: String,
    ): Map<String, Any> = when (val result = Calculator.evaluate(ifade)) {
        is Calculator.Outcome.Ok -> mapOf("sonuc" to result.formatted)
        is Calculator.Outcome.Error -> mapOf("hata" to result.message)
    }

    @Tool(
        description = "Telefonun anlık durumunu verir: pil yüzdesi, şarj durumu, boş RAM, " +
            "boş depolama ve uçak modu. Cihaz durumu sorularında kullan.",
    )
    fun cihazDurumu(): Map<String, Any> {
        val battery = readBattery()
        val memory = readMemory()
        val freeDiskGb = appContext.filesDir.usableSpace / 1_073_741_824.0
        val airplane = Settings.Global.getInt(
            appContext.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
        return mapOf(
            "pil_yuzdesi" to battery.first,
            "sarj_oluyor" to battery.second,
            "bos_ram_mb" to memory.first,
            "toplam_ram_mb" to memory.second,
            "bos_depolama_gb" to String.format(Locale.US, "%.1f", freeDiskGb),
            "ucak_modu" to airplane,
        )
    }

    @Tool(description = "Kısa bir notu telefonun çevrimdışı not defterine kaydeder. Veri cihazdan çıkmaz.")
    fun notKaydet(
        @ToolParam(description = "Kaydedilecek not metni") metin: String,
    ): Map<String, Any> {
        val trimmed = metin.trim()
        if (trimmed.isEmpty()) return mapOf("hata" to "Not boş olamaz")
        val total = noteStore.add(trimmed, clock())
        return mapOf("kaydedildi" to true, "toplam_not" to total)
    }

    @Tool(description = "Çevrimdışı not defterindeki son notları listeler.")
    fun notlariListele(
        @ToolParam(description = "Kaç not gösterilsin (varsayılan 10)") adet: Int = 10,
    ): Map<String, Any> {
        val notes = noteStore.list(adet)
        return if (notes.isEmpty()) {
            mapOf("bilgi" to "Kayıtlı not yok")
        } else {
            mapOf("notlar" to notes)
        }
    }

    private fun readBattery(): Pair<Int, Boolean> {
        val intent: Intent? = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    private fun readMemory(): Pair<Long, Long> {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0L to 0L
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return (info.availMem / 1_048_576) to (info.totalMem / 1_048_576)
    }

    companion object {
        /** Modeller ekranında gösterilen özet; yetenek listesi arayüzde taklitsiz sergilenir. */
        const val SUMMARY = "saat/tarih · hesap makinesi · cihaz durumu · not defteri"
    }
}
