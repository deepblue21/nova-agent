<#
.SYNOPSIS
  Play "upload key" (yükleme anahtarı) üretir ve nova-android/keystore.properties dosyasını yazar.

.DESCRIPTION
  Play bloker B2. Anahtar DEPONUN DIŞINDA, varsayılan olarak
  %USERPROFILE%\.horus-keys\ altında saklanır; depoya yalnız ona işaret eden
  keystore.properties yazılır ve o dosya da gitignore'ludur.

  Parolalar hiçbir yere loglanmaz, ekrana basılmaz, komut geçmişine düşmez —
  SecureString ile sorulur.

  ÖNEMLİ: Bu anahtarı KAYBEDERSEN uygulamanın yeni sürümünü yayınlayamazsın.
  Play App Signing kullanırken bile yükleme anahtarı sende kalır. Ürettikten
  sonra .jks dosyasını ve parolaları bir parola yöneticisine / çevrimdışı
  yedeğe koy.

.EXAMPLE
  .\scripts\new-upload-keystore.ps1
#>
[CmdletBinding()]
param(
    # Anahtarın yazılacağı klasör. Varsayılan: depo dışı.
    [string]$KeyDir = (Join-Path $env:USERPROFILE ".horus-keys"),
    [string]$Alias  = "nova-upload",
    # Play, uzun ömürlü anahtar ister. 10000 gün ~27 yıl.
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = "Stop"

$repoRoot    = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot "nova-android"
$propsPath   = Join-Path $androidRoot "keystore.properties"
$storePath   = Join-Path $KeyDir "$Alias.jks"

# —— keytool'u bul ——————————————————————————————————————————————
function Find-Keytool {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
    $candidates += "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe"
    $candidates += "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "keytool bulunamadi. JAVA_HOME ayarla (JDK 17) ya da Android Studio'nun jbr klasorunu kullan."
}
$keytool = Find-Keytool
Write-Host "keytool: $keytool"

if (Test-Path $storePath) {
    throw "Bu dosya zaten var: $storePath`nUzerine yazmak Play'e yuklenmis anahtari kaybettirir. Farkli -Alias ya da -KeyDir ver."
}

# —— kimlik bilgileri ————————————————————————————————————————————
$cn = Read-Host "Ad Soyad (CN)"
$o  = Read-Host "Kurum / marka (O) [bos birakilabilir]"
$c  = Read-Host "Ulke kodu (C) [orn. TR]"
if ([string]::IsNullOrWhiteSpace($c)) { $c = "TR" }
$dname = "CN=$cn, O=$o, C=$c"

# —— parola ——————————————————————————————————————————————————————
$p1 = Read-Host "Keystore parolasi (en az 12 karakter)" -AsSecureString
$p2 = Read-Host "Parolayi tekrar gir"                    -AsSecureString
$plain1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p1))
$plain2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p2))
if ($plain1 -ne $plain2)   { throw "Parolalar eslesmedi." }
if ($plain1.Length -lt 12) { throw "Parola cok kisa (en az 12 karakter)." }

New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null

# keytool'a parolayi ARGUMAN olarak vermiyoruz (komut satiri baska sureclerden gorulebilir);
# stdin'den okutuyoruz.
$ktArgs = @(
    "-genkeypair", "-v",
    "-keystore", $storePath,
    "-alias", $Alias,
    "-keyalg", "RSA", "-keysize", "4096",
    "-validity", "$ValidityDays",
    "-dname", $dname,
    "-storetype", "PKCS12"
)
# PKCS12'de store ve key parolasi aynidir; keytool ikisini de stdin'den ister.
$plain1 + "`n" + $plain1 + "`n" | & $keytool @ktArgs
if ($LASTEXITCODE -ne 0) { throw "keytool basarisiz oldu (cikis kodu $LASTEXITCODE)." }

# —— keystore.properties ————————————————————————————————————————
$escaped = $storePath -replace '\\', '\\\\'
@"
# Play yukleme anahtari — BU DOSYA GITIGNORE'LU, ASLA COMMIT ETME.
# Uretildi: $(Get-Date -Format 'yyyy-MM-dd')
storeFile=$escaped
storePassword=$plain1
keyAlias=$Alias
keyPassword=$plain1
"@ | Set-Content -Path $propsPath -Encoding UTF8 -NoNewline

Write-Host ""
Write-Host "Anahtar uretildi : $storePath"
Write-Host "Yapilandirma     : $propsPath  (gitignore'lu)"
Write-Host ""
Write-Host "SIMDI YAP:" -ForegroundColor Yellow
Write-Host "  1) $storePath dosyasini ve parolayi bir parola yoneticisine + cevrimdisi yedege koy."
Write-Host "  2) Dogrula:  cd nova-android; .\gradlew.bat bundleRelease"
Write-Host "  3) Imzayi kontrol et: $keytool -list -v -keystore `"$storePath`""
Write-Host ""
Write-Host "Bu anahtari kaybedersen uygulamanin yeni surumunu yayinlayamazsin." -ForegroundColor Red

$plain1 = $null; $plain2 = $null
[GC]::Collect()
