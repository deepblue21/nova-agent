# Play Console → Data Safety formu — doldurulmuş cevaplar

Formu Play Console'da **senin** doldurman gerekiyor (hesabına ben giremem).
Aşağıdaki cevaplar koddan doğrulanmıştır; ekrandaki soruların sırasıyla dizildi.

> **Anahtar ayrım:** Play'in "collected" (toplanan) tanımı, verinin **cihazdan
> çıkıp geliştiricinin sunucusuna gitmesidir**. Yalnız cihazda kalan veri
> toplanmış SAYILMAZ. NOVA'nın geliştirici sunucusu yok; bu yüzden neredeyse
> her şeye "Hayır" diyoruz — ve bu doğru, kaçamak değil.

---

## 1. Veri toplama ve paylaşma

**"Uygulamanız gerekli kullanıcı verilerinin herhangi birini topluyor mu ya da
paylaşıyor mu?"**

→ **Hayır**

**Gerekçe (denetimden):**
- Uygulamada analitik, reklam, çökme raporlama SDK'sı **yok** (Firebase,
  Crashlytics, AppsFlyer, Adjust, Facebook, AdMob, Sentry — hiçbiri yok;
  bağımlılık listesi temiz).
- Üretim kodunda tek bir `Log.*` / `println` / `printStackTrace` çağrısı yok;
  sohbet içeriği hiçbir yere yazılmıyor (bu bir birim testiyle kilitli).
- Sabit kodlu tek dış adres **huggingface.co** (model indirme). Diğer tüm
  bağlantılar **kullanıcının kendi girdiği** Gateway adresine gider.

**Gateway ne olacak?** Kullanıcının kendi bilgisayarındaki sunucu, geliştiricinin
değil. Play bunu geliştiriciye veri aktarımı saymaz. Yine de gizlilik
politikasında açıkça anlatılıyor.

---

## 2. Güvenlik uygulamaları

| Soru | Cevap | Not |
|---|---|---|
| Veriler aktarımda şifreleniyor mu? | **Evet** | Gateway `https` ve `http` destekler; şifresiz `http` **yalnız yerel ağ adreslerine** izinlidir (`NetworkPolicy`, 8 güvenlik testiyle kilitli). Hugging Face indirmeleri her zaman `https`. |
| Kullanıcı verisinin silinmesini isteyebilir mi? | **Evet** | Ayarlar → Veri yönetimi → "Yerel veriyi temizle". Ayrıca uygulamayı kaldırmak her şeyi siler. Silme talebi için bir sunucuya yazmaya gerek yok, çünkü sunucu yok. |
| Uygulama Play Families politikasına tabi mi? | **Hayır** | 13 yaş altına yönelik değil. |
| Bağımsız güvenlik denetimi | **Hayır** | Dürüst cevap: bağımsız denetim yaptırılmadı. |

---

## 3. Uygulama içeriği → Yapay zekâ beyanı

**"Uygulamanız üretken yapay zekâ özellikleri içeriyor mu?"** → **Evet**

- Tür: **metin → metin** (sohbet).
- Çıktı kullanıcıya gösterilir, üçüncü kişilere yayımlanmaz.
- **Uygulama içi bildirim mekanizması var** (politikanın şartı): her yapay zekâ
  yanıtının altında bayrak simgesi → sebep seçimi → cihazda kaydedilir. Akış
  **uygulamadan çıkmadan** tamamlanır. Kullanıcı isterse Ayarlar'dan gönderir.

---

## 4. İzin beyanları

| İzin | Play'de sorulur mu | Cevap |
|---|---|---|
| `RECORD_AUDIO` | Evet | Yalnız kullanıcı mikrofona bastığında; ses kaydı saklanmaz, yalnız çözülen metin sohbete girer. Arka planda dinleme yok. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Evet | Model dosyası indirme. Ekran kapansa da indirmenin sürmesi için. Android 15+ için 6 saat / 24 saat sınırı biliniyor ve manifestte belgelendi. |
| `POST_NOTIFICATIONS` | Hayır | İndirme ilerlemesi. |

**Hassas izin talebi yok:** konum, rehber, SMS, çağrı kaydı, tüm dosyalara
erişim, AccessibilityService — hiçbiri istenmiyor.

> **Önemli:** telefon kontrolü (AccessibilityService) özelliği bu sürümde
> **kapalı** ve gezinmede görünmüyor (`PHONE_TASKS_TAB_ENABLED = false`).
> Play'in 28 Ocak 2026 kuralı AccessibilityService ile özerk eylemi yasaklıyor.
> Bu sekme açılırsa beyan da değişmek zorunda.

---

## 5. Formu doldururken dikkat

1. **"Toplamıyoruz" demek kolay ama savunulabilir olmalı.** Yukarıdaki gerekçeler
   incelemede sorulursa gösterilebilir; hepsi testle kilitli.
2. **Gizlilik politikası URL'i zorunlu.** `docs/play/GIZLILIK-POLITIKASI.md`
   hazır; yayımlanacak yer için `YAYIN-KONTROL-LISTESI.md`'ye bak.
3. **Beyan ile uygulama uyuşmalı.** İleride analitik eklersen bu form da
   değişmeli; uyuşmazlık askıya alma sebebidir.
