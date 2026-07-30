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

# 2) Port 80/443 doluysa Caddy'yi alternatif porta al
$script:webPort = 80
$p80 = Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue
if ($p80) {
    $env:CADDY_HTTP_PORT = "8081"
    $script:webPort = 8081
    Warn "Port 80 dolu - Caddy 8081'e alindi (http://localhost:8081)"
}
$p443 = Get-NetTCPConnection -LocalPort 443 -State Listen -ErrorAction SilentlyContinue
if ($p443) {
    $env:CADDY_HTTPS_PORT = "8443"
    Warn "Port 443 dolu - Caddy HTTPS 8443'e alindi"
}
$script:gwPort = 8088
$p8088 = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue
if ($p8088) {
    $owner = ""
    try { $owner = (Get-Process -Id $p8088[0].OwningProcess -ErrorAction SilentlyContinue).ProcessName } catch { }
    $env:GATEWAY_PORT = "18088"
    $script:gwPort = 18088
    Warn "Port 8088 dolu (kullanan: $owner) - Gateway 18088'e alindi"
}

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
if ($script:gwPort -ne 8088) { Set-DotEnvValue "GATEWAY_PORT" "$($script:gwPort)" }
else { Remove-DotEnvKey "GATEWAY_PORT" }

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
if ($caddyUp) { if ($script:webPort -eq 80) { Ok "Web arayuzu: http://localhost" } else { Ok "Web arayuzu: http://localhost:$($script:webPort)" } }
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
    function Ensure-FwRule([string]$name, [string]$remote) {
        try {
            $existing = Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue
            if ($existing) { Remove-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue }
            New-NetFirewallRule -DisplayName $name -Direction Inbound -Action Allow `
                -Protocol TCP -LocalPort $script:gwPort -RemoteAddress $remote -ErrorAction Stop | Out-Null
            Ok "Firewall: $name (port $($script:gwPort), yalniz $remote)"
            return $true
        } catch {
            Warn "Firewall kurali eklenemedi: $name (yonetici PowerShell gerekir). Elle:"
            Write-Host "  New-NetFirewallRule -DisplayName '$name' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $($script:gwPort) -RemoteAddress $remote" -ForegroundColor Yellow
            return $false
        }
    }
    if ($Lan)       { $null = Ensure-FwRule "Project Horus Gateway LAN $($script:gwPort)" "LocalSubnet" }
    if ($Tailscale) { $null = Ensure-FwRule "Project Horus Gateway Tailscale $($script:gwPort)" "100.64.0.0/10" }

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

    # d) Telefon icin API key uret (bir kez gorunur)
    Write-Host ""
    Write-Host "Telefon icin API anahtari uretiliyor..." -ForegroundColor Cyan
    $keyOut = cmd /c "docker compose exec -T gateway node scripts/bootstrap-user.mjs phone@horus.local 5 2>&1" | Out-String
    $key = ([regex]::Match($keyOut, 'nv_[0-9a-f]{6}_\S+')).Value

    Write-Host ""
    Write-Host "==================== TELEFON AYARLARI ====================" -ForegroundColor Green
    if ($lanIp) { Write-Host "  Ayni Wi-Fi'de  (Base URL): http://$($lanIp):$($script:gwPort)/v1" }
    if ($tsIp)  { Write-Host "  Tailscale ile  (Base URL): http://$($tsIp):$($script:gwPort)/v1" }
    if ($key) { Write-Host "  API anahtari (Token):      $key" }
    else { Warn "API anahtari uretilemedi. Elle: docker compose exec gateway node scripts/bootstrap-user.mjs phone@horus.local" }
    Write-Host "=========================================================" -ForegroundColor Green
    Write-Host "Telefonda: uygulamayi ac > Ayarlar > PC baglantisi > adresi ve anahtari gir > 'Baglantiyi test et' > Kaydet."
    if ($Lan)       { Write-Host "  - Ayni Wi-Fi adresi icin telefon ile PC AYNI agda olmali." }
    if ($Tailscale) { Write-Host "  - Tailscale adresi icin telefonda Tailscale kurulu ve AYNI hesapta olmali (mobil veriyle de calisir)." }
}
