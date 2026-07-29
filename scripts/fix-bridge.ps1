# fix-bridge.ps1 - Ollama koprusunu (11500) guncel WSL IP'sine tazeler.
#
# Sorun: WSL'in IP'si her yeniden baslatmada degisebilir; portproxy eski IP'ye
# isaret ederse gateway Ollama'ya ulasamaz -> "upstream error".
#
#   .\scripts\fix-bridge.ps1            # koprunun hedefini guncel WSL IP'sine cevirir
#   .\scripts\fix-bridge.ps1 -Install   # + her oturum acilisinda otomatik calismasi icin gorev kaydeder
#
# Yonetici PowerShell gerektirir (netsh portproxy + zamanlanmis gorev).

param([switch]$Install)

$ErrorActionPreference = "Continue"
function Ok($m)   { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "HATA: $m" -ForegroundColor Red; exit 1 }

$listen = 11500

# 0) Yonetici mi?
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Fail "Bu script yonetici gerektirir. PowerShell'i 'Yonetici olarak calistir' ile ac." }

# 1) Guncel WSL IP'sini bul
$raw = & wsl -e sh -c "hostname -I" 2>$null
$wslIp = ([regex]::Match(($raw -join ' '), '\d{1,3}(\.\d{1,3}){3}')).Value
if (-not $wslIp) { Fail "WSL IP bulunamadi. WSL calisiyor mu? ('wsl echo ok' ile dene)" }
Ok "WSL IP: $wslIp"

# 2) Ollama o IP'de yanit veriyor mu? (OLLAMA_HOST=0.0.0.0 gerekli)
try {
    $null = Invoke-RestMethod -Uri "http://$($wslIp):11434/api/version" -TimeoutSec 5
    Ok "Ollama ${wslIp}:11434 uzerinde yanit veriyor"
} catch {
    Fail "Ollama ${wslIp}:11434 yanit vermiyor. WSL'de calisiyor mu ve OLLAMA_HOST=0.0.0.0 ayarli mi? (bkz. /etc/systemd/system/ollama.service.d/override.conf)"
}

# 3) Portproxy'yi tazele: 0.0.0.0:11500 -> <WSL-IP>:11434
netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=$listen 2>$null | Out-Null
netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=$listen connectaddress=$wslIp connectport=11434 | Out-Null
Ok "Kopru tazelendi: 0.0.0.0:$listen -> ${wslIp}:11434"

# 4) Firewall kurali (bir kez)
$rule = "Horus Ollama proxy $listen"
if (-not (Get-NetFirewallRule -DisplayName $rule -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $rule -Direction Inbound -Action Allow -Protocol TCP -LocalPort $listen -Profile Any -ErrorAction SilentlyContinue | Out-Null
    Ok "Firewall kurali eklendi ($rule)"
}

# 5) Dogrulama: kopru uzerinden model listesi
try {
    $n = (Invoke-RestMethod -Uri "http://127.0.0.1:$listen/api/tags" -TimeoutSec 5).models.Count
    Ok "Kopru dogrulandi: $n model gorunuyor"
} catch {
    Warn "127.0.0.1:$listen henuz dogrulanamadi; birkac saniye sonra tekrar dene."
}

# 6) Istege bagli: her oturum acilisinda otomatik calistir
if ($Install) {
    $scriptPath = $MyInvocation.MyCommand.Path
    $action    = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`""
    $trigger   = New-ScheduledTaskTrigger -AtLogOn
    # UserId, DOMAIN\kullanici biciminde olmali; yalin kullanici adi
    # "Parametre hatali" hatasi verir.
    $userId    = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $principal = New-ScheduledTaskPrincipal -UserId $userId -RunLevel Highest
    try {
        Register-ScheduledTask -TaskName "HorusOllamaBridge" -Action $action -Trigger $trigger `
            -Principal $principal -Force -ErrorAction Stop | Out-Null
        Ok "Acilis gorevi kaydedildi (HorusOllamaBridge): her oturumda kopru otomatik tazelenecek."
    } catch {
        # Basarisiz kaydi basarili gibi gostermeyelim.
        Warn "Acilis gorevi kaydedilemedi: $($_.Exception.Message)"
        Warn "Kopru simdilik calisiyor; WSL IP degisirse bu scripti tekrar calistir."
    }
}

Write-Host ""
Write-Host "Kopru hazir. Gateway OLLAMA_URL=http://host.docker.internal:$listen ile calismaya devam edebilir." -ForegroundColor Green
