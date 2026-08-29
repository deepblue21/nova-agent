@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title NOVA - uygulama dogrulamasi

set "REPO=%~dp0"
set "LOG=%REPO%build-log.txt"

cd /d "%REPO%nova-android"

echo === NOVA / Project Horus - uygulama dogrulamasi === > "%LOG%"
echo Tarih: %DATE% %TIME% >> "%LOG%"
echo. >> "%LOG%"

echo.
echo  [1/3] Birim testleri (testDebugUnitTest)...
echo  Ilk seferde Gradle 9.4.1 + JDK indirilecegi icin birkac dakika surebilir.
echo ---------- testDebugUnitTest ---------- >> "%LOG%"
call gradlew.bat testDebugUnitTest --console=plain >> "%LOG%" 2>&1
set TEST_EXIT=%ERRORLEVEL%
echo TEST_EXIT=%TEST_EXIT% >> "%LOG%"
echo  -^> cikis kodu: %TEST_EXIT%

echo.
echo  [2/3] Lint (lintDebug)...
echo ---------- lintDebug ---------- >> "%LOG%"
call gradlew.bat lintDebug --console=plain >> "%LOG%" 2>&1
set LINT_EXIT=%ERRORLEVEL%
echo LINT_EXIT=%LINT_EXIT% >> "%LOG%"
echo  -^> cikis kodu: %LINT_EXIT%

echo.
echo  [3/3] Guncel APK (assembleDebug)...
echo ---------- assembleDebug ---------- >> "%LOG%"
call gradlew.bat assembleDebug --console=plain >> "%LOG%" 2>&1
set ASM_EXIT=%ERRORLEVEL%
echo ASM_EXIT=%ASM_EXIT% >> "%LOG%"
echo  -^> cikis kodu: %ASM_EXIT%

if "%ASM_EXIT%"=="0" (
  if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "%REPO%NOVA-Horus-debug.apk" >nul
    echo APK_KOPYALANDI=%REPO%NOVA-Horus-debug.apk >> "%LOG%"
    echo  -^> guncel APK: NOVA-Horus-debug.apk
  )
)

echo. >> "%LOG%"
echo ---------- test raporu ozeti ---------- >> "%LOG%"
if exist "app\build\reports\tests\testDebugUnitTest\index.html" (
  echo rapor: app\build\reports\tests\testDebugUnitTest\index.html >> "%LOG%"
)
echo ---------- lint raporu ---------- >> "%LOG%"
if exist "app\build\reports\lint-results-debug.txt" (
  type "app\build\reports\lint-results-debug.txt" >> "%LOG%"
) else (
  echo (lint metin raporu yok) >> "%LOG%"
)

echo.
echo ==========================================================
echo  Bitti.  test=%TEST_EXIT%  lint=%LINT_EXIT%  apk=%ASM_EXIT%
echo  Ayrinti: %LOG%
echo  Claude bu dosyayi kendisi okuyacak - sohbete "bitti" yaz.
echo ==========================================================
echo.
pause
