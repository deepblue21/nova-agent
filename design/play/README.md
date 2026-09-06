# Play Console görselleri

`icon-512.svg`, `feature-1024x500.svg` ve `feature-1024x500-en.svg`
**otomatik üretiliyor** — kaynak `design/nova-tokens.json`, üretici
`scripts/sync-design-tokens.mjs`. Elle düzenleme; `npm run tokens` üzerine yazar.

Bu dosyaların elde çizilmemesinin nedeni: ikisi de marka işaretinin ta kendisi.
Elde çizilirse token'lar değiştiğinde sessizce eskirler ve mağazadaki NOVA,
uygulamadaki NOVA'ya benzemez olur.

## PNG'ler nasıl üretildi

Play PNG/JPEG istiyor, SVG kabul etmiyor. Depoda rasterleştirici bağımlılığı
yok, o yüzden PNG'ler işlenmiş durumda. Yeniden üretmek gerekirse:

```bash
pip install cairosvg --break-system-packages
python3 - <<'PY'
import cairosvg
from PIL import Image
jobs = [("icon-512", 512, 512),
        ("feature-1024x500", 1024, 500),
        ("feature-1024x500-en", 1024, 500)]
for name, w, h in jobs:
    cairosvg.svg2png(url=f"design/play/{name}.svg",
                     write_to=f"design/play/{name}.png",
                     output_width=w, output_height=h)
    # Play alfa kanalı kabul etmiyor: opak marka zeminine düzleştir.
    im = Image.open(f"design/play/{name}.png").convert("RGBA")
    flat = Image.new("RGB", im.size, (6, 7, 17))   # #060711
    flat.paste(im, mask=im.split()[3])
    flat.save(f"design/play/{name}.png", "PNG", optimize=True)
PY
```

**Yazı tipleri.** Öne çıkan görsel marka yazı tiplerini kullanıyor: başlık
**Syne 800**, alt satırlar **Sora 600/400** (ikisi de `typography` token'ında
tanımlı, Google Fonts, OFL). Sistemde kurulu değilse rasterleştirici
`DejaVu Sans`a düşer — okunur ama marka tipografisi değildir. Varyasyonlu
`.ttf`den statik örnek çıkarmak gerekiyor; fontconfig varyasyon eksenini
rasterleştiriciye geçirmiyor ve `font-weight: 800` sessizce 400 kalıyor:

```bash
python3 -c "
from fontTools.varLib import instancer
from fontTools.ttLib import TTFont
for src, wght, fam in [('Syne[wght].ttf',800,'Syne'),('Sora[wght].ttf',600,'Sora'),('Sora[wght].ttf',400,'Sora')]:
    f = TTFont(src); instancer.instantiateVariableFont(f, {'wght': wght}, inplace=True, updateFontNames=True)
    f.save(f'{fam}-{wght}.ttf')" && fc-cache -f
```

## Ölçülen değerler (elle değiştirilecekse)

| | Değer |
|---|---|
| Simge — işaret ölçeği | 1.0 (kareyi %56 dolduruyor) |
| Öne çıkan — işaretin görünen genişliği | 148–332 px |
| Öne çıkan — metin başlangıcı | x = 396 |
| Öne çıkan — sol pay | 147 px |
| Öne çıkan — sağ pay | TR 134 px · EN 106 px |

Metin ya da yazı tipi değişirse **punto yeniden ölçülmeli**: ilk denemede
28 punto İngilizce satırı sağ kenardan taşırıyordu.
