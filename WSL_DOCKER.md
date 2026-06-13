# WSL'de Docker Stack'i Kaldırma (Runbook)

> Hedef: `docker-compose.yml` + `docker-compose.faz2.yml` stack'ini (Postgres, Redis,
> MinIO, Prometheus, Grafana, gateway) WSL Ubuntu içinde çalıştırmak.
> Çekirdek akış (migrate → auth → admin → kota → history) 10 Haz 2026'da sandbox'ta
> bare-metal olarak zaten doğrulandı (bkz. PROGRESS.md); bu runbook aynı şeyi
> Docker'lı tam stack ile tekrarlar.

## 0. WSL Ubuntu hazır mı?

PowerShell'de:

```powershell
wsl --list --verbose
```

Distro görünmüyorsa (geçmişte `ERROR_ALREADY_EXISTS` sorunu yaşandı, farklı adla kur):

```powershell
wsl --install -d Ubuntu --name NovaUbuntu --web-download
wsl -d NovaUbuntu
```

## 1. WSL içine Docker Engine kur (Docker Desktop'sız, native)

WSL Ubuntu terminalinde:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker
sudo service docker start
docker run --rm hello-world   # doğrulama
```

> Alternatif: Docker Desktop kurup Settings → Resources → WSL Integration'da
> NovaUbuntu'yu aç. İkisinden biri yeterli.

## 2. Projeye eriş ve .env hazırla

```bash
cd /mnt/c/Users/<WindowsUser>/Nova_Agent_AI
cp gateway/.env.example gateway/.env
```

`gateway/.env` içinde en az şunları doldur:

- `GATEWAY_TOKEN` → `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`
- `ALLOW_ORIGINS` → UI origin'lerin (compose içinde Caddy varsa onun adresi)
- En az bir provider key (`ANTHROPIC_API_KEY` / `GEMINI_API_KEY` / `OPENAI_API_KEY`) **veya** erişilebilir bir Ollama (`OLLAMA_URL`)
- Multi-user: `DATABASE_URL` / `REDIS_URL` compose'da otomatik enjekte edilir, elleme
- Admin için: `ADMIN_USER_IDS=<bootstrap'tan gelen user id>` (adım 5'te)

> Not: `/mnt/c` altında Docker build yavaş olabilir. Yavaşsa projeyi WSL ext4'üne
> kopyala: `cp -r /mnt/c/Users/<WindowsUser>/Nova_Agent_AI ~/nova && cd ~/nova`

## 3. Konfigürasyonu doğrula + stack'i kaldır

```bash
docker compose -f docker-compose.yml -f docker-compose.faz2.yml config -q && echo OK
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.faz2.yml ps
```

## 4. Migrate loglarını kontrol et

```bash
docker compose logs migrate
# beklenen: "applied 001_init.sql" + "applied 002_billing.sql" (tekrar çalışırsa "skip")
```

## 5. İlk kullanıcı + API key (bootstrap)

```bash
docker compose exec gateway node scripts/bootstrap-user.mjs demo@example.local 5
# Çıktıdaki "user id"yi gateway/.env'de ADMIN_USER_IDS'e yaz, API key'i kaydet (bir kez gösterilir)
docker compose restart gateway
```

## 6. Smoke testleri

```bash
KEY=nv_...   # bootstrap'tan gelen key
curl -s localhost:8088/health                                              # 200
curl -s -o /dev/null -w "%{http_code}\n" localhost:8088/v1/models          # 401
curl -s -H "Authorization: Bearer $KEY" localhost:8088/v1/models           # 200
curl -s localhost:8088/metrics | head                                      # Prometheus
curl -s -X POST -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"model":"auto","messages":[{"role":"user","content":"merhaba"}]}' \
  localhost:8088/v1/chat/completions                                       # gerçek cevap (provider key varsa)
```

Ek kontroller: Grafana `localhost:3001` (compose'daki porta göre), Prometheus
`localhost:9090`, MinIO console, `POST /v1/media` (multi-user token ile).

## 7. Sorun giderme

- `permission denied /var/run/docker.sock` → adım 1'deki `usermod -aG docker` + yeni shell.
- WSL'de Docker servis başlamadıysa → `sudo service docker start` (systemd yoksa).
- Gateway production guard'a takılırsa → `GATEWAY_TOKEN` boş veya `ALLOW_ORIGINS=*` demektir; ikisini de düzelt.
- Ollama'ya Windows host'tan erişim → WSL içinden `OLLAMA_URL=http://$(hostname).local:11434` veya Windows IP'si; Ollama'yı `OLLAMA_HOST=0.0.0.0 ollama serve` ile başlat.
