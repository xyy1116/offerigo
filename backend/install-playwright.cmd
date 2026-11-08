@echo off
setlocal

echo ============================================================
echo   Playwright Chromium browser install (Maven-based, no Node)
echo ============================================================
echo.

echo [1/4] Checking Maven environment...
where mvn >nul 2>nul
if errorlevel 1 (
    echo [FAIL] 'mvn' command not found. Please install Maven and add it to PATH.
    echo.
    pause
    exit /b 1
)
echo [OK] Maven is available.

echo.
echo [2/4] Validating Maven project and dependencies...
call mvn -q -DskipTests validate
if errorlevel 1 (
    echo [FAIL] Maven dependency resolution failed. Check network or local repository.
    echo.
    pause
    exit /b 1
)
echo [OK] Project dependencies resolved.

echo.
echo [3/4] Downloading and installing Chromium (first run ~150MB, please wait)...
call mvn -e exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
if errorlevel 1 (
    echo.
    echo [FAIL] Chromium installation failed.
    echo   Likely cause: network restriction / proxy / unreachable download source.
    echo   Retry later, or run the same command manually to troubleshoot.
    echo.
    pause
    exit /b 1
)
echo [OK] Chromium install command finished.

echo.
echo [4/4] Verifying installed browser...
set "FOUND="
for /d %%D in ("%LOCALAPPDATA%\ms-playwright\chromium-*") do set "FOUND=%%D"
if defined FOUND (
    echo [SUCCESS] Verification passed. Chromium found at:
    echo           %FOUND%
    echo.
    echo Dynamic rendering is now available. Restart the backend to take effect.
) else (
    echo [FAIL] No Chromium directory found under %LOCALAPPDATA%\ms-playwright.
    echo   Install returned success but files may not be on disk. Check disk space and retry.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Install complete. Playwright dynamic rendering mode is ready.
echo ============================================================
echo.
pause
endlocal