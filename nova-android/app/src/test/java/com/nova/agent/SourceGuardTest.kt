package com.nova.agent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KOD DENETİM TESTLERİ — düzeltilen kusurların geri gelmesini engeller.
 *
 * Neden kaynak taranıyor: denetimde çıkan kusurların bir kısmı bir fonksiyonun
 * YANLIŞ ÇALIŞMASI değil, **doğru fonksiyonun çağrılmaması**ydı. Bunlar davranış
 * testiyle yakalanamıyordu, çünkü saf fonksiyonlar zaten doğruydu ve testleri
 * yeşildi. En net örnek Y1: `NetworkPolicy`in 12 testi geçiyordu ama politika
 * sohbet yolunda hiç çağrılmıyordu.
 *
 * Bu testler o boşluğu kapatır: "şu kural şu dosyada duruyor mu" sorusunu
 * doğrudan sorar. Kırılgan olmamaları için dar ve gerekçeli tutulmuştur —
 * biçim değil, yalnız kuralın varlığı sınanır.
 */
class SourceGuardTest {

    private fun source(relative: String): String {
        // Birim testleri modül dizininden (app/) koşar; CI ve IDE'de aynı.
        val candidates = listOf(
            File("src/main/java/com/nova/agent/$relative"),
            File("app/src/main/java/com/nova/agent/$relative"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertTrue(
            "kaynak bulunamadi: $relative (calisma dizini=${File(".").absolutePath})",
            file != null,
        )
        return stripComments(file!!.readText())
    }

    /**
     * Yorumları çıkarır — guard'lar KODU denetlemeli, yorumu değil.
     *
     * İlk koşuda iki test tam bu yüzden kırmızıya döndü: düzeltmelerin
     * KDoc'larında eski hatalı satır ("Eskiden `sortedBy { BigInteger(it.id) }`
     * idi") gerekçe olarak aynen yazılıydı ve "bu dizge kodda geçmesin" kuralı
     * onu yakaladı. Guard doğru çalışıyordu; ölçtüğü metin yanlıştı.
     *
     * Ters yön de önemli: yorumu ayıklamak, bir kuralın YALNIZCA yorumda
     * yazarak sağlanmasını da imkânsız kılar — "şunu çağırmalı" diyen bir
     * guard, gerçekten çağrıldığını görmek zorunda.
     */
    private fun stripComments(source: String): String {
        val noBlock = source.replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        return noBlock.lines().joinToString("\n") { line ->
            var cut = -1
            var i = 0
            while (i < line.length - 1) {
                // "://" içindeki çift eğik çizgi yorum değildir (URL'ler).
                if (line[i] == '/' && line[i + 1] == '/' && (i == 0 || line[i - 1] != ':')) {
                    cut = i
                    break
                }
                i++
            }
            if (cut >= 0) line.substring(0, cut) else line
        }
    }

    // ---------- Y1: ag politikasi bogaz noktasinda ----------

    @Test
    fun `canonicalBaseUrl ag politikasini uygular`() {
        val text = source("net/GatewayConnectionClient.kt")
        assertTrue(
            "canonicalBaseUrl NetworkPolicy'yi cagirmali - tum giden istekler buradan geciyor",
            text.contains("NetworkPolicy.allowsHost"),
        )
    }

    @Test
    fun `giden istemciler kendi URL'lerini elle kurmaz`() {
        // Her biri kullanici girdisinden URL kuruyor; politikanin uygulandigi
        // TEK yer canonicalBaseUrl oldugu icin hepsi oradan gecmeli.
        listOf(
            "net/NovaClient.kt",
            "net/MobileTaskClient.kt",
        ).forEach { path ->
            assertTrue(
                "$path canonicalBaseUrl kullanmali",
                source(path).contains("canonicalBaseUrl"),
            )
        }
    }

    // ---------- G1/G2: gizlilik ----------

    @Test
    fun `otomatik devir gizlilik kapisindan gecer`() {
        val text = source("NovaViewModel.kt")
        val gate = text.substringAfter("private fun autoHandoffAfterLocalError")
            .substringBefore("\n    /**")
        assertTrue(
            "hassas konusma yerel hata bahanesiyle sessizce disari cikamaz",
            gate.contains("conversationSensitive()"),
        )
    }

    @Test
    fun `gizlilik karari son mesaja degil tum konusmaya bakar`() {
        val text = source("NovaViewModel.kt")
        assertFalse(
            "yalniz son isteme bakmak gecmisteki sirri disari kaciriyordu",
            text.contains("PrivacyClassifier.isSensitive(lastPrompt)"),
        )
        assertTrue(text.contains("PrivacyClassifier.isAnySensitive"))
    }

    // ---------- T1: olay kimligi sozlesmesi ----------

    @Test
    fun `reducer olay kimligini ham BigInteger'a vermez`() {
        val text = source("feature/tasks/MobileTaskReducer.kt")
        assertFalse(
            "serbest metin kimlik NumberFormatException ile cokertiyordu",
            text.contains("sortedBy { BigInteger(it.id) }"),
        )
        assertTrue(text.contains("orderEvents"))
    }

    // ---------- K2: indirme yasam dongusunden bagimsiz ----------

    @Test
    fun `shutdown indirmeyi iptal etmez`() {
        val text = source("llm/LocalLlmController.kt")
        val shutdown = text.substringAfter("fun shutdown()").substringBefore("}")
        assertFalse(
            "uygulama kapaninca 8,6 GB'lik indirme olmemeli",
            shutdown.contains("cancelDownload") || shutdown.contains("downloadHandles"),
        )
    }

    @Test
    fun `indirme WorkManager isi olarak kuyruga alinir`() {
        val text = source("llm/LocalLlmController.kt")
        assertTrue(text.contains("enqueueUniqueWork"))
        assertFalse(
            "indirme artik viewModelScope'ta kosmamali",
            text.contains("downloader.download("),
        )
    }

    // ---------- Y2: dogrulanmamis model motora gitmez ----------

    @Test
    fun `uretim dogrulanmamis modeli reddeder`() {
        val text = source("llm/LocalLlmController.kt")
        assertTrue(
            "exists() + length() yetmez; bozuk .litertlm native cokme demek",
            text.contains("diskState.verified"),
        )
    }

    @Test
    fun `dogrulamasi basarisiz model diskte birakilmaz`() {
        val text = source("llm/local/LocalModelStore.kt")
        val verify = text.substringAfter("fun verify(").substringBefore("fun writeMarker")
        assertTrue(
            "bozuk dosya silinmezse isInstalled hala kurulu diyor",
            verify.contains("model.delete()"),
        )
    }

    // ---------- Y4: mevcut hedef dogrulanmadan basarili sayilmaz ----------

    @Test
    fun `mevcut hedef dosya SHA atlanarak basarili sayilmaz`() {
        val text = source("llm/local/ModelDownloader.kt")
        assertFalse(
            "boyut dogru diye icerik dogru sayilamaz",
            text.contains("if (target.exists()) return Result.Success"),
        )
    }

    // ---------- V1: gecmis atomik yazilir ----------

    @Test
    fun `sohbet gecmisi atomik yazilir`() {
        val text = source("data/ConversationStore.kt")
        assertTrue("gecici dosya + rename olmali", text.contains("renameTo(file)"))
        assertTrue("rename oncesi fsync sart", text.contains("fd.sync()"))
    }

    @Test
    fun `bozuk gecmis dosyasi karantinaya alinir`() {
        val text = source("data/ConversationStore.kt")
        assertTrue(
            "bozuk dosya sessizce sifirlanip ilk save ile ezilmemeli",
            text.contains(".corrupt"),
        )
    }

    // ---------- gizlilik: sohbet icerigi loglanmaz ----------

    // ---------- T3: temiz kapanis da bir kopmadir ----------

    @Test
    fun `SSE temiz kapanisinda yeniden baglanilir`() {
        val text = source("feature/tasks/MobileTaskViewModel.kt")
        val onClosed = text.substringAfter("override fun onClosed()").substringBefore("override fun onError")
        assertTrue(
            "proxy akisi HATASIZ kapatiyor; onError hic gelmiyor, " +
                "yeniden baglanma yalniz buradan tetiklenebilir",
            onClosed.contains("scheduleReconnect()"),
        )
    }

    @Test
    fun `terminale gecen gorevde akis kapatilir`() {
        val text = source("feature/tasks/MobileTaskViewModel.kt")
        val onEvent = text.substringAfter("override fun onEvent").substringBefore("override fun onClosed")
        assertTrue(
            "biten gorevin bosta baglantisi ekran acik kaldigi surece tasiniyordu",
            onEvent.contains("disconnect()"),
        )
    }

    @Test
    fun `yeniden deneme sinirsiz degil`() {
        val text = source("feature/tasks/MobileTaskViewModel.kt")
        assertTrue(
            "sunucu gercekten kapaliysa sonsuza dek denemek yerine durum soylenmeli",
            text.contains("MAX_RECONNECT_ATTEMPTS"),
        )
        assertTrue(
            "ekranda cikissiz hata kalmamali: kullanicinin yeniden deneme yolu olmali",
            text.contains("fun retryStream"),
        )
    }

    // ---------- T4: onay paneli parmagin altinda degismez ----------

    @Test
    fun `bekleyen onay yeni istekle ezilmez`() {
        val text = source("feature/tasks/MobileTaskReducer.kt")
        assertFalse(
            "ekrandaki onay dogrudan yenisiyle degistirilirse yanlis eylem onaylanir",
            Regex("""confirmation\.requested"\s*->\s*mutation\.event\.confirmation""")
                .containsMatchIn(text),
        )
        assertTrue(text.contains("enqueueConfirmation"))
        assertTrue(text.contains("dequeueConfirmation"))
    }

    // ---------- E1: dusunme sizintisi ----------

    @Test
    fun `gateway yolu da dusunmeyi ayiklar`() {
        val text = source("NovaViewModel.kt")
        assertFalse(
            "ham metni dogrudan balona yazmak <think> blogunu iceriye ve disa aktarmaya siziyordu",
            text.contains("content = sb.toString()"),
        )
        val finish = text.substringAfter("private fun finish(speak: Boolean)").substringBefore("private fun updateLast")
        assertTrue(
            "gateway yolu (finish) dusunmeyi ayiklamiyordu; yalniz finishLocal ayikliyordu",
            finish.contains("renderStreamed()"),
        )
    }

    // ---------- S1: TTS sinirini asan yanit ----------

    @Test
    fun `TTS metni sinir altinda parcalanir`() {
        val text = source("voice/SpeechManager.kt")
        assertTrue(
            "sinir asilinca speak ERROR doner ve HICBIR geri cagri gelmez",
            text.contains("TtsChunker.chunk"),
        )
        assertTrue(
            "speak'in donus degeri kontrol edilmezse hata tumuyle sessiz kalir",
            text.contains("TextToSpeech.ERROR"),
        )
    }

    // ---------- M1: kendi iptali sunucu hatasi gibi gosterilmez ----------

    @Test
    fun `kullanici iptali gateway hatasi olarak yazilmaz`() {
        val client = source("net/NovaClient.kt")
        assertTrue(
            "iptal isaretlenmeli; metne bakmak (t.message == Canceled) yerellestirmeyle bozulur",
            client.contains("fun cancelStream"),
        )
        val vm = source("NovaViewModel.kt")
        assertFalse(
            "ViewModel akisi dogrudan iptal ederse istemci bunun kullanici istegi oldugunu bilemez",
            vm.contains("es?.cancel()"),
        )
    }

    // ---------- J1: Android org.json null farki ----------

    @Test
    fun `ham optString kullanilmaz`() {
        val root = listOf(File("src/main/java/com/nova/agent"), File("app/src/main/java/com/nova/agent"))
            .first { it.exists() }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "JsonExt.kt" }
            .filter { stripComments(it.readText()).contains(".optString(") }
            .map { it.name }
            .toList()
        assertTrue(
            "Android org.json JSON null'da \"null\" DIZESI dondurur; JVM testi bunu " +
                "yakalayamaz cunku referans uygulama dogru davranir - ihlal: $offenders",
            offenders.isEmpty(),
        )
    }

    // ---------- U: arayuz mantik tutarliligi ----------

    @Test
    fun `izin karti model yokken dogru basligi kullanir`() {
        val text = source("feature/chat/ChatScreen.kt")
        assertFalse(
            "baslik sabit yazilirsa model HIC YOKKEN de 'yanit veremedi' denir",
            Regex("""Text\(\s*"Telefon modeli yanit veremedi"""").containsMatchIn(
                text.replace("yanıt", "yanit"),
            ),
        )
        assertTrue(text.contains("FallbackKind.NO_LOCAL_MODEL"))
    }

    @Test
    fun `PC'ye gonder yalniz PC ulasilabilirken onerilir`() {
        val text = source("feature/chat/ChatScreen.kt")
        assertTrue(
            "varsayilan kurulumda gateway adresi BOS; yalniz politikaya bakmak, " +
                "kesin basarisiz olacak bir eylemi tek cikis yolu olarak sunuyordu",
            text.contains("val canSendToPc = allowGateway && gatewayReady"),
        )
    }

    @Test
    fun `sohbet cipleri eylemi durumdan ayirir`() {
        val text = source("feature/chat/ChatScreen.kt")
        assertTrue(
            "sohbeti cihaz disina cikaran 'PC ajanina devret' pasif bir durum " +
                "etiketiyle ayni gorunemez",
            text.contains("action = true"),
        )
    }

    @Test
    fun `gorev ekrani baglantisiz girdi sunmaz`() {
        val text = source("feature/tasks/MobileTaskScreen.kt")
        val empty = text.substringAfter("private fun TaskEmptyState").substringBefore("private fun QuickPrompt")
        assertFalse(
            "gonderilemeyecek bir girdiyi etkin gostermek olu alan uretir",
            empty.contains("""QuickPrompt("Android""") &&
                !empty.substringBefore("""QuickPrompt("Android""").contains("if (connected)"),
        )
        assertTrue(empty.contains("if (connected)"))
    }

    @Test
    fun `model listesi basligi kurulu olmayani cihazda gostermez`() {
        val text = source("feature/models/ModelsScreen.kt")
        assertFalse(
            "liste katalogun tamamini gosteriyor; 'CIHAZDAKI MODELLER' basligi yalan",
            text.contains("CİHAZDAKİ MODELLER"),
        )
    }

    @Test
    fun `hazir olmayan cevrimdisi durumu onay ikonu kullanmaz`() {
        // Yalnız çevrimdışı hazırlık kartı denetlenir. Gateway model satırındaki
        // ✓ MEŞRU: orada "bu model seçili" anlamına geliyor ve durumla uyumlu.
        // İlk sürümde guard tüm dosyaya bakıyordu ve o doğru kullanımı da
        // yakaladı — kural doğruydu, kapsamı genişti.
        val card = source("feature/models/ModelsScreen.kt")
            .substringAfter("private fun OfflineReadinessCard")
            .substringBefore("private fun ")
        assertFalse(
            "onay isareti 'tamamlandi' demektir, 'model gerekli' ile celisir; " +
                "yalniz renk degistirmek renk korlugunde ayirt edilemez",
            Regex("""Icon\(\s*Icons\.Filled\.CheckCircle,""").containsMatchIn(card),
        )
        assertTrue("iki durum farkli ikon kullanmali", card.contains("Icons.Filled.Download"))
    }

    @Test
    fun `dondurulmus telefon kontrolu sekmesi tek anahtarla yonetilir`() {
        val text = source("ui/app/NovaAppShell.kt")
        assertTrue(
            "Play politikasi AccessibilityService ile ozerk eylemi yasakliyor; " +
                "sekme tek satirdan yonetilmeli",
            text.contains("PHONE_TASKS_TAB_ENABLED"),
        )
        val app = source("ui/app/NovaApp.kt")
        assertTrue(
            "sekme kapaliyken TASKS durumunda kalan kullanici erisilemeyen ekranda kilitlenir",
            app.contains("PHONE_TASKS_TAB_ENABLED && vm.mode == Mode.TASKS"),
        )
    }

    // ---------- kesif / eslesme ----------

    @Test
    fun `mDNS geri cagrilari geri alinir`() {
        val text = source("net/NsdGatewayDiscovery.kt")
        assertTrue(
            "API 34+ registerServiceInfoCallback geri alinmazsa kayitlar surec " +
                "olene kadar birikir ve kapanmis akisa veri gondermeye devam eder",
            text.contains("unregisterServiceInfoCallback"),
        )
    }

    @Test
    fun `kaybolan servis adiyla eslestirilir`() {
        val text = source("net/NsdGatewayDiscovery.kt")
        assertTrue(
            "onServiceLost yalniz servis adini bilir; displayName TXT'ten gelir " +
                "ve ayni olmak zorunda degil - kapanan PC listede kaliyordu",
            text.contains("byServiceName"),
        )
    }

    @Test
    fun `API 34 oncesi cozumleme gercekten siraya alinir`() {
        val text = source("net/NsdGatewayDiscovery.kt")
        assertTrue(
            "resolveService ASENKRON; beklenmezse ikinci PC FAILURE_ALREADY_ACTIVE " +
                "alip sessizce dusuyordu",
            text.contains("CountDownLatch"),
        )
    }

    @Test
    fun `yeniden tara olu degil`() {
        val controller = source("feature/pairing/PairingController.kt")
        assertTrue(controller.contains("fun rescan"))
        val app = source("ui/app/NovaApp.kt")
        assertTrue(
            "dugme startDiscovery'ye bagliydi, o da is aktifse ilk satirda " +
                "donuyordu; mDNS isi HIC bitmedigi icin dugme hicbir sey yapmiyordu",
            app.contains("vm.pairing::rescan"),
        )
    }

    // ---------- veri / sizinti ----------

    @Test
    fun `gizli deger panoya hassas isaretlenerek kopyalanir`() {
        val text = source("ui/components/NovaSecretField.kt")
        assertTrue(
            "Android 13+ pano onizlemesi anahtari duz metin gosteriyordu; " +
                "maskelemenin tum anlami kaciyordu",
            text.contains("rememberClipboardCopy(sensitive = true)"),
        )
        val chat = source("feature/chat/ChatScreen.kt")
        assertFalse(
            "sohbet metni gizli degil; hassas isaret sadece anahtar alanina ait",
            chat.contains("rememberClipboardCopy(sensitive = true)"),
        )
    }

    @Test
    fun `dogrulanmamis model cikmaz yol degil`() {
        val text = source("llm/LocalLlmController.kt")
        assertTrue(
            "yonlendirme dogrulanmamis dosyayi 'kurulu' sayip buraya getiriyor; " +
                "uretim yolu reddedince cevrimdisi modda istem HICBIR yere gitmiyordu",
            text.contains("store.verify(spec)"),
        )
        assertFalse(
            "kullaniciyi baska ekrandaki 'Dogrula' dugmesine yollamak cozum degil: " +
                "SHA-256 yerel bir islem, cevrimdisiyken de burada yapilabilir",
            text.contains("Modeller sekmesinden \\\"Dogrula\\\""),
        )
    }

    @Test
    fun `dogrulama isareti sessizce yutulmaz`() {
        val text = source("llm/local/LocalModelStore.kt")
        assertTrue(
            "isaret yazimi tipik olarak DISK DOLDUGU icin basarisiz olur - yani tam " +
                "GB'larca model indirilirken; yutulan hata cevrimdisi sohbeti oldururdu",
            text.contains("fun writeMarker(spec: LocalModelSpec): Boolean"),
        )
    }

    @Test
    fun `gecmis aramasi yarisa karsi korumali`() {
        val text = source("NovaViewModel.kt")
        assertTrue(
            "sonuclar BITIS sirasina gore yaziliyordu: eski sorgu yenisini eziyordu",
            text.contains("historyGeneration"),
        )
    }

    @Test
    fun `silme onay ister`() {
        val text = source("feature/history/ChatHistoryPanel.kt")
        assertTrue(
            "silme geri alinamaz ve cop kutusu ikonu paylas ikonunun bitisiginde",
            text.contains("confirmingDelete"),
        )
    }

    @Test
    fun `model katalogu cagrisi da iptal edilir`() {
        val text = source("NovaViewModel.kt")
        val cleared = text.substringAfter("override fun onCleared()")
        assertTrue(
            "iptal edilmeyen cagri ViewModel'i yikimindan sonra canli tutuyordu",
            cleared.contains("modelsCall?.cancel()"),
        )
    }

    @Test
    fun `bilinmeyen pil ham eksi bir olarak verilmez`() {
        val text = source("llm/local/tools/HorusToolSet.kt")
        assertTrue(
            "-1 'bilinmiyor' demek, bir yuzde degil; model 'pilin %-1' diyordu",
            text.contains("\"bilinmiyor\""),
        )
    }

    @Test
    fun `derin ozyineleme sinirli`() {
        val text = source("llm/local/tools/Calculator.kt")
        assertTrue(
            "StackOverflowError bir Error'dur, catch(CalcException) yakalamaz - " +
                "arac hatasi uygulamayi dusuruyordu",
            text.contains("MAX_DEPTH"),
        )
    }

    @Test
    fun `kesif ile istemci ayni yolda anlasir`() {
        val discovery = source("net/GatewayDiscovery.kt")
        val client = source("net/GatewayConnectionClient.kt")
        assertTrue(
            "canonicalBaseUrl /v1 disini reddediyor; kesif kabul ederse " +
                "kullanilamayacak bir PC listeye dusuyor ve eslesme 'basarili' diyor",
            client.contains("""segments != listOf("v1")"""),
        )
        assertTrue(
            "kesif de ayni kurali uygulamali",
            discovery.contains("""path.trim('/') != "v1""""),
        )
    }

    // ---------- Play B6: yapay zeka icerik bildirimi ----------

    @Test
    fun `uretilen icerigin yaninda bildirim eylemi var`() {
        val text = source("feature/chat/ChatScreen.kt")
        assertTrue(
            "Play politikasi: kullanici sakincali AI ciktisini UYGULAMADAN CIKMADAN " +
                "bildirebilmeli; eylem uretilen icerigin yaninda olmali",
            text.contains("\"Bu yanıtı bildir\""),
        )
        assertTrue(text.contains("ReportContentDialog"))
    }

    @Test
    fun `bildirim akisi cihazda kapanir`() {
        val vm = source("NovaViewModel.kt")
        val fn = vm.substringAfter("fun reportContent(").substringBefore("fun refreshContentReports")
        assertTrue("kayit cihaza yazilmali", fn.contains("reportStore.add"))
        assertFalse(
            "bildirim paylasim ekranina atilarak tamamlanamaz - politika " +
                "uygulamadan cikmadan bildirmeyi sart kosuyor",
            fn.contains("Intent.ACTION_SEND"),
        )
    }

    @Test
    fun `bildirim sessizce yuklenmez`() {
        val store = source("data/ContentReportStore.kt")
        assertFalse(
            "bildirim sohbet alintisi tasiyor; sessiz yukleme 'istemler telefondan " +
                "cikmaz' sozunu ve Data Safety beyanini cignerdi",
            store.contains("OkHttp") || store.contains("HttpUrl") || store.contains("Request("),
        )
    }

    // ---------- pano: tek yazma ----------

    @Test
    fun `gizli deger panoya TEK cagriyla yazilir`() {
        val field = source("ui/components/NovaSecretField.kt")
        assertFalse(
            "once isaretsiz yazip uzerine isaretlisini yazmak sizintiyi KAPATMAZ: " +
                "ilk yazma Android 13+ onizlemesini anahtar duz metinken tetikler",
            field.contains("setText(") || field.contains("markClipboardSensitive"),
        )
        assertTrue("kopyalama tek bir cagriya inmeli", field.contains("onCopy(value)"))

        val helper = source("ui/components/SensitiveClipboard.kt")
        assertEquals(
            "yardimcida tek setPrimaryClip cagrisi olmali",
            1,
            Regex("""setPrimaryClip\(""").findAll(helper).count(),
        )
        assertTrue(helper.contains("EXTRA_IS_SENSITIVE"))
    }

    @Test
    fun `kullanimdan kalkan Compose panosu kalmadi`() {
        val root = listOf(File("src/main/java/com/nova/agent"), File("app/src/main/java/com/nova/agent"))
            .first { it.exists() }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { stripComments(it.readText()).contains("LocalClipboardManager") }
            .map { it.name }
            .toList()
        assertTrue(
            "androidx.compose.ui.platform.ClipboardManager kullanimdan kaldirildi - ihlal: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `uretim kodunda log cagrisi yok`() {
        val root = listOf(File("src/main/java/com/nova/agent"), File("app/src/main/java/com/nova/agent"))
            .first { it.exists() }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                text.contains("android.util.Log") ||
                    Regex("""\bprintStackTrace\(""").containsMatchIn(text) ||
                    Regex("""(?<!\.)\bprintln\(""").containsMatchIn(text)
            }
            .map { it.name }
            .toList()
        assertTrue(
            "sohbet icerigi ve belirtecler asla loglanmamali - ihlal: $offenders",
            offenders.isEmpty(),
        )
    }
}
