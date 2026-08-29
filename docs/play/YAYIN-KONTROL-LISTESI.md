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

Commit'ler hazır ve yerelde bekliyor; göndermek için Windows'ta çalıştır
(kimlik doğrulaman orada):

```powershell
cd C:\Users\salih\Project_Horus
git push nova-upstream codex/phase1-local-first
```

Sonra gizlilik sayfasını yayına almak için — Pages yalnız ana daldan dağıtır,
o yüzden ilgili commit `main`'e geçmeli:

```powershell
git checkout main
git pull nova-upstream main
git cherry-pick 1b1dd0c      # yalnız Play hazırlığı: site/ + pages.yml + docs/play
git push nova-upstream main
git checkout codex/phase1-local-first
```

*(Yalnız yeni dosya eklediği için çakışma beklenmiyor. Tüm dalı birleştirmek
istersen `git merge codex/phase1-local-first` de olur.)*

Son adım, tek seferlik: GitHub → repo → **Settings → Pages → Source: GitHub
Actions**. Ardından Actions sekmesinden iş akışının yeşil olduğunu gör.

> **Temizlik notu:** bu oturumda git'i Linux tarafından çalıştırdığım için
> `.git/` içinde silinemeyen geçici dosyalar kaldı (~210 `tmp_obj_*` ve birkaç
> `*.lock.stale`). Zararsızlar, ama Windows'ta `git gc --prune=now` ile
> temizlenirler.

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
