# Project Horus — port haritası

Tarih: 2026-08-24 · İlke: **Horus paylaşılan portlara dokunmaz.** Kendi 18xxx bloğunda yaşar.

## Neden bu belge var

`start-horus.ps1` eskiden şöyle çalışıyordu: 80, 443 ve 8088'i dener, **o an boşsa alır**,
doluysa alternatife kayar. Bu mantık Horus'u korur ama komşusunu bozar — portu paylaşan
diğer servis (OpenClaw, Keycloak, başka bir dev sunucusu) **kapalıyken** Horus onun portunu
sessizce sahiplenir, o servis açılmak istediğinde port dolu bulur.

WSL2'de localhost yönlendirmesi olduğu için bu risk teoride kalmıyor: WSL içindeki bir
dinleyici de Windows tarafında aynı portu tutar. OpenClaw WSL'de systemd servisleriyle
koştuğu için tam olarak bu sınıfa giriyor.

Ayrıca eski mantık `.env`'den `GATEWAY_PORT` anahtarını **siliyordu** (8088 seçildiğinde),
yani elle `docker compose up` diyen bir sonraki çalıştırma compose varsayılanına düşüp
yine paylaşılan portu alıyordu.

## Horus'un sahip olduğu portlar (host tarafı)

| Servis | Host portu | Yedek adaylar | Not |
|--------|-----------|---------------|-----|
| Gateway (telefonun bağlandığı yer) | **18088** | 18089, 18090, 18091 | Kap içinde 8088 kalır — izole, çatışma yok |
| Web arayüzü (Caddy HTTP) | **18080** | 18082, 18083 | 80'e hiç dokunulmaz |
| Web arayüzü (Caddy HTTPS) | **18443** | 18444, 18445 | 443'e hiç dokunulmaz |

Seçim her çalıştırmada kök `.env`'e **kalıcı** yazılır (`GATEWAY_PORT`, `CADDY_HTTP_PORT`,
`CADDY_HTTPS_PORT`), böylece düz `docker compose up` da aynı portları kullanır.

**Docker'ın tuttuğu port bizim sayılır.** Aksi halde Horus zaten ayaktayken yeniden
başlatmak portu her seferinde bir sonraki adaya kaydırır ve telefonda kayıtlı adres bozulur.
(Eski `last-run.log`'da tam bu görülüyor: `Port 8088 dolu (kullanan: com.docker.backend)`.)

## Asla dokunulmayan portlar

`80`, `443`, `8088`, `8080` — Horus bunları boş bulsa da almaz.

## Telefon tarafı

Uygulama **hiçbir yerde port tahmin etmiyor** — bu doğrulandı:

- `Pairing.parsePairUri`: `port` parametresi yoksa `null` döner, varsayılana düşmez.
- `GatewayDiscovery.fromService`: portu mDNS kaydından alır.
- Eşleme (mDNS keşfi + 8 karakterlik kod + `horus://pair` bağlantısı) gerçek portu taşır.

Değişen tek şey **elle giriş rehberliği**: yer tutucu ve hata metinlerindeki örnekler
`8088` yerine `18088` gösteriyor. Ayrıca `LiveGatewayOllamaConnectionTest`'in yedek
adresi düzeltildi — eski hâliyle emülatörden yanlış porta bağlanmayı deniyordu.

## Kalan risk: faz2 yığınının loopback portları

`docker-compose.faz2.yml` şu host portlarını yayınlıyor (yalnız 127.0.0.1, ama WSL
yönlendirmesi nedeniyle yine çatışabilir):

| Port | Servis |
|------|--------|
| 8081 | Keycloak |
| 8080 | SearXNG |
| 8000 | Whisper (STT) |
| 8001 | TTS |
| 9000 / 9001 | MinIO (S3 API / konsol) |
| 9090 | Prometheus |
| 3001 | Grafana |

Bunlar **bilinçli olarak değiştirilmedi**: hangisinin OpenClaw ile çakıştığı bilinmiyor ve
`DEPLOY_VPS.md` bazılarına (Keycloak 8081) adıyla atıf yapıyor. Faz2'yi çalıştırmaya
başlarsan bu bloğu da 18xxx'e taşımak tek satırlık bir iş.

## Değiştirmek istersen

Tek yer: `scripts/start-horus.ps1` içindeki `Get-HorusPort` çağrıları. Aday listesini
değiştir, gerisi (güvenlik duvarı kuralı, mDNS yayını, QR eşleme bağlantısı, sağlık
kontrolü) seçilen portu otomatik izler.
