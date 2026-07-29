# NOVA ortak tasarım sistemi

Web arayüzü ile Android uygulaması **tek bir tasarım kaynağından** beslenir.
Renk, tema, ölçü, hareket, ikon ve gezinme tanımları burada durur; iki platform
da bu dosyadan üretilen kodu okur. Amaç kopmayı yapısal olarak imkânsız kılmak:
bir tarafta rengi değiştirip diğerini unutmak mümkün değil.

## Tek kaynak

`design/nova-tokens.json` — **elle düzenlenecek tek dosya budur.**

### Pulse Aperture kaynak akışı

Pulse Aperture için tek kaynak akışı şu sırayı izler:

1. `design/nova-tokens.json`, tema sırasını ve her temanın
   `accents[].sourceAsset` yolunu tanımlar.
2. `design/brand/pulse-aperture/` altındaki 11 onaylı SVG, marka işaretinin gerçek
   görsel kaynaklarıdır: amethyst, arctic, sapphire, emerald, lime, amber,
   copper, coral, ruby, lavender ve moonstone.
3. `docs/design/Nova İkon.dc.html`, bu dosyaları yalnızca göreli `img` yollarıyla
   yeniden kullanır; yeni, kopya veya inline SVG üretmez. Brand / thinking /
   preview durumları, 96 / 72 / 48 / 36 px kontrolleri, Ruby siyah yüzeyi ve
   azaltılmış hareket örneği burada gözle denetlenir.
4. Web ve Android uygulama kodu semantik renk, ölçü ve hareket değerlerini
   `npm run tokens` ile üretilen platform dosyalarından alır.

Bu nedenle tasarım kütüphanesi yeni bir kaynak değildir: token sözleşmesini ve
mevcut marka varlıklarını görünür kılan bir tüketicidir. Tema eklerken önce token
kaydını ve onaylı kaynak dosyasını birlikte ekle; ardından üretimi ve tasarım
kütüphanesi sözleşme testini çalıştır.

## Üretim

```bash
npm run tokens
```

Şunları yeniden yazar:

| Üretilen dosya | Kullanan |
|---|---|
| `web/src/design/tokens.generated.mjs` | Web — CSS değişkenleri, aksan listesi, gezinme, ikon adları, hareket sabitleri |
| `nova-android/app/src/main/java/com/nova/agent/ui/theme/NovaTokens.kt` | Android — `Color`, `NovaAccent`, `NovaRadius`, `NovaSpace`, `NovaDuration`, `NovaOrb` |

Üretilen dosyalar depoya işlenir; derleme betiğe bağımlı değildir. Başlarına
"OTOMATİK ÜRETİLDİ" notu konur — elle düzenlenirse ilk `npm run tokens` çağrısı
değişikliği siler.

## Ortaklaşan şeyler

**Renkler.** `color` bloğu iki tarafta aynı adlarla çıkar (`--bg` ↔ `Bg`,
`--surface1` ↔ `Surface1`). Tek istisna `textMain`: Compose'da `Text`
composable'ıyla çakışmasın diye Kotlin adı `TextMain`, CSS adı `--text`.

**Temalar.** `accents` dizisi hem web'deki Ayarlar → Görünüm hem de Android'deki
Görünüm ayarında aynı sırayla, aynı adla, aynı hex değerleriyle görünür. Web
varsayılanı `webDefault`, Android varsayılanı `androidDefault` ile işaretlenir —
ikisi farklı olabilir ama seçenek kümesi ortaktır.

**Hareket.** `motion.duration` ve `motion.easing` geçiş sürelerini hizalar.
`motion.orb` ses orbunun geometrisini tanımlar; web canvas'ı
(`web/src/hooks/useOrb.js`) ve Compose `Orb.kt` aynı sayıları kullanır, bu yüzden
orb iki platformda birebir aynı hareket eder.

**Marka işareti.** `brand.aperture` — NOVA Pulse Aperture'ın geometrisi, iki yol
verisi ve hareket zamanlaması. Web `ui/NovaMark.jsx` ile Android
`ui/brand/NovaThinkingIndicator.kt` aynı sayıları okur: nefes 2750 ms, çekirdek
nabzı 1375 ms, 14 noktalı 4200 ms yörünge, 5500 ms ışık süpürmesi, ±0.7° eğim.
Renkler burada değil — her aksanın `aperture` seti (light / mid / deep / glow /
core) ikonu boyar, böylece işaret her temada tema rengini alır. Sekme ve PWA
ikonu (`web/public/icon.svg`) da aynı yollardan üretilir. Uygulamadaki tek
marka görseli budur; jenerik "parıltı" ikonu hiçbir yerde kullanılmaz.

**İkonlar.** `icons` bölümü semantik anahtar → (lucide adı, Material adı)
eşlemesidir. Yeni ikon eklerken iki sütunu birlikte doldur. Web tarafında
`web/src/lib/icons.mjs` geliştirme modunda bu sözlükle kendini karşılaştırır ve
uyuşmazlıkta konsola uyarı basar.

**Gezinme.** `navigation.destinations` — Kontrol / İşler / Sohbet / Modeller.
Android `NavigationBar`, web'in sol rayı ve dar ekrandaki alt çubuğu aynı sıradan
ve aynı etiketlerden üretilir. Ses, iki tarafta da Sohbet üst çubuğundaki
mikrofonla açılır.

**Durum renkleri.** `status.connection` ve `status.task` bağlantı ve görev
durumlarının renk tonunu sabitler. Görevlerin kullanıcıya gösterilen Türkçe
etiketleri ayrıca `web/src/lib/tasks.mjs` ile Android
`feature/tasks/MobileTaskModels.kt` arasında birebir tutulur.

## Yeni bir token eklerken

1. `design/nova-tokens.json` içine ekle.
2. `npm run tokens` çalıştır.
3. İki taraftaki kullanım yerini de güncelle (CSS değişkeni / Compose sabiti).

Hex değerini doğrudan `.jsx`, `.mjs` ya da `.kt` dosyasına yazma — kopma tam
oradan başlar.
