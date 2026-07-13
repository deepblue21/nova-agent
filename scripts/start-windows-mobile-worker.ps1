[CmdletBinding()]
param(
    [switch]$PrepareOnly,
    [switch]$Once,
    [string]$Distro = "Ubuntu",
    [string]$EnvFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Import-WorkerEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string[]]$AllowedKeys
    )

    $loaded = 0
    $lineNumber = 0
    foreach ($rawLine in [System.IO.File]::ReadLines($Path)) {
        $lineNumber++
        $line = $rawLine.Trim()
        if (!$line -or $line.StartsWith("#")) {
            continue
        }
        $entry = [regex]::Match($line, "^(?<name>[A-Za-z_][A-Za-z0-9_]*)=(?<value>.*)$")
        if (!$entry.Success) {
            throw "Malformed environment entry at line $lineNumber in mobile-worker/.env"
        }
        $name = $entry.Groups['name'].Value
        if ($AllowedKeys -notcontains $name) {
            throw "Unsupported environment key $name at line $lineNumber in mobile-worker/.env"
        }
        Set-Item -Path "Env:$name" -Value $entry.Groups['value'].Value
        $loaded++
    }
    if ($loaded -eq 0) {
        throw "mobile-worker/.env must contain at least one KEY=value entry"
    }
    return $loaded
}

function Clear-ProxyEnvironment {
    foreach ($name in @("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "all_proxy", "no_proxy")) {
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }
}

function Find-Adb {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string[]]$SdkRoots)

    foreach ($candidate in $SdkRoots) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $platformTools = Join-Path $candidate "platform-tools"
        $adb = Join-Path $platformTools "adb.exe"
        if (Test-Path -LiteralPath $adb -PathType Leaf) {
            return [PSCustomObject]@{
                PlatformTools = $platformTools
                Adb = $adb
            }
        }
    }
    throw "adb.exe was not found under ANDROID_SDK_ROOT, ANDROID_HOME, or LOCALAPPDATA\\Android\\Sdk"
}

$scriptDirectory = Split-Path -Parent $PSCommandPath
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory ".."))
$workerDirectory = Join-Path $projectRoot "mobile-worker"
$canonicalEnvFile = Join-Path $workerDirectory ".env"
$windowsVenv = Join-Path $workerDirectory ".venv-windows"
$allowedWorkerEnvironmentKeys = @(
    "MOBILE_WORKER_TOKEN",
    "MOBILE_WORKER_DEVICE_ID",
    "MOBILE_WORKER_ADB_SERVER_HOST",
    "MOBILE_WORKER_ADB_SERVER_PORT",
    "HORUS_GATEWAY_URL",
    "MOBILE_WORKER_OLLAMA_URL",
    "MOBILE_WORKER_OLLAMA_WSL_DISTRO",
    "MOBILE_WORKER_OLLAMA_MODEL",
    "MOBILE_WORKER_MAX_STEPS",
    "MOBILE_WORKER_STATUS_POLL_SECONDS",
    "MOBILE_WORKER_EXECUTION_TIMEOUT_SECONDS",
    "MOBILE_WORKER_READINESS_TIMEOUT_SECONDS"
)

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = $canonicalEnvFile
}
$requestedEnvFile = [System.IO.Path]::GetFullPath($EnvFile)
if (!$requestedEnvFile.Equals($canonicalEnvFile, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "-EnvFile must be the ignored mobile-worker/.env file"
}
if (!(Test-Path -LiteralPath $workerDirectory -PathType Container) -or !(Test-Path -LiteralPath (Join-Path $workerDirectory "pyproject.toml") -PathType Leaf)) {
    throw "The mobile-worker project is missing from the canonical project root"
}
if (!(Test-Path -LiteralPath $canonicalEnvFile -PathType Leaf)) {
    throw "mobile-worker/.env is required; copy mobile-worker/.env.example and set local values without committing it"
}
if ($Distro -notmatch "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") {
    throw "-Distro must be a valid WSL distro identifier"
}

$uv = Get-Command uv -CommandType Application -ErrorAction Stop
$wsl = Get-Command wsl.exe -CommandType Application -ErrorAction Stop
$loadedVariables = Import-WorkerEnvironment -Path $canonicalEnvFile -AllowedKeys $allowedWorkerEnvironmentKeys
Clear-ProxyEnvironment
$python312 = & $uv.Source python find 3.12 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($python312)) {
    throw "uv could not find a local Python 3.12 interpreter"
}
$adb = Find-Adb -SdkRoots @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA "Android\\Sdk"))

$env:ADBUTILS_ADB_PATH = $adb.Adb
$env:PATH = "$($adb.PlatformTools);$env:PATH"
$env:MOBILE_WORKER_ADB_SERVER_HOST = "127.0.0.1"
$env:MOBILE_WORKER_ADB_SERVER_PORT = "5037"
$env:MOBILE_WORKER_OLLAMA_WSL_DISTRO = $Distro
Remove-Item -Path "Env:MOBILE_WORKER_OLLAMA_URL" -ErrorAction SilentlyContinue

if ($PrepareOnly) {
    Write-Output "Windows mobile worker readiness"
    Write-Output "Environment file: loaded ($loadedVariables variables; values redacted)"
    Write-Output "ADB executable: found"
    Write-Output "ADB endpoint: 127.0.0.1:5037"
    Write-Output "Python: local 3.12 interpreter found by uv"
    Write-Output "WSL launcher: found; distro identifier: $Distro"
    Write-Output "Ollama mode: derived WSL NAT address only"
    Write-Output "Prepare-only: no virtual environment, uv sync, ADB, firewall, WSL, or Ollama state was changed"
    exit 0
}

$env:VIRTUAL_ENV = $windowsVenv
Push-Location $workerDirectory
try {
    & $uv.Source sync --active --locked --python $python312
    if ($LASTEXITCODE -ne 0) {
        throw "uv sync failed"
    }

    $workerArguments = @()
    if ($Once) {
        $workerArguments += "--once"
    }
    & $uv.Source run --active horus-mobile-worker @workerArguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
