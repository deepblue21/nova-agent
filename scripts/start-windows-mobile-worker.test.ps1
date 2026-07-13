[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$launcher = Join-Path $PSScriptRoot "start-windows-mobile-worker.ps1"
$envFile = Join-Path $projectRoot "mobile-worker\.env"
$powershellExecutable = Join-Path $PSHOME "powershell.exe"
$proxyNames = @("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "all_proxy", "no_proxy")
$loaderSecret = "loader-secret-not-real"
$proxySecret = "proxy-secret-not-real"
$failures = [System.Collections.Generic.List[string]]::new()

function Assert-True {
    param([Parameter(Mandatory = $true)][bool]$Condition, [Parameter(Mandatory = $true)][string]$Message)

    if (!$Condition) {
        $script:failures.Add($Message)
    }
}

function Invoke-LauncherFixture {
    param(
        [Parameter(Mandatory = $true)][string]$Content,
        [string]$ProxyName,
        [switch]$PrepareOnly
    )

    Set-Content -LiteralPath $script:envFile -Value $Content -NoNewline
    if ($ProxyName) {
        [Environment]::SetEnvironmentVariable($ProxyName, $script:proxySecret, "Process")
    }

    $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $script:launcher)
    if ($PrepareOnly) {
        $arguments += "-PrepareOnly"
    }
    $stdoutFile = Join-Path $script:temporaryRoot ("launcher-stdout-" + [guid]::NewGuid().ToString("N") + ".txt")
    $stderrFile = Join-Path $script:temporaryRoot ("launcher-stderr-" + [guid]::NewGuid().ToString("N") + ".txt")
    try {
        $process = Start-Process -FilePath $script:powershellExecutable -ArgumentList $arguments -NoNewWindow -PassThru -Wait -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile
        $output = (Get-Content -LiteralPath $stdoutFile -Raw) + (Get-Content -LiteralPath $stderrFile -Raw)
    }
    finally {
        Remove-Item -LiteralPath $stdoutFile, $stderrFile -ErrorAction SilentlyContinue
    }
    [PSCustomObject]@{
        ExitCode = $process.ExitCode
        Output = $output
    }
}

function Assert-SecretIsRedacted {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Output, [Parameter(Mandatory = $true)][string]$Message)

    Assert-True -Condition (!$Output.Contains($script:loaderSecret) -and !$Output.Contains($script:proxySecret)) -Message $Message
}

if (Test-Path -LiteralPath $envFile) {
    throw "Refusing to replace an existing mobile-worker/.env while running the launcher regression test"
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("horus-windows-launcher-test-" + [guid]::NewGuid().ToString("N"))
$binDirectory = Join-Path $temporaryRoot "bin"
$sdkDirectory = Join-Path $temporaryRoot "sdk"
$platformTools = Join-Path $sdkDirectory "platform-tools"
$captureFile = Join-Path $temporaryRoot "launched-environment.txt"
$savedEnvironment = @{}

try {
    New-Item -ItemType Directory -Force -Path $binDirectory, $platformTools | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $platformTools "adb.exe") | Out-Null
    @(
        "@echo off",
        'if /I "%1"=="python" (',
        "  echo C:\\test-python312\\python.exe",
        "  exit /b 0",
        ")",
        'if /I "%1"=="sync" exit /b 0',
        'if /I "%1"=="run" (',
        '  set > "%HORUS_TEST_ENV_CAPTURE%"',
        "  exit /b 0",
        ")",
        "exit /b 1"
    ) | Set-Content -LiteralPath (Join-Path $binDirectory "uv.cmd") -Encoding ascii

    $environmentNames = @("PATH", "PATHEXT", "ANDROID_SDK_ROOT", "HORUS_TEST_ENV_CAPTURE") + $proxyNames
    foreach ($name in $environmentNames) {
        $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
        [Environment]::SetEnvironmentVariable($name, $null, "Process")
    }
    $systemDirectory = [Environment]::GetFolderPath([Environment+SpecialFolder]::System)
    [Environment]::SetEnvironmentVariable("PATH", "$binDirectory;$systemDirectory", "Process")
    [Environment]::SetEnvironmentVariable("PATHEXT", ".CMD;.EXE;.BAT", "Process")
    [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdkDirectory, "Process")
    [Environment]::SetEnvironmentVariable("HORUS_TEST_ENV_CAPTURE", $captureFile, "Process")
    $fakeUvSource = @(& $powershellExecutable -NoProfile -Command "(Get-Command uv -CommandType Application).Source")
    Assert-True -Condition ($fakeUvSource.Count -eq 1 -and $fakeUvSource[0] -eq (Join-Path $binDirectory "uv.cmd")) -Message "The fake uv runner was not selected: $($fakeUvSource -join ', ')"

    $workerSettingsFixture = @"
HORUS_GATEWAY_URL=http://127.0.0.1:8088/v1
MOBILE_WORKER_TOKEN=$loaderSecret
MOBILE_WORKER_DEVICE_ID=emulator-5554
MOBILE_WORKER_OLLAMA_URL=
MOBILE_WORKER_OLLAMA_WSL_DISTRO=Ubuntu
MOBILE_WORKER_OLLAMA_MODEL=test-model
MOBILE_WORKER_MAX_STEPS=8
MOBILE_WORKER_STATUS_POLL_SECONDS=1.0
MOBILE_WORKER_EXECUTION_TIMEOUT_SECONDS=120.0
MOBILE_WORKER_READINESS_TIMEOUT_SECONDS=15.0
MOBILE_WORKER_ADB_SERVER_HOST=127.0.0.1
MOBILE_WORKER_ADB_SERVER_PORT=5037
"@
    $workerSettingsWithoutReadinessTimeout = $workerSettingsFixture -replace "MOBILE_WORKER_READINESS_TIMEOUT_SECONDS=15\.0\r?\n", ""

    $readinessResult = Invoke-LauncherFixture -Content $workerSettingsFixture -PrepareOnly
    Assert-True -Condition ($readinessResult.ExitCode -eq 0) -Message "MOBILE_WORKER_READINESS_TIMEOUT_SECONDS must be accepted by the launcher"
    Assert-True -Condition ($readinessResult.Output.Contains("values redacted")) -Message "Prepare-only output must state that environment values are redacted"
    Assert-SecretIsRedacted -Output $readinessResult.Output -Message "Loader output must not emit loaded secrets"

    $unsupportedProxyResult = Invoke-LauncherFixture -Content ($workerSettingsWithoutReadinessTimeout + "`nHTTP_PROXY=$proxySecret`n") -PrepareOnly
    Assert-True -Condition ($unsupportedProxyResult.ExitCode -ne 0) -Message "An unsupported HTTP_PROXY entry must fail closed"
    Assert-True -Condition ($unsupportedProxyResult.Output.Contains("Unsupported environment key HTTP_PROXY")) -Message "An unsupported HTTP_PROXY entry must report only its key"
    Assert-SecretIsRedacted -Output $unsupportedProxyResult.Output -Message "An unsupported proxy value must not be emitted"

    foreach ($gatewayOnlyKey in @("MOBILE_WORKER_ENABLED", "MOBILE_WORKER_LEASE_MS", "MOBILE_WORKER_GOAL_POLICY")) {
        $gatewayOnlyResult = Invoke-LauncherFixture -Content ($workerSettingsWithoutReadinessTimeout + "`n$gatewayOnlyKey=not-a-worker-setting`n") -PrepareOnly
        Assert-True -Condition ($gatewayOnlyResult.ExitCode -ne 0) -Message "$gatewayOnlyKey must not be accepted by the launcher"
        Assert-True -Condition ($gatewayOnlyResult.Output.Contains("Unsupported environment key $gatewayOnlyKey")) -Message "$gatewayOnlyKey must be rejected as an unsupported environment key"
        Assert-SecretIsRedacted -Output $gatewayOnlyResult.Output -Message "$gatewayOnlyKey rejection must not emit loaded secrets"
    }

    foreach ($proxyName in $proxyNames) {
        Remove-Item -LiteralPath $captureFile -ErrorAction SilentlyContinue
        $launchResult = Invoke-LauncherFixture -Content $workerSettingsWithoutReadinessTimeout -ProxyName $proxyName
        Assert-True -Condition ($launchResult.ExitCode -eq 0) -Message "$proxyName must be removed before the worker launch"
        Assert-SecretIsRedacted -Output $launchResult.Output -Message "$proxyName launch output must not emit secrets"
        $launchedEnvironment = if (Test-Path -LiteralPath $captureFile) { Get-Content -LiteralPath $captureFile -Raw } else { "" }
        Assert-True -Condition (![regex]::IsMatch($launchedEnvironment, "(?im)^(HTTP_PROXY|HTTPS_PROXY|ALL_PROXY|NO_PROXY)=")) -Message "$proxyName must be absent from the launched worker environment"
    }
}
finally {
    Remove-Item -LiteralPath $envFile -ErrorAction SilentlyContinue
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
    Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        [Console]::Error.WriteLine("FAIL: $failure")
    }
    exit 1
}

Write-Output "PASS: Windows mobile worker launcher regression tests"
