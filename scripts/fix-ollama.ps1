# fix-ollama.ps1 - WSL'deki Ollama'yi Docker'daki Gateway'e erisilebilir kilar.
#
# Sorun: Ollama WSL'de 127.0.0.1'de dinliyor; Gateway konteyneri host.docker.internal
# uzerinden Windows'un o loopback'ine ulasamiyor -> "upstream error".
#
# Cozum: Windows'ta 0.0.0.0:11500 -> 127.0.0.1:11434 port koprusu kurar (Windows
# localhost, WSL2 forwarding sayesinde zaten Ollama'ya gidiyor), firewall'i acar,
# Gateway'i OLLAMA_URL=http://host.docker.internal:11500'e yonlendirip yeniden baslatir.
#
# Yonetici PowerShell'de calistir:  .\scripts\fix-ollama.ps1

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "HATA: $m" -ForegroundColor Red; exit 1 }

$listen = 11500

# 0) Yonetici mi? (portproxy + firewall gerektirir)
$admin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Fail "Bu script yonetici gerektirir. PowerShell'i 'Yonetici olarak calistir' ile ac." }

# 1) Ollama Windows localhost'ta erisilebiliyor mu? (WSL2 forwarding)
try {
    Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/tags" -TimeoutSec 5 *> $null
    Ok "Ollama Windows localhost:11434 uzerinden erisilebilir"
} catch {
    Fail "Windows'tan 127.0.0.1:11434 Ollama'ya erisilemiyor. WSL'de Ollama calisiyor mu? ('wsl ollama list')"
}

# 2) Port koprusu: 0.0.0.0:11500 -> 127.0.0.1:11434
netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=$listen 2>$null | Out-Null
netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=$listen connectaddress=127.0.0.1 connectport=11434 | Out-Null
if ($LASTEXITCODE -eq 0) { Ok "Port koprusu kuruldu: 0.0.0.0:$listen -> 127.0.0.1:11434" }
else { Warn "portproxy kurulamadi (netsh cikis $LASTEXITCODE)" }

# 3) Firewall: Docker konteynerinden gelen $listen istegine izin
$ruleName = "Horus Ollama proxy $listen"
if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow `
        -Protocol TCP -LocalPort $listen -Profile Any -ErrorAction SilentlyContinue | Out-Null
}
Ok "Firewall kurali hazir (port $listen)"

# 4) Gateway'i yeni OLLAMA_URL ile yeniden baslat (diger portlari koru)
$env:OLLAMA_URL = "http://host.docker.internal:$listen"
if (Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue) { $env:GATEWAY_PORT = "18088" }
$env:GATEWAY_BIND = "0.0.0.0"   # telefon/tailnet erisimi acik kalsin
Write-Host "Gateway yeniden baslatiliyor (OLLAMA_URL=$($env:OLLAMA_URL))..." -ForegroundColor Cyan
cmd /c "docker compose up -d gateway 2>&1" | Out-String | Write-Host

# 5) Konteynerden Ollama erisim testi
Start-Sleep -Seconds 4
$test = cmd /c "docker compose exec -T gateway node -e ""fetch(process.env.OLLAMA_URL+'/api/tags').then(r=>r.json()).then(d=>console.log('OLLAMA-OK',d.models.length)).catch(e=>console.log('OLLAMA-ERR',e.message))"" 2>&1" | Out-String
Write-Host $test
if ($test -match "OLLAMA-OK") { Ok "Konteyner artik Ollama'ya ulasiyor. Telefondan tekrar dene." }
else { Warn "Konteyner hala ulasamiyor. Ciktiyi paylas." }
