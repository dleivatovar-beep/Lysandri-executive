param(
    [string]$EvidenceDir = "F:\Lysandri-executive\backend\evidencias_cierre\ejecucion_20261027_112000"
)

$ErrorActionPreference = "Stop"

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "GENERANDO PAQUETE DE EVIDENCIAS EN: $EvidenceDir" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$surefireDest = Join-Path $EvidenceDir "surefire-reports"
New-Item -ItemType Directory -Force -Path $surefireDest | Out-Null

# 1. Copiar reportes XML originales de Surefire
Write-Host "[1/6] Copiando reportes XML de Surefire (233 pruebas)..." -ForegroundColor Yellow
Get-ChildItem -Path "F:\Lysandri-executive\backend\target\surefire-reports" -Filter "*.xml" | Copy-Item -Destination $surefireDest -Force

# 2. Registrar hash SHA-256 del artefacto JAR final
Write-Host "[2/6] Calculando y registrando SHA-256 del JAR ejecutable..." -ForegroundColor Yellow
$jarPath = "F:\Lysandri-executive\backend\target\lysandri-backend-1.0.0-SNAPSHOT.jar"
$jarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
$hashContent = @"
Artefacto: lysandri-backend-1.0.0-SNAPSHOT.jar
Algoritmo: SHA-256
Hash: $jarHash
Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
Ruta: $jarPath
"@
Set-Content -LiteralPath (Join-Path $EvidenceDir "sha256_jar.txt") -Value $hashContent

# 3. Copiar bitácoras de pruebas Maven si existen
Write-Host "[3/6] Respaldando bitácoras de ejecución de pruebas Maven..." -ForegroundColor Yellow
if (Test-Path "F:\Lysandri-executive\backend\maven_unit_tests.log") {
    Copy-Item -Path "F:\Lysandri-executive\backend\maven_unit_tests.log" -Destination (Join-Path $EvidenceDir "resultado_pruebas_unitarias.log") -Force
}
if (Test-Path "F:\Lysandri-executive\backend\maven_test_output.log") {
    Copy-Item -Path "F:\Lysandri-executive\backend\maven_test_output.log" -Destination (Join-Path $EvidenceDir "resultado_pruebas_completas_docker.log") -Force
}

# 4. Ejecutar batería de pruebas de comportamiento de scripts PowerShell
Write-Host "[4/6] Ejecutando batería de pruebas de comportamiento de scripts (19 casos)..." -ForegroundColor Yellow
$scriptsLog = Join-Path $EvidenceDir "resultado_pruebas_scripts.log"
$p = Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -File `"F:\Lysandri-executive\backend\test-scripts-behavior.ps1`"" -RedirectStandardOutput $scriptsLog -RedirectStandardError (Join-Path $EvidenceDir "scripts_stderr.log") -Wait -PassThru -NoNewWindow
Write-Host "  -> Batería de scripts finalizada con código de salida: $($p.ExitCode)" -ForegroundColor Green

# 5. Ejecutar verificación en vivo del JAR real contra PostgreSQL desechable y SMTP local
Write-Host "[5/6] Ejecutando arranque real del JAR en vivo (PostgreSQL desechable + Mock SMTP + Auto-processing + Restart)..." -ForegroundColor Yellow
$jarLiveLog = Join-Path $EvidenceDir "resultado_arranque_jar_real.log"
$pj = Start-Process -FilePath "python" -ArgumentList "`"F:\Lysandri-executive\backend\run-live-jar-test.py`"" -RedirectStandardOutput $jarLiveLog -RedirectStandardError (Join-Path $EvidenceDir "jar_live_stderr.log") -Wait -PassThru -NoNewWindow
Write-Host "  -> Verificación en vivo del JAR finalizada con código de salida: $($pj.ExitCode)" -ForegroundColor Green

# 6. Copiar Guía de Operación y Generar Resumen Deduplicado
Write-Host "[6/6] Generando resumen deduplicado de resultados reales..." -ForegroundColor Yellow
Copy-Item -Path "F:\Lysandri-executive\GUIA_OPERACION.md" -Destination (Join-Path $EvidenceDir "GUIA_OPERACION.md") -Force
& python "F:\Lysandri-executive\backend\generate_deduplicated_summary.py" $EvidenceDir

Write-Host "================================================================================" -ForegroundColor Green
Write-Host "PAQUETE DE EVIDENCIAS GENERADO EXITOSAMENTE EN: $EvidenceDir" -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
