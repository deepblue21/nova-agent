package com.nova.agent.llm.local

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Bir modelin diskteki durumu. */
sealed interface LocalModelDiskState {
    data object NotInstalled : LocalModelDiskState

    /** Yarım kalmış indirme; kaldığı yerden sürdürülebilir. */
    data class Partial(val bytes: Long) : LocalModelDiskState

    /** Dosya tam boyutta mevcut. [verified] = SHA-256 işareti dosyası doğru. */
    data class Installed(val verified: Boolean) : LocalModelDiskState
}

/**
 * Uygulama-özel depodaki model dosyalarını yönetir.
 * Düzen: filesDir/models/<dosya>            → kurulu model
 *        filesDir/models/<dosya>.part       → sürdürülebilir yarım indirme
 *        filesDir/models/<dosya>.sha256.ok  → doğrulama işareti (içerik = özet)
 */
class LocalModelStore(filesDir: File) {

    /**
     * Android yapıcısı. Sınıfın gerçek bağımlılığı `filesDir`den ibaret;
     * `Context` almayı bırakınca `diskState`/`verify`/`delete` mantığı JVM
     * birim testinde gerçek dosyalarla koşturulabiliyor. Bu mantık çevrimdışı
     * kullanımın kalbinde ve daha önce hiç test edilmiyordu.
     */
    constructor(context: Context) : this(context.filesDir)

    val modelsDir: File = File(filesDir, "models")

    fun modelFile(spec: LocalModelSpec): File = File(modelsDir, spec.fileName)
    fun partFile(spec: LocalModelSpec): File = File(modelsDir, spec.fileName + ".part")
    fun markerFile(spec: LocalModelSpec): File = File(modelsDir, spec.fileName + ".sha256.ok")

    fun diskState(spec: LocalModelSpec): LocalModelDiskState {
        val model = modelFile(spec)
        if (model.exists()) {
            if (model.length() != spec.sizeBytes) return LocalModelDiskState.Partial(model.length())
            val marker = markerFile(spec)
            val verified = marker.exists() &&
                runCatching { marker.readText().trim().equals(spec.sha256, ignoreCase = true) }
                    .getOrDefault(false)
            return LocalModelDiskState.Installed(verified)
        }
        val part = partFile(spec)
        if (part.exists() && part.length() > 0) return LocalModelDiskState.Partial(part.length())
        return LocalModelDiskState.NotInstalled
    }

    fun isInstalled(spec: LocalModelSpec): Boolean =
        diskState(spec) is LocalModelDiskState.Installed

    fun delete(spec: LocalModelSpec) {
        modelFile(spec).delete()
        partFile(spec).delete()
        markerFile(spec).delete()
    }

    /**
     * Kurulu dosyayı baştan sona yeniden özetler ve işaret dosyasını günceller.
     * Yavaş bir işlemdir (yüzlerce MB); arka plan dispatcher'ında çağrılmalı.
     */
    fun verify(spec: LocalModelSpec): Boolean {
        val model = modelFile(spec)
        if (!model.exists() || model.length() != spec.sizeBytes) return false
        val digest = model.inputStream().use { sha256Hex(it) }
        val ok = digest.equals(spec.sha256, ignoreCase = true)
        if (ok) {
            runCatching { markerFile(spec).writeText(digest) }
        } else {
            // Y2 — bozuk dosya DİSKTE BIRAKILMAZ.
            // Eskiden yalnız işaret dosyası siliniyor, model dosyası duruyordu;
            // `isInstalled` doğrulamaya bakmadığı için hâlâ "kurulu" görünüyor
            // ve bozuk .litertlm native motora veriliyordu. Oradaki SIGSEGV'yi
            // `catch (Throwable)` yakalayamaz — süreç düşer.
            markerFile(spec).delete()
            model.delete()
        }
        return ok
    }

    /**
     * Doğrulama işaretini yazar. **Başarısını döndürür** — eskiden sonucu
     * yutuyordu.
     *
     * Neden önemli: bunun tipik başarısızlık nedeni diskin dolması, ki bu tam
     * olarak GB'larca modelin indirildiği anda olur. İşaret yazılamayınca
     * indirme "başarılı" diyor, dosya diskte duruyor, ama durum
     * `Installed(verified = false)` kalıyor — ve üretim yolu doğrulanmamış
     * dosyayı çalıştırmayı reddettiği için çevrimdışı sohbet sessizce
     * ölüyordu. Çağıran taraf artık bunu görebiliyor.
     */
    fun writeMarker(spec: LocalModelSpec): Boolean {
        modelsDir.mkdirs()
        return runCatching { markerFile(spec).writeText(spec.sha256) }.isSuccess
    }

    companion object {
        /** Akıştan SHA-256 hesaplar; saf ve JVM'de test edilebilir. */
        fun sha256Hex(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
            return toHex(digest.digest())
        }

        fun toHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                if (v < 0x10) sb.append('0')
                sb.append(Integer.toHexString(v))
            }
            return sb.toString()
        }
    }
}
