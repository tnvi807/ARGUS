<#
.SYNOPSIS
  ARGUS local demo launcher for PowerShell (no Docker).

.DESCRIPTION
  PowerShell twin of start-demo.bat: builds the React UI, embeds it in the Spring
  Boot backend and starts the server on http://localhost:8080 (in-memory H2,
  autopilot traffic ON).

  It calls npm.cmd explicitly instead of npm so it works even on machines where
  the execution policy blocks the npm.ps1 shim. If this script itself is blocked,
  run it with:

      powershell -ExecutionPolicy Bypass -File .\start-demo.ps1

.EXAMPLE
  .\start-demo.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

Write-Host "==========================================================="
Write-Host "  ARGUS - ONE command, ONE url, fully live (no Docker)"
Write-Host "==========================================================="
Write-Host ""

# --- 1. Build the React UI (only if not built yet) ---
if (-not (Test-Path (Join-Path $PSScriptRoot 'frontend\dist\index.html'))) {
    Write-Host "[1/3] Building frontend..."
    Push-Location (Join-Path $PSScriptRoot 'frontend')
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "FRONTEND BUILD FAILED" }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "[1/3] Frontend already built - skipping."
}

# --- 2. Copy the built UI into the backend so it serves it ---
Write-Host "[2/3] Copying UI into backend\src\main\resources\static ..."
robocopy (Join-Path $PSScriptRoot 'frontend\dist') `
         (Join-Path $PSScriptRoot 'backend\src\main\resources\static') `
         /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "COPY FAILED" }

# --- 3. Start the backend (Spring Boot + in-memory H2, autopilot ON) ---
Write-Host "[3/3] Starting backend on http://localhost:8080 ..."
$serverCmd = "cd /d `"$PSScriptRoot\backend`" && mvn spring-boot:run -Dspring-boot.run.profiles=local"
Start-Process -FilePath cmd.exe -ArgumentList '/k', $serverCmd -WindowTitle 'ARGUS (backend + UI) :8080'

Write-Host "Waiting for server..."
$healthy = $false
for ($i = 0; $i -lt 100 -and -not $healthy; $i++) {
    Start-Sleep -Seconds 3
    try {
        $response = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' `
            -UseBasicParsing -TimeoutSec 5
        $healthy = $response.StatusCode -eq 200
    } catch { }
}

if ($healthy) {
    Start-Process 'http://localhost:8080'
} else {
    Write-Warning "Server did not report healthy in time - check the backend window."
}

Write-Host ""
Write-Host "  ---------------------------------------------------"
Write-Host "   LIVE demo is running at http://localhost:8080"
Write-Host ""
Write-Host "   Login credentials:"
Write-Host "     admin    / ChangeMe!Argus2026   (full access)"
Write-Host "     analyst  / Analyst!Argus2026    (analyst access)"
Write-Host "     viewer   / Viewer!Argus2026     (read-only)"
Write-Host ""
Write-Host "   NOTE: data is real - it lives in the API server"
Write-Host "   window you opened. Close that window to stop."
Write-Host "  ---------------------------------------------------"
