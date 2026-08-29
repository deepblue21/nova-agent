# start-horus.ps1 - Project Horus stack'ini tek komutla baslatir ve dogrular.
#
#   .\scripts\start-horus.ps1                 # compose stack'i baslat + saglik kontrolu (yalniz loopback)
#   .\scripts\start-horus.ps1 -ApiKey $key    # + canli smoke (models/chat + mobil kontrol duzlemi)
#   .\scripts\start-horus.ps1 -NoBuild        # imajlari yeniden derlemeden baslat
#   .\scripts\start-horus.ps1 -Lan            # + telefonu ayni Wi-Fi'den baglamak icin ac (alt agla sinirli)
#   .\scripts\start-horus.ps1 -Tailscale      # + tailnet cihazlarindan erisim (100.64.0.0/10 ile sinirli)
#   .\scripts\start-horus.ps1 -Lan -Tailscale # ikisi birden (evde Wi-Fi, disarida Tailscale)
#
# Yonetim API anahtari asla yazdirilmaz; yalniz telefon esleme anahtari bir kez gosterilir.
# -Lan/-Tailscale kalicidir: secim kok .env'e yazilir; bayraksiz calistirmak loopback'e dondurur.

param(
    [string]$ApiKey = "",
    [switch]$NoBuild,
    [switch]$Lan,
    [switch]$Tailscale
)

# Not: "Stop" kullanma - PS 5.1'de docker'in stderr ilerleme ciktisi bile script'i oldurur.
$ErrorActionPreference = "Continue"

# Konsol cikis kodlamasi UTF-8 olmali. Aksi halde alt sureclerin (pair.mjs)
# UTF-8 ciktisi cp857/cp437 olarak okunur: QR kodunun blok karakterleri ve
# Turkce harfler bozuk gorunur (Ôûä, BA─ÿLA gibi), ustelik asagidaki
# "eslesme kodu uretildi mi" kontrolu de yanlis negatif verir.
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
try { Start-Transcript -Path (Join-Path $root "scripts\last-run.log") -Force *> $null } catch { }

function Fail($msg) { Write-Host "HATA: $msg" -ForegroundColor Red; exit 1 }
function Ok($msg)   { Write-Host "  OK  $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "  !   $msg" -ForegroundColor Yellow }

# 1) Docker calisiyor mu?
try { docker info *> $null } catch { Fail "Docker Desktop calismiyor. Once Docker'i baslat." }
if ($LASTEXITCODE -ne 0) { Fail "Docker Desktop calismiyor. Once Docker'i baslat." }
Ok "Docker hazir"

# 2) Port secimi - Horus'a AYRILMIS 18xxx blogu
#
# ESKI DAVRANIS VE NEDEN DEGISTI:
# Onceki surum once 80 / 443 / 8088'i denerdi ve "su anda bos ise al" derdi.
# Bu, portu paylasan diger servisi (OpenClaw, Keycloak 8081, baska bir dev
# sunucusu) O SERVIS KAPALIYKEN sessizce sahiplenmek demekti; sonra o servis
# acilmak istediginde port dolu olurdu. Yani Horus kendini koruyor ama
# komsusunu bozuyordu. WSL2'de localhost yonlendirmesi oldugu icin WSL
# icindeki bir dinleyici de Windows'ta ayni portu tutar - risk gercek.
#
# Artik Horus paylasilan portlara HIC dokunmuyor: yalniz kendi 18xxx
# blogundan secer ve secimi .env'e KALICI yazar (asagida), boylece duz
# "docker compose up" da ayni portu kullanir.
#
# Docker'in kendi tuttugu port BIZIM stack'imizdir (yeniden baslatma):
# onu dolu sayip bir sonraki adaya kaymak, her calistirmada portun
# kaymasina ve telefondaki kayitli adresin bozulmasina yol acardi.
function Get-PortOwner([int]$p) {
    $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if (-not $c) { return $null }
    try { return (Get-Process -Id $c[0].OwningProcess -ErrorAction SilentlyContinue).ProcessName }
    catch { return "bilinmiyor" }
}
function Get-HorusPort([int[]]$candidates, [string]$label) {
    foreach ($p in $candidates) {
        $owner = Get-PortOwner $p
        if (-not $owner) { return $p }
        if ($owner -match "^com\.docker") { return $p }   # kendi stack'imiz
        Warn "$label : $p dolu (kullanan: $owner) - siradaki aday deneniyor"
    }
    Fail "$label icin bos port bulunamadi. Denenenler: $($candidates -join ', ')"
}

$script:gwPort  = Get-HorusPort @(18088, 18089, 18090, 18091) "Gateway"
$script:webPort = Get-HorusPort @(18080, 18082, 18083)        "Web arayuzu (HTTP)"
$script:tlsPort = Get-HorusPort @(18443, 18444, 18445)        "Web arayuzu (HTTPS)"

$env:GATEWAY_PORT     = "$($script:gwPort)"
$env:CADDY_HTTP_PORT  = "$($script:webPort)"
$env:CADDY_HTTPS_PORT = "$($script:tlsPort)"
Ok "Portlar: Gateway $($script:gwPort) | Web $($script:webPort) | HTTPS $($script:tlsPort) (8088/8080/80/443'e dokunulmuyor)"

function Set-DotEnvValue([string]$key, [string]$value) {
    # Kok .env'e kalici yazar: kullanici sonradan elle 'docker compose up' dese
    # bile ayni baglama korunur (compose kok .env'i otomatik okur).
    $envPath = Join-Path $root ".env"
    $lines = @()
    if (Test-Path $envPath) {
        $lines = Get-Content $envPath | Where-Object { $_ -notmatch "^\s*$([regex]::Escape($key))=" }
    }
    $lines += "$key=$value"
    Set-Content -Path $envPath -Value $lines -Encoding ascii
}

function Remove-DotEnvKey([string]$key) {
    $envPath = Join-Path $root ".env"
    if (-not (Test-Path $envPath)) { return }
    $lines = Get-Content $envPath | Where-Object { $_ -notmatch "^\s*$([regex]::Escape($key))=" }
    Set-Content -Path $envPath -Value $lines -Encoding ascii
}

if ($Lan -or $Tailscale) {
    # 0.0.0.0'a baglanir; erisim asagida Windows Guvenlik Duvari kurallariyla
    # yalniz yerel alt ag ve/veya tailnet (100.64.0.0/10) ile SINIRLANIR.
    $env:GATEWAY_BIND = "0.0.0.0"
    Set-DotEnvValue "GATEWAY_BIND" "0.0.0.0"
    if ($Lan)       { Warn "LAN modu: Gateway yerel aga aciliyor (guvenlik duvari yalniz kendi alt agina izin verecek)." }
    if ($Tailscale) { Warn "Tailscale modu: Gateway tailnet cihazlarina aciliyor (100.64.0.0/10 ile sinirli)." }
} else {
    # Bayrak yoksa guvenli varsayilana geri don (yalniz loopback).
    Set-DotEnvValue "GATEWAY_BIND" "127.0.0.1"
}
# Port secimi HER ZAMAN .env'e yazilir. Eskiden 8088 secildiginde anahtar
# .env'den SILINIYORDU; o zaman duz "docker compose up" compose varsayilanina
# duser ve yine paylasilan portu sahiplenirdi.
Set-DotEnvValue "GATEWAY_PORT"     "$($script:gwPort)"
Set-DotEnvValue "CADDY_HTTP_PORT"  "$($script:webPort)"
Set-DotEnvValue "CADDY_HTTPS_PORT" "$($script:tlsPort)"

# 2b) Ollama koprusu (fix-ollama.ps1) aktifse Gateway'i otomatik ona yonlendir.
# Kullanici $env:OLLAMA_URL vermediyse ve 11500 koprusu ayaktaysa onu kullan.
if (-not $env:OLLAMA_URL) {
    $bridge = Get-NetTCPConnection -LocalPort 11500 -State Listen -ErrorAction SilentlyContinue
    if ($bridge) {
        $env:OLLAMA_URL = "http://host.docker.internal:11500"
        Ok "Ollama koprusu (11500) bulundu - Gateway ona yonlendirildi"
    }
}

# 3) Stack'i baslat (cikti last-run.log'a da yazilir)
$buildFlag = ""
if (-not $NoBuild) { $buildFlag = "--build" }
$composeOut = cmd /c "docker compose up -d $buildFlag 2>&1" | Out-String
Write-Host $composeOut
if ($LASTEXITCODE -ne 0) { Fail "docker compose up basarisiz (cikti yukarida)." }

# 4) Gateway sagligini bekle (migrate + boot icin 120 sn'ye kadar)
$healthy = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$($script:gwPort)/health" -TimeoutSec 3
        if ($r.ok) { $healthy = $true; break }
    } catch { }
}
if (-not $healthy) {
    cmd /c "docker compose logs --tail 30 gateway 2>&1" | Out-String | Write-Host
    Fail "Gateway 120 sn icinde /health'e yanit vermedi (yukarida son loglar)."
}
Ok "Gateway ayakta: http://127.0.0.1:$($script:gwPort)/v1"

# 5) Caddy (web) kontrolu - port 80 baska servis tarafindan kullaniliyorsa uyar
$caddyUp = (docker compose ps --status running caddy 2>$null | Select-String "caddy") -ne $null
if ($caddyUp) { Ok "Web arayuzu: http://localhost:$($script:webPort)" }
else { Warn "Caddy calismiyor (port 80 dolu olabilir). Web icin: npm --prefix web run preview" }

# 6) Ollama kontrolu (yerel modeller icin)
try {
    Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/version" -TimeoutSec 3 *> $null
    Ok "Ollama ayakta (127.0.0.1:11434)"
} catch {
    Warn "Ollama yanit vermiyor - yerel modellerle sohbet calismaz. 'ollama serve' ile baslat."
}

# 7) Istege bagli canli smoke (API anahtari gerekir - README 'first user + API key' adimi)
if ($ApiKey) {
    Write-Host ""
    Write-Host "Canli smoke kosuluyor..." -ForegroundColor Cyan
    $env:GATEWAY_URL = "http://127.0.0.1:$($script:gwPort)"
    $env:GATEWAY_TOKEN = $ApiKey
    node scripts/smoke-live.mjs
    if ($LASTEXITCODE -ne 0) { Fail "smoke-live basarisiz." }
    $env:HORUS_BASE_URL = "http://127.0.0.1:$($script:gwPort)/v1"
    $env:HORUS_API_KEY = $ApiKey
    node scripts/smoke-mobile-control-plane.mjs
    if ($LASTEXITCODE -ne 0) { Fail "mobil kontrol duzlemi smoke basarisiz." }
    Ok "Tum smoke testleri gecti"
} else {
    Write-Host ""
    Write-Host "Ipucu: API anahtarinla tam dogrulama icin:" -ForegroundColor Cyan
    Write-Host "  .\scripts\start-horus.ps1 -NoBuild -ApiKey `$env:NOVA_KEY"
}

Write-Host ""
Write-Host "Project Horus hazir." -ForegroundColor Green

# ---- Telefon baglantisi (-Lan ve/veya -Tailscale ile) ----
if ($Lan -or $Tailscale) {
    Write-Host ""
    Write-Host "=== Telefon baglantisi kuruluyor ===" -ForegroundColor Cyan

    # a) IP'leri topla (iki mod ayni anda kurulabilir)
    $lanIp = $null; $tsIp = $null
    if ($Lan) {
        $lanIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -match '^(192\.168|10\.|172\.(1[6-9]|2[0-9]|3[01]))\.' -and $_.PrefixOrigin -ne 'WellKnown' -and $_.IPAddress -notmatch '^100\.' } |
            Sort-Object -Property SkipAsSource |
            Select-Object -First 1 -ExpandProperty IPAddress)
        if (-not $lanIp) { $lanIp = "<PC-IP>" ; Warn "LAN IP otomatik bulunamadi; 'ipconfig' ile bak." }
    }
    if ($Tailscale) {
        try { $tsIp = (& tailscale ip -4 2>$null | Select-Object -First 1).Trim() } catch { }
        if (-not $tsIp) {
            $tsIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
                Where-Object { $_.IPAddress -match '^100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.' } |
                Select-Object -First 1 -ExpandProperty IPAddress)
        }
        if (-not $tsIp) { $tsIp = "<TAILSCALE-IP>" ; Warn "Tailscale IP bulunamadi. Tailscale acik mi? 'tailscale ip -4' ile bak." }
    }

    # b) Guvenlik duvari kurallari (admin gerekir) - dar kapsamli:
    #    LAN kurali yalniz kendi alt agina (LocalSubnet), Tailscale kurali
    #    yalniz tailnet CGNAT araligina (100.64.0.0/10) izin verir.
    function Ensure-FwRule([string]$name, [string]$remote, [int]$port) {
        try {
            $existing = Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue
            if ($existing) { Remove-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue }
            New-NetFirewallRule -DisplayName $name -Direction Inbound -Action Allow `
                -Protocol TCP -LocalPort $port -RemoteAddress $remote -ErrorAction Stop | Out-Null
            Ok "Firewall: $name (port $port, yalniz $remote)"
            return $true
        } catch {
            Warn "Firewall kurali eklenemedi: $name (yonetici PowerShell gerekir). Elle:"
            Write-Host "  New-NetFirewallRule -DisplayName '$name' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port -RemoteAddress $remote" -ForegroundColor Yellow
            return $false
        }
    }
    # Gateway portu (Horus 18xxx blogu).
    if ($Lan)       { $null = Ensure-FwRule "Project Horus Gateway LAN $($script:gwPort)" "LocalSubnet" $script:gwPort }
    if ($Tailscale) { $null = Ensure-FwRule "Project Horus Gateway Tailscale $($script:gwPort)" "100.64.0.0/10" $script:gwPort }

    # Caddy web portu (80/8081). Compose'da bind adresi YOK, yani Caddy her
    # zaman tum arayuzlerde dinliyor - telefondan web arayuzunu acabilmek icin
    # gerekli, ama kural konmazsa katildigin HER agda (kafe Wi-Fi'si dahil)
    # acik kalir. Gateway ile ayni daralticiyi buraya da uygula.
    if ($Lan)       { $null = Ensure-FwRule "Project Horus Web LAN $($script:webPort)" "LocalSubnet" $script:webPort }
    if ($Tailscale) { $null = Ensure-FwRule "Project Horus Web Tailscale $($script:webPort)" "100.64.0.0/10" $script:webPort }

    # c) Baglama dogrulamasi: Gateway gercekten LAN IP'sinde dinliyor mu?
    #    (Eski surumlerdeki sessiz hata buydu: compose 127.0.0.1'e sabitti.)
    $checkIp = if ($lanIp -and $lanIp -ne "<PC-IP>") { $lanIp } elseif ($tsIp -and $tsIp -ne "<TAILSCALE-IP>") { $tsIp } else { $null }
    if ($checkIp) {
        try {
            $null = Invoke-RestMethod -Uri "http://$($checkIp):$($script:gwPort)/health" -TimeoutSec 5
            Ok "Gateway $checkIp uzerinde erisilebilir dogrulandi"
        } catch {
            Warn "Gateway $($checkIp):$($script:gwPort) uzerinde YANIT VERMIYOR."
            Warn "Container eski port baglamasiyla kalmis olabilir; su komutla tazele:"
            Write-Host "  docker compose up -d --force-recreate gateway" -ForegroundColor Yellow
        }
    }

    # d) mDNS yayini (yalniz LAN modunda) - telefon PC'yi kendiliginden bulsun.
    #    Docker bridge agi multicast'i LAN'a gecirmez, o yuzden yayinci HOST'ta kosar.
    if ($Lan -and $lanIp -and $lanIp -ne "<PC-IP>") {
        Get-Job -Name "horus-mdns" -ErrorAction SilentlyContinue | Stop-Job -PassThru -ErrorAction SilentlyContinue | Remove-Job -Force -ErrorAction SilentlyContinue
        $pcName = $env:COMPUTERNAME
        try {
            Start-Job -Name "horus-mdns" -ScriptBlock {
                param($root, $ip, $port, $name)
                Set-Location $root
                node scripts/announce-mdns.mjs --ip $ip --port $port --name $name
            } -ArgumentList $root, $lanIp, $script:gwPort, $pcName | Out-Null
            Start-Sleep -Milliseconds 700
            $job = Get-Job -Name "horus-mdns" -ErrorAction SilentlyContinue
            if ($job -and $job.State -eq "Running") {
                Ok "mDNS yayini acik (_horus._tcp) - telefon PC'yi listede gorecek"
            } else {
                Warn "mDNS yayini baslamadi (5353 portu Bonjour/iTunes tarafindan tutuluyor olabilir)."
                Warn "Sorun degil: QR ile eslesme calismaya devam eder."
            }
        } catch {
            Warn "mDNS yayinci baslatilamadi: $($_.Exception.Message). QR yolu etkilenmez."
        }
        Write-Host "  (durdurmak icin: Stop-Job -Name horus-mdns; Remove-Job -Name horus-mdns)" -ForegroundColor DarkGray
    }

    # e) Eslesme kodu + QR. API anahtari BURADA uretilmez; telefon kodu takas
    #    ettiginde uretilir. Ekran goruntusu sizsa bile 5 dk sonra ise yaramaz.
    $qrHost = if ($lanIp -and $lanIp -ne "<PC-IP>") { $lanIp } elseif ($tsIp -and $tsIp -ne "<TAILSCALE-IP>") { $tsIp } else { $null }
    if ($qrHost) {
        $pairCmd = "docker compose exec -T gateway node scripts/pair.mjs --host $qrHost --port $($script:gwPort) --name `"$($env:COMPUTERNAME)`" 2>&1"
        $pairOut = cmd /c $pairCmd | Out-String
        # Yalniz ASCII on ekine bakilir. "TELEFONU BAĞLA" icindeki Ğ, konsol
        # kod sayfasi UTF-8 degilse baska bayta donusur ve tam esleme kacar;
        # 2026-08-26'da tam bu yuzden kod uretildigi halde "uretilemedi"
        # uyarisi basildi. Asil karar zaten cikis kodunda.
        if ($LASTEXITCODE -eq 0 -and $pairOut -match "TELEFONU BA") {
            Write-Host $pairOut
        } else {
            Warn "Eslesme kodu uretilemedi. Ciktisi:"
            Write-Host $pairOut -ForegroundColor DarkGray
            Warn "Yedek yol: docker compose exec gateway node scripts/bootstrap-user.mjs phone@horus.local"
        }
    } else {
        Warn "QR icin kullanilabilir bir adres bulunamadi."
    }

    Write-Host "==================== TELEFON ====================" -ForegroundColor Green
    if ($Lan)       { Write-Host "  1) Ayni Wi-Fi'deysen: uygulama PC'yi kendisi bulur (Ayarlar > PC'yi bagla)." }
    Write-Host "  2) Bulamazsa yukaridaki QR'i okut."
    Write-Host "  3) Kamera yoksa QR'in altindaki kodu ve adresi elle gir."
    if ($tsIp -and $tsIp -ne "<TAILSCALE-IP>") {
        Write-Host "  Tailscale adresi (disaridan): http://$($tsIp):$($script:gwPort)/v1" -ForegroundColor DarkGray
    }
    Write-Host "=================================================" -ForegroundColor Green
}
