# Play mağaza metinleri

Play Console → *Ana mağaza girişi*. Türkçe birincil, İngilizce ikinci dil.
Karakter sınırları Play'in kendi sınırları; sayımlar yapıldı.

> **Uyulan kurallar:** abartılı iddia yok ("en iyi", "#1"), anahtar kelime
> doldurma yok, yapay zekâ kullanımı açıkça yazıldı, sunulmayan özellik
> vaat edilmedi. Play, açıklamayla uygulamanın uyuşmamasını ret sebebi sayıyor.

---

## Uygulama adı (en fazla 30)

```
NOVA — Cihazda AI Asistan
```
25 karakter.

### Marka kontrolü — yapıldı, karar verildi (2026-09-09)

**Karar: NOVA adıyla devam, risk bilinerek kabul edildi.** Kararı Salih verdi;
gerekçesi ve bulgular aşağıda, sonradan "bilinmiyordu" denmesin diye.

Bulgular:

- **Play'de aynı kategoride aynı ad kullanımda.** AI Chatbot - Nova
  (`com.scaleup.chatai`, MWM), Nova AI: Chatbot Assistant (`com.nova.ai.app`),
  Nova: AI Chatbot (`com.apporigins.NovaAI`), Nova: AI Planner, Nova AI Friend.
  Hepsi yapay zekâ sohbet asistanı.
- **"NOVA AI" markası bir Türk şirketi tarafından talep edilmiş.** USPTO
  97845770 — HUBX Yazılım Hizmetleri A.Ş., sınıf 9 (mobil uygulama yazılımı) ve
  42 (SaaS), 18.03.2023, o kayıtta "beklemede".
- **Play IP politikası:** *"identical or similar trademark in a way that is
  likely to cause confusion as to the source"* → askıya alma sebebi.
- Alternatif olarak bakılan **Horus** da temiz değil (Play'de `HorusAI`
  geliştiricisi, horusai.net, pulsrhorus.com).

Kapsanmayanlar — **karar bunlara bakılmadan verildi**: TÜRKPATENT araması
yapılmadı (buradan erişilemiyor) ve bu bir hukuki görüş değil.

**Kabul edilen iki sonuç:**

1. Şikâyet gelirse süreç kaldırma + itiraz şeklinde işler.
2. Görünürlük: "Nova AI" araması doygun; organik keşif zayıf olacak, mağaza
   metnindeki *cihazda/çevrimdışı* farkı öne çıkarılarak telafi edilmeye
   çalışılıyor.

**Neden bu kararın zamanı geçti sayılmaz ama geri dönüşü pahalı:**
`applicationId = "com.nova.agent"` Play'e ilk yüklemeden sonra **asla
değiştirilemez**. Sonradan marka değişirse paket adı "nova" olarak kalır.

---

## Kısa açıklama (en fazla 80)

```
İnternetsiz çalışan yapay zekâ sohbeti. İstemlerin telefonundan çıkmaz.
```
70 karakter.

---

## Tam açıklama (en fazla 4000)

```
NOVA, telefonunda çalışan bir yapay zekâ sohbet uygulamasıdır.

Cihazına bir dil modeli indirirsin ve sohbet edersin. İnternet bağlantısı
olmadan da yanıt alırsın; yazdıkların telefonundan çıkmaz.

NELER YAPAR

• Cihazda sohbet — indirdiğin modelle, çevrimdışı.
• Sesle konuşma — mikrofona basıp konuşursun, yanıtı sesli dinlersin.
• Sohbet geçmişi — konuşmaların telefonunda saklanır, arayabilirsin.
• Çevrimdışı araçlar — hesap makinesi, not defteri, cihaz durumu.
• Kendi bilgisayarına bağlanma (isteğe bağlı) — evinde bir Gateway
  çalıştırıyorsan telefonunu ona eşleyip daha büyük modelleri kullanırsın.

GİZLİLİK

Bu uygulamanın geliştiriciye ait bir sunucusu yoktur. Analitik, reklam ve
çökme raporlama bileşeni içermez.

Varsayılan ayarda istemlerin telefonunda işlenir. Yalnız sen seçersen kendi
kurduğun Gateway'e gönderilir — o da senin bilgisayarında çalışır. Kart
numarası, IBAN, kimlik veya parola gibi hassas görünen içerikte otomatik
aktarım yapılmaz; her seferinde onayın istenir.

Verilerinin tamamını Ayarlar'dan tek dokunuşla silebilirsin.

NELERİ BİLMEN GEREKİYOR

• Yanıtlar yapay zekâ tarafından üretilir ve YANLIŞ OLABİLİR. Tıbbi, hukuki
  ve finansal konularda uzmanına danış.
• Cihazda çalışan modeller yer kaplar. Önerilen başlangıç modeli yaklaşık
  0,5 GB'tır; daha büyük modeller daha çok RAM ister. Uygulama cihazına
  uygun olanı önerir.
• Sakıncalı bir yanıt görürsen yanıtın altındaki bayrak simgesiyle
  bildirebilirsin.
• Gerektirdiği izinler: internet (model indirme), mikrofon (yalnız sesle
  konuşurken), bildirim (indirme ilerlemesi). Konum, rehber, SMS ve dosya
  erişimi istenmez.

Gizlilik politikası: https://deepblue21.github.io/nova-agent/privacy/
```

Yaklaşık 1.520 karakter — sınırın çok altında, okunabilir.

---

## English (en-US)

**App name (≤30):** `NOVA — On-Device AI Assistant` — 28 characters.

**Short description (≤80):**
```
AI chat that runs on your phone. Your prompts never leave the device.
```
68 characters.

**Full description:**
```
NOVA is an AI chat app that runs on your phone.

You download a language model to your device and chat with it. You get answers
without an internet connection, and what you type never leaves your phone.

WHAT IT DOES

• On-device chat — with the model you downloaded, offline.
• Voice — press the microphone, speak, and hear the answer read back.
• Chat history — conversations are kept on your phone and are searchable.
• Offline tools — calculator, notepad, device status.
• Connect to your own computer (optional) — if you run a Gateway at home, you
  can pair your phone with it and use larger models.

PRIVACY

This app has no developer-operated server. It contains no analytics, no
advertising and no crash-reporting components.

By default your prompts are processed on your phone. They are sent to a Gateway
only if you choose that — and that Gateway runs on your own computer. Content
that looks sensitive (card numbers, IBAN, ID numbers, passwords) is never
handed off automatically; your approval is requested every time.

You can delete all of your data from Settings with one tap.

WHAT YOU SHOULD KNOW

• Responses are generated by AI and MAY BE WRONG. Consult a qualified
  professional for medical, legal and financial matters.
• On-device models take up storage. The recommended starter model is about
  0.5 GB; larger models need more RAM. The app suggests one that fits your
  device.
• If you see an objectionable response, report it with the flag icon beneath it.
• Permissions used: internet (model downloads), microphone (only while using
  voice), notifications (download progress). No location, contacts, SMS or file
  access is requested.

Privacy policy: https://deepblue21.github.io/nova-agent/privacy/
```

---

## Görseller

| Varlık | Gereklilik | Durum |
|---|---|---|
| Uygulama simgesi | 512×512 PNG, 32-bit, alfa yok | ✔ `design/play/icon-512.png` |
| Öne çıkan görsel (TR) | 1024×500 PNG/JPG, alfa yok | ✔ `design/play/feature-1024x500.png` |
| Öne çıkan görsel (EN) | aynı | ✔ `design/play/feature-1024x500-en.png` |
| Telefon ekran görüntüsü | en az 2, en fazla 8; 16:9–9:16, kenar 320–3840 px | ✖ emülatörden alınabilir |

Simge ve öne çıkan görsel `design/nova-tokens.json`'dan **otomatik üretiliyor**
(`npm run tokens`); PNG'ye çevirme adımı `design/play/README.md`'de. Elde
düzenleme — üretici üzerine yazar.

Simgeye köşe yuvarlatma **bilerek** konmadı: Play yuvarlak köşeyi ve gölgeyi
kendisi ekliyor, SVG'de de olsa çift yuvarlatılmış kirli bir kenar çıkardı.

**Ekran görüntüsü için öneri (4 kare):** Sohbet (yanıt görünür durumda) ·
Modeller (indirme kartı) · Kontrol (yürütme politikası) · Ayarlar (gizlilik/veri).

> Emülatörün araç çubuğundaki fotoğraf makinesi simgesi tam çözünürlüklü PNG
> kaydeder; ekran görüntüsü almanın doğru yolu odur, masaüstü ekran görüntüsü
> değil.
