# Play yayını — kontrol listesi

Kod tarafı bitti (**389/389 test**, denetim listesinde açık kusur yok).
Kalanlar hesap/mağaza işleri.

---

## 1. Gizlilik politikası URL'i (B4) — hazır, tek düğmeye bakıyor

Play, uygulama kaydında **zorunlu** olarak herkese açık bir gizlilik politikası
URL'i ister. Metin yazıldı ve yayımlanabilir hâle getirildi.

**Hazır olanlar**

| Dosya | Ne |
|---|---|
| `site/privacy/index.html` | Yayımlanacak sayfa — Türkçe + İngilizce, tek dosya, dış bağımlılık yok, açık/koyu tema, mobil uyumlu |
| `.github/workflows/pages.yml` | Yalnız `site/` klasörünü GitHub Pages'e yayımlayan iş akışı |
| `docs/play/GIZLILIK-POLITIKASI.md` | Aynı metnin Markdown hâli (kaynak/arşiv) |

**Neden `docs/` yayımlanmıyor:** o klasörde uygulamanın bulunan her kusurunu tek
tek anlatan denetim raporu var. Pages'i `docs/`'a bağlamak raporun tamamını
herkese açık hâle getirirdi. Yayımlanan şey açıkça seçilmiş olmalı.

**Senin yapman gerekenler (2 dakika)**

1. `site/` ve `.github/workflows/pages.yml` dosyalarını **`main` dalına** al
   (bu dosyalar şu an `codex/phase1-local-first` üzerinde).
2. GitHub → repo → **Settings → Pages → Source: GitHub Actions**.
   *(Varsayılan `github-pages` ortamı yalnız ana daldan dağıtıma izin verir;
   bu yüzden 1. adım önce gelmeli.)*
3. Actions sekmesinden iş akışının yeşil olduğunu gör.

**Ortaya çıkacak adres**

```
https://deepblue21.github.io/nova-agent/privacy/
```

Play Console → *Uygulama içeriği → Gizlilik politikası* alanına bu adres girilir.

**Doğrulandı:** sayfa gerçek tarayıcıda açıldı — JavaScript hatası yok, iki dil de
doğru açılıyor, açık/koyu tema çalışıyor, 390 px genişlikte yatay taşma yok.
İş akışında `site/privacy/index.html` var mı diye bir kontrol adımı da var:
sessizce boş site yayımlayıp Play'e 404 veren bir URL vermektense orada patlaması
iyidir.

---

## 2. Data Safety formu (B7) — cevaplar hazır

`docs/play/DATA-SAFETY-FORMU.md` — Play Console'daki soruların sırasıyla
doldurulmuş hâli. Ana cevap: **veri toplanmıyor.** Gerekçeler koddan doğrulandı
ve testlerle kilitli (sıfır analitik/reklam/çökme SDK'sı, üretim kodunda tek bir
log çağrısı yok, sabit kodlu tek dış adres `huggingface.co`).

Formu senin doldurman gerekiyor; hesabına giremem.

---

## 3. Kalanlar

| İş | Kim | Not |
|---|---|---|
| Mağaza metinleri (başlık, kısa/uzun açıklama) | yazılabilir | henüz yazılmadı |
| Ekran görüntüleri, uygulama simgesi, öne çıkan görsel | kısmen | emülatörden alınabilir; öne çıkan görsel (1024×500) tasarım işi |
| Play Console hesabı + geliştirici doğrulaması | **sen** | 30 Eylül 2026 sınırı |
| 12 test kullanıcısı / 14 gün kapalı test | **sen** | kişisel hesapta zorunlu |
| Upload keystore + imzalı AAB | **sen** | `scripts/new-upload-keystore.ps1`; parolayı ben ne üretirim ne yazarım |
