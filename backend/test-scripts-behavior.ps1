# Test suite for PowerShell startup script behavior (Requerimientos 3, 4 y 5)
$ErrorActionPreference = "Continue"

$backendScript = Join-Path $PSScriptRoot "iniciar-backend.ps1"
$rootScript = Join-Path (Split-Path -Parent $PSScriptRoot) "iniciar-backend.ps1"
$mockPsqlPath = Join-Path $PSScriptRoot "mock_psql.ps1"

$script:testResults = [System.Collections.ArrayList]::new()

function Invoke-TestCase {
    param(
        [string]$Name,
        [scriptblock]$Setup,
        [string]$AdditionalArgs,
        [int]$ExpectedExitCode,
        [int[]]$ListenPorts = @(),
        [string]$TargetScript = $backendScript,
        [scriptblock]$PostValidation = $null
    )

    # Save original environment state
    $origAppPass = $env:CLONE_APP_PASSWORD
    $origAdminPass = $env:CLONE_ADMIN_PASSWORD
    $origMoodle = $env:MOODLE_URL
    $origJwt = $env:JWT_SECRET
    $origActivationKey = $env:ACTIVATION_ENCRYPTION_KEY
    $origMockPath = $env:TEST_PSQL_MOCK_PATH
    $origMockBehavior = $env:TEST_MOCK_PSQL_BEHAVIOR
    $origMockDb = $env:MOCK_DB
    $origDisableTool = $env:DISABLE_VERIFICATION_TOOL
    $origCaptureArgs = $env:TEST_CAPTURE_JAVA_ARGS_FILE

    $listeners = [System.Collections.ArrayList]::new()

    try {
        foreach ($p in $ListenPorts) {
            $l = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $p)
            $l.Start()
            [void]$listeners.Add($l)
        }

        & $Setup

        $argList = "-ExecutionPolicy Bypass -File `"$TargetScript`" $AdditionalArgs"
        $proc = Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Wait -PassThru -NoNewWindow
        $actualExitCode = $proc.ExitCode

        $postPassed = $true
        $postError = ""
        if ($PostValidation) {
            try {
                $postPassed = & $PostValidation
                if ($postPassed -ne $true) {
                    $postError = "PostValidation returned false"
                }
            } catch {
                $postPassed = $false
                $postError = $_.Exception.Message
            }
        }

        $passed = ($actualExitCode -eq $ExpectedExitCode) -and ($postPassed -eq $true)
        $status = if ($passed) { "PASS" } else { "FAIL" }
        $color = if ($passed) { "Green" } else { "Red" }

        [void]$script:testResults.Add([PSCustomObject]@{
            Test = $Name
            Expected = $ExpectedExitCode
            Actual = $actualExitCode
            PostValidation = if ($PostValidation) { if ($postPassed) { "OK" } else { "FAILED ($postError)" } } else { "N/A" }
            Status = $status
        })

        $extraMsg = if ($postError) { " -> Error: $postError" } else { "" }
        Write-Host "[$status] $Name (Expected: $ExpectedExitCode, Actual: $actualExitCode)$extraMsg" -ForegroundColor $color
    } finally {
        foreach ($l in $listeners) {
            $l.Stop()
        }
        $env:CLONE_APP_PASSWORD = $origAppPass
        $env:CLONE_ADMIN_PASSWORD = $origAdminPass
        $env:MOODLE_URL = $origMoodle
        $env:JWT_SECRET = $origJwt
        $env:ACTIVATION_ENCRYPTION_KEY = $origActivationKey
        $env:TEST_PSQL_MOCK_PATH = $origMockPath
        $env:TEST_MOCK_PSQL_BEHAVIOR = $origMockBehavior
        $env:MOCK_DB = $origMockDb
        $env:DISABLE_VERIFICATION_TOOL = $origDisableTool
        $env:TEST_CAPTURE_JAVA_ARGS_FILE = $origCaptureArgs
    }
}

Write-Host "`n================================================================================" -ForegroundColor Cyan
Write-Host "EJECUCION DE BATERIA DE PRUEBAS DE SCRIPTS POWERSHELL (HALLAZGO 3 Y 5)" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# 1. MOODLE_URL ausente -> fallback http://localhost:8081 (Exit 0)
Invoke-TestCase -Name "1. MOODLE_URL ausente (usa default http://localhost:8081)" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 0

# 2. MOODLE_URL vacia -> fallback http://localhost:8081 (Exit 0)
Invoke-TestCase -Name "2. MOODLE_URL vacia (usa default http://localhost:8081)" -Setup {
    $env:MOODLE_URL = ""
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 0

# 3. MOODLE_URL personalizada valida (Exit 0)
Invoke-TestCase -Name "3. MOODLE_URL personalizada valida (http://localhost:8085)" -Setup {
    $env:MOODLE_URL = "http://localhost:8085"
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 0

# 4. MOODLE_URL invalida -> rechazada (Exit 1)
Invoke-TestCase -Name "4. MOODLE_URL invalida (ftp://bad-url)" -Setup {
    $env:MOODLE_URL = "ftp://bad-url"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ExpectedExitCode 1

# 5. MOODLE_URL conflicto de puerto con HttpPort (Exit 1)
Invoke-TestCase -Name "5. MOODLE_URL conflicto de puerto con HttpPort" -Setup {
    $env:MOODLE_URL = "http://localhost:18088"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ExpectedExitCode 1

# 6. Destino original rechazado: Puerto prohibido 5432 (Exit 1)
Invoke-TestCase -Name "6. Destino original rechazado: Puerto prohibido 5432" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
} -AdditionalArgs "-SoloVerificar -ClonePort 5432 -HttpPort 18088" -ExpectedExitCode 1

# 7. Destino original rechazado: Base de datos original lysandri_db (Exit 1)
Invoke-TestCase -Name "7. Destino original rechazado: Base original lysandri_db" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -CloneDatabase lysandri_db -HttpPort 18088" -ExpectedExitCode 1

# 8. Destino original rechazado: Host lysandri-postgres (Exit 1)
Invoke-TestCase -Name "8. Destino original rechazado: Host lysandri-postgres" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
} -AdditionalArgs "-SoloVerificar -CloneHost lysandri-postgres -ClonePort 15434 -HttpPort 18088" -ExpectedExitCode 1

# 9. Fallo de conexion TCP a puerto no activo (Exit 2)
Invoke-TestCase -Name "9. Fallo de conexion TCP a host:puerto no activo" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
} -AdditionalArgs "-SoloVerificar -ClonePort 15499 -HttpPort 18088" -ListenPorts @() -ExpectedExitCode 2

# 10. Puerto HTTP ocupado (Exit 3)
Invoke-TestCase -Name "10. Puerto HTTP ocupado detecta conflicto" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18090" -ListenPorts @(15434, 18090) -ExpectedExitCode 3

# 11. Herramienta de verificacion ausente (-ForzarHerramientaAusente) (Exit 4)
Invoke-TestCase -Name "11. Herramienta de verificacion ausente (-ForzarHerramientaAusente)" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
} -AdditionalArgs "-SoloVerificar -ForzarHerramientaAusente -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 4

# 12. Base de datos no autorizada por nombre (Exit 4)
Invoke-TestCase -Name "12. Base incorrecta: Nombre no autorizado antes de conectar" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -CloneDatabase base_ajena_test -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 4

# 13. Base incorrecta: Servidor responde con base no coincidente (Exit 4)
Invoke-TestCase -Name "13. Base incorrecta: Servidor responde con base distinta" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "WRONG_DB"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 4

# 14. Host o puerto diferente: Servidor responde con otro host/usuario (Exit 4)
Invoke-TestCase -Name "14. Servidor accesible pero identidad ajena" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "DIFFERENT_SERVER"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 4

# 15. Credenciales incorrectas de aplicacion (lysandri_app) (Exit 4)
Invoke-TestCase -Name "15. Credenciales incorrectas para usuario lysandri_app" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "FAIL_APP"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 4

# 16. Credenciales incorrectas de Flyway (lysandri_admin) (Exit 4)
Invoke-TestCase -Name "16. Credenciales incorrectas para usuario lysandri_admin" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "FAIL_FLYWAY"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 4

# 17. Destino correcto verificado (-SoloVerificar) (Exit 0)
Invoke-TestCase -Name "17. Destino correcto y verificado (-SoloVerificar)" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
} -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 0

# 18. Verificacion del destino realmente pasado a Java sin secretos expuestos (Exit 0)
$tempArgsFile = Join-Path $PSScriptRoot "captured_java_args.tmp"
Invoke-TestCase -Name "18. Verificacion de argumentos Java y proteccion de secretos" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
    $env:TEST_CAPTURE_JAVA_ARGS_FILE = $tempArgsFile
    Remove-Item -LiteralPath $tempArgsFile -ErrorAction SilentlyContinue
} -AdditionalArgs "-ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 0 -PostValidation {
    if (-not (Test-Path -LiteralPath $tempArgsFile)) {
        throw "El archivo de argumentos capturados no fue generado."
    }
    $argsContent = Get-Content -LiteralPath $tempArgsFile -Raw
    Remove-Item -LiteralPath $tempArgsFile -ErrorAction SilentlyContinue

    # Verificar que contiene la URL correcta
    if (-not ($argsContent -match "spring\.datasource\.url=jdbc:postgresql://127\.0\.0\.1:15434/lysandri_clone_db")) {
        throw "URL de DataSource no coincide con el destino verificado."
    }
    if (-not ($argsContent -match "spring\.flyway\.url=jdbc:postgresql://127\.0\.0\.1:15434/lysandri_clone_db")) {
        throw "URL de Flyway no coincide con el destino verificado."
    }
    # Verificar usuarios
    if (-not ($argsContent -match "spring\.datasource\.username=lysandri_app")) {
        throw "Usuario de DataSource incorrecto."
    }
    if (-not ($argsContent -match "spring\.flyway\.user=lysandri_admin")) {
        throw "Usuario de Flyway incorrecto."
    }
    # Verificar que NO hay passwords o nombres de variables de contrasena en los argumentos
    if ($argsContent -match "-D.*password" -or $argsContent -match "CLONE_APP_PASSWORD" -or $argsContent -match "CLONE_ADMIN_PASSWORD" -or $argsContent -match "ACTIVATION_ENCRYPTION_KEY") {
        throw "Se detectaron secretos o contrasenas en los argumentos visibles de Java!"
    }
    return $true
}

# 19. Ejecucion delegada desde script raiz (Exit 0)
Invoke-TestCase -Name "19. Ejecucion delegada desde script raiz (iniciar-backend.ps1)" -Setup {
    Remove-Item env:MOODLE_URL -ErrorAction SilentlyContinue
    $env:TEST_PSQL_MOCK_PATH = $mockPsqlPath
    $env:TEST_MOCK_PSQL_BEHAVIOR = "OK"
} -TargetScript $rootScript -AdditionalArgs "-SoloVerificar -ClonePort 15434 -HttpPort 18088" -ListenPorts @(15434) -ExpectedExitCode 0

# Resumen Final
Write-Host "`n================================================================================" -ForegroundColor Cyan
Write-Host "RESUMEN FINAL DE PRUEBAS DE SCRIPTS POWERSHELL" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
$failedCount = @($script:testResults | Where-Object { $_.Status -eq "FAIL" }).Count
$totalCount = $script:testResults.Count

Write-Host "Total pruebas ejecutadas: $totalCount"
Write-Host "Pruebas exitosas (PASS):  $($totalCount - $failedCount)"
Write-Host "Pruebas fallidas (FAIL):  $failedCount"

if ($failedCount -eq 0) {
    Write-Host "`nTODAS LAS $totalCount PRUEBAS DEL SCRIPT PASARON EXITOSAMENTE.`n" -ForegroundColor Green
    exit 0
} else {
    Write-Error "`n$failedCount pruebas del script fallaron.`n"
    exit 1
}
