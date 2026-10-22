@echo off
title ARGUS Demo Launcher
cd /d "%~dp0"

echo ==========================================================
echo   ARGUS - ONE command, ONE url, fully live (no Docker)
echo ==========================================================
echo.

rem --- 1. Build the React UI (only if not built yet) ---
if not exist "%~dp0frontend\dist\index.html" (
  echo [1/3] Building frontend...
  pushd "%~dp0frontend"
  call npm run build
  if errorlevel 1 (
    echo FRONTEND BUILD FAILED
    popd
    pause
    exit /b 1
  )
  popd
) else (
  echo [1/3] Frontend already built - skipping.
)

rem --- 2. Copy the built UI into the backend so it serves it ---
echo [2/3] Copying UI into backend\src\main\resources\static ...
robocopy "%~dp0frontend\dist" "%~dp0backend\src\main\resources\static" /MIR /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
  echo COPY FAILED
  pause
  exit /b 1
)

rem --- 3. Start the backend (Spring Boot + in-memory H2, autopilot ON) ---
echo [3/3] Starting backend on http://localhost:8080 ...
start "ARGUS (backend + UI) :8080" cmd /k "cd /d %~dp0backend && mvn spring-boot:run -Dspring-boot.run.profiles=local"

echo Waiting for server...
:wait
timeout /t 3 /nobreak >nul
curl -sf http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 goto wait

echo Opening http://localhost:8080 ...
start "" http://localhost:8080

echo.
echo  ---------------------------------------------------
echo   LIVE demo is running at http://localhost:8080
echo.
echo   Login credentials:
echo     admin    / ChangeMe!Argus2026   (full access)
echo     analyst  / Analyst!Argus2026    (analyst access)
echo     viewer   / Viewer!Argus2026     (read-only)
echo.
echo   NOTE: data is real - it lives in the API server
echo   window you can see. Close that window to stop.
echo  ---------------------------------------------------
pause
