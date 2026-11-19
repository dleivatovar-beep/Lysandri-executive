<#
.SYNOPSIS
    Script reproducible y estricto de arranque seguro del backend Lysandri Executive.
.DESCRIPTION
    Verifica los requisitos previos de Java, JAR, variables de entorno y secretos,
    resuelve y valida MOODLE_URL, comprueba disponibilidad e identidad autenticada
    de DataSource y Flyway en el clon autorizado. El acceso a lysandri_db requiere
    -UsarBaseOriginal, perfil original-dev y validación estricta del destino.
    No imprime secretos ni crea/reinicializa clones persistentes.
#>
[CmdletBinding()]
param(
    [string]$Perfil = "clone",
    [int]$HttpPort = 8080,
    [string]$CloneHost = "127.0.0.1",
    [int]$ClonePort = 5434,
    [string]$CloneDatabase = "lysandri_clone_db",
    [switch]$UsarBaseOriginal,
    [switch]$SoloVerificar,
    [switch]$SoloVerificarBaseDatos,
    [switch]$ForzarHerramientaAusente
)

function Stop-Script {
    param(
        [string]$Message,
        [int]$ExitCode = 1
    )
    Write-Host "`n[FATAL] $Message" -ForegroundColor Red
    [Console]::Error.WriteLine("ERROR: $Message")
    exit $ExitCode
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ($UsarBaseOriginal) {
    $rootEnv = Join-Path (Split-Path -Parent $scriptDir) ".env"
    if (Test-Path -LiteralPath $rootEnv) {
        Get-Content -LiteralPath $rootEnv | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $parts = $line.Split("=", 2)
                $name = $parts[0].Trim()
                $value = $parts[1].Trim().Trim('"').Trim("'")
                if (-not [System.Environment]::GetEnvironmentVariable($name)) {
                    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
                }
            }
        }
    }
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "INICIO REPRODUCIBLE Y SEGURO: BACKEND LYSANDRI EXECUTIVE" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

if (-not $SoloVerificarBaseDatos) {
    # 1. Comprobación de Java (Java 17 o superior)
    Write-Host "[1/7] Comprobando instalacion de Java..." -ForegroundColor Yellow
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        Stop-Script "Java no esta instalado o no se encuentra en el PATH. Se requiere Java 17 o superior." 1
    }

    $javaVersionOutput = & $env:ComSpec /d /c 'java -version 2>&1'
    Write-Host "  -> Java detectado: $($javaCmd.Source)" -ForegroundColor Green
    $versionMatch = [regex]::Match($javaVersionOutput, 'version "(?<ver>\d+)')
    if ($versionMatch.Success) {
        $majorVer = [int]$versionMatch.Groups['ver'].Value
        if ($majorVer -lt 17) {
            Stop-Script "Version de Java no soportada ($majorVer). Se requiere Java 17 o superior." 1
        }
    }
    else {
        Stop-Script "No se pudo determinar la version de Java." 1
    }

    # 2. Comprobación del artefacto JAR ejecutable
    Write-Host "[2/7] Comprobando artefacto JAR ejecutable..." -ForegroundColor Yellow
    $jarPath = Join-Path $scriptDir "target/lysandri-backend-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jarPath)) {
        # Probar ruta alternativa desde raiz
        $jarPath = Join-Path $scriptDir "backend/target/lysandri-backend-1.0.0-SNAPSHOT.jar"
    }

    if (-not (Test-Path -LiteralPath $jarPath)) {
        Stop-Script "No se encontro el archivo JAR en $jarPath. Ejecute primero 'mvnw package -DskipTests' para compilarlo." 1
    }

    $jarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
    Write-Host "  -> JAR encontrado: $(Split-Path -Leaf $jarPath)" -ForegroundColor Green
    Write-Host "  -> SHA-256 del JAR: $jarHash" -ForegroundColor DarkGray
}

# 3. Comprobación de Variables de Entorno y Secretos
Write-Host "[3/7] Comprobando variables de entorno y configuracion de seguridad..." -ForegroundColor Yellow

# Cargar .env.local si existe en la raiz o en backend
$envPaths = @(
    (Join-Path $scriptDir ".env.local"),
    (Join-Path (Split-Path -Parent $scriptDir) ".env.local")
)
foreach ($ep in $envPaths) {
    if (Test-Path -LiteralPath $ep) {
        Write-Host "  -> Leyendo variables de entorno desde: $ep" -ForegroundColor DarkGray
        Get-Content $ep | ForEach-Object {
            $line = $_.Trim()
            if ($line -and (-not $line.StartsWith("#")) -and ($line.Contains("="))) {
                $parts = $line.Split("=", 2)
                $name = $parts[0].Trim()
                $val = $parts[1].Trim()
                if (-not [System.Environment]::GetEnvironmentVariable($name)) {
                    [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
                }
            }
        }
        break
    }
}

. (Join-Path $scriptDir "correo.ps1")
Import-Correo

$dataSourceUser = "lysandri_app"
$flywayUser = "lysandri_admin"
$dataSourcePass = $env:CLONE_APP_PASSWORD
$flywayPass = $env:CLONE_ADMIN_PASSWORD

if ($UsarBaseOriginal) {
    if ($Perfil -notmatch '(^|,)original-dev(,|$)' -or $Perfil -match '(^|,)(clone|test|prod|production)(,|$)') {
        Stop-Script "El modo de base original requiere el perfil original-dev y no admite clone, test ni prod." 1
    }
    if ($env:DB_HOST -notin @("127.0.0.1", "localhost") -or $env:DB_PORT -ne "5432" -or $env:DB_NAME -ne "lysandri_db") {
        Stop-Script "La configuración de .env no identifica exactamente 127.0.0.1:5432/lysandri_db." 1
    }
    $dataSourceUser = $env:DB_APP_USER
    $flywayUser = $env:DB_ADMIN_USER
    $dataSourcePass = $env:DB_APP_PASSWORD
    $flywayPass = $env:DB_ADMIN_PASSWORD
    $CloneHost = "127.0.0.1"
    $ClonePort = 5432
    $CloneDatabase = "lysandri_db"
    $env:LYSANDRI_ALLOW_ORIGINAL_DB = "true"
    Write-Host "  -> Modo original-dev habilitado explícitamente para 127.0.0.1:5432/lysandri_db." -ForegroundColor Yellow
}

if ([string]::IsNullOrWhiteSpace($dataSourcePass) -or [string]::IsNullOrWhiteSpace($flywayPass) -or [string]::IsNullOrWhiteSpace($dataSourceUser) -or [string]::IsNullOrWhiteSpace($flywayUser)) {
    if ($UsarBaseOriginal) {
        Stop-Script "Faltan DB_APP_USER/DB_APP_PASSWORD o DB_ADMIN_USER/DB_ADMIN_PASSWORD en .env." 1
    }
    Stop-Script "Faltan CLONE_APP_PASSWORD o CLONE_ADMIN_PASSWORD en .env.local." 1
}

# Verificar clave obligatoria, externa y persistente para cifrado de activaciones (no se autogenera)
$activationKey = $env:ACTIVATION_ENCRYPTION_KEY
if ([string]::IsNullOrWhiteSpace($activationKey)) {
    Stop-Script "Falta la variable obligatoria ACTIVATION_ENCRYPTION_KEY (clave persistente exclusiva para activaciones). Definala en el entorno o en .env.local." 1
}
if ($activationKey.Length -lt 32) {
    Stop-Script "La variable ACTIVATION_ENCRYPTION_KEY tiene longitud insuficiente (minimo 32 caracteres / 256 bits)." 1
}

# Validar o generar JWT_SECRET estrictamente para el proceso local (sin imprimirlo)
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    Write-Host "  -> Generando JWT_SECRET temporal seguro de 512 bits para el proceso local..." -ForegroundColor Yellow
    $bytes = New-Object byte[] 64
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
}
else {
    if ($env:JWT_SECRET.Length -lt 32) {
        Stop-Script "La variable JWT_SECRET tiene longitud insuficiente (minimo 32 caracteres / 256 bits)." 1
    }
}
Write-Host "  -> Variables y secretos verificados correctamente (protegidos, nunca impresos)" -ForegroundColor Green

if (-not $SoloVerificarBaseDatos) {
    # 4. Resolución y Validación de MOODLE_URL
    Write-Host "[4/7] Resolviendo y validando MOODLE_URL..." -ForegroundColor Yellow
    $rawMoodleUrl = $env:MOODLE_URL
    if ([string]::IsNullOrWhiteSpace($rawMoodleUrl)) {
        $resolvedMoodleUrl = "http://localhost:8081"
    }
    else {
        $resolvedMoodleUrl = $rawMoodleUrl.Trim()
    }

    $uriResult = $null
    $isValidUri = [System.Uri]::TryCreate($resolvedMoodleUrl, [System.UriKind]::Absolute, [ref]$uriResult)
    if (-not $isValidUri -or ($uriResult.Scheme -ne "http" -and $uriResult.Scheme -ne "https")) {
        Stop-Script "MOODLE_URL invalida: '$resolvedMoodleUrl'. Debe ser una URL absoluta http o https." 1
    }

    $moodlePort = if ($uriResult.IsDefaultPort) {
        if ($uriResult.Scheme -eq "https") { 443 } else { 80 }
    }
    else {
        $uriResult.Port
    }

    if ($moodlePort -eq $HttpPort -and ($uriResult.Host -eq "localhost" -or $uriResult.Host -eq "127.0.0.1")) {
        Stop-Script "Conflicto de puerto: El puerto de MOODLE_URL ($moodlePort) coincide con el puerto HTTP del backend ($HttpPort)." 1
    }
    Write-Host "  -> MOODLE_URL resuelta: $resolvedMoodleUrl (puerto $moodlePort)" -ForegroundColor Green
}

# 5. Verificación Estricta de Disponibilidad e Identidad Autenticada de PostgreSQL
Write-Host "[5/7] Verificando PostgreSQL autorizado en $CloneHost`:$ClonePort ($CloneDatabase)..." -ForegroundColor Yellow

# A. Restricción absoluta contra puerto 5432 o base de producción original
if (-not $UsarBaseOriginal -and ($ClonePort -eq 5432 -or $CloneDatabase -eq "lysandri_db" -or $CloneHost -eq "lysandri-postgres")) {
    Stop-Script "VIOLACION DE SEGURIDAD: El destino especificado ($CloneHost`:$ClonePort/$CloneDatabase) corresponde a la base de datos original de produccion. Solo se permite conectar a bases clon autorizadas." 1
}

# B. Comprobar que el nombre de la base corresponde a un clon autorizado
if (-not $UsarBaseOriginal -and -not ($CloneDatabase -eq "lysandri_clone_db" -or $CloneDatabase.StartsWith("lysandri_clone_"))) {
    Stop-Script "Base de datos no autorizada para perfil clone: '$CloneDatabase'. Solo se admiten 'lysandri_clone_db' o bases clon autorizadas." 4
}

# C. Verificar conectividad TCP al destino exacto con socket directo
$tcpConnected = $false
try {
    $tcpSocket = New-Object System.Net.Sockets.TcpClient
    $tcpConnectTask = $tcpSocket.BeginConnect($CloneHost, $ClonePort, $null, $null)
    $tcpWaitSuccess = $tcpConnectTask.AsyncWaitHandle.WaitOne(2000, $false)
    if ($tcpWaitSuccess -and $tcpSocket.Connected) {
        $tcpSocket.EndConnect($tcpConnectTask)
        $tcpConnected = $true
    }
    $tcpSocket.Close()
} catch {
    $tcpConnected = $false
}
if (-not $tcpConnected) {
    Stop-Script "No hay conexion TCP en $CloneHost`:$ClonePort. Verifique que el servicio PostgreSQL este activo en dicho host y puerto." 2
}
Write-Host "  -> Conectividad TCP confirmada en $CloneHost`:$ClonePort" -ForegroundColor Green

# D. Comprobación mediante conexión autenticada de la identidad efectiva de DataSource y Flyway
$psqlCmd = if ($env:DISABLE_VERIFICATION_TOOL -eq "1" -or $ForzarHerramientaAusente) { $null } else {
    if ($env:TEST_PSQL_MOCK_PATH -and (Test-Path -LiteralPath $env:TEST_PSQL_MOCK_PATH)) {
        [PSCustomObject]@{ Source = $env:TEST_PSQL_MOCK_PATH }
    }
    else {
        Get-Command psql -ErrorAction SilentlyContinue
    }
}
$psqlPath = if ($psqlCmd) { $psqlCmd.Source } else {
    if ($env:DISABLE_VERIFICATION_TOOL -ne "1" -and -not $ForzarHerramientaAusente) {
        $found = Get-Item "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue | Select-Object -Last 1
        if ($found) { $found.FullName } else { $null }
    }
    else { $null }
}

if (-not $psqlPath) {
    Stop-Script "Herramienta de verificacion no disponible: No se encontro cliente psql para verificar inequivocamente el destino $CloneHost`:$ClonePort/$CloneDatabase. Docker no puede verificar inequivocamente este destino. Abortando con error estricto." 4
}

function Confirm-PostgresIdentity {
    param([string]$UserName, [string]$Password, [string]$Role)

    $env:PGPASSWORD = $Password
    $result = @(& $psqlPath -X -h $CloneHost -p $ClonePort -U $UserName -d $CloneDatabase -w -t -A -c "SELECT current_database() || '|' || current_user;" 2>&1)
    $resultCode = $LASTEXITCODE

    if ($resultCode -ne 0) {
        $detail = $result -join "`n"
        if ($detail -match 'the database system is (starting up|in recovery mode|shutting down|not yet accepting connections)|server closed the connection unexpectedly|connection refused|connection timed out|connection reset by peer') {
            Stop-Script "PostgreSQL todavia no admite conexiones autenticadas en $CloneHost`:$ClonePort/$CloneDatabase. Reintente en unos segundos." 2
        }
    }
    if ($resultCode -ne 0 -or $result.Count -ne 1 -or $result[0] -cne "$CloneDatabase|$UserName") {
        Stop-Script "Fallo de autenticacion o identidad para usuario $Role '$UserName' en $CloneHost`:$ClonePort/$CloneDatabase." 4
    }
    Write-Host "  -> Identidad y autenticacion $Role ('$UserName') confirmada" -ForegroundColor Green
}

$identityVerified = $false
try {
    Write-Host "  -> Verificando identidad del destino exacto ($CloneHost`:$ClonePort/$CloneDatabase) con psql..." -ForegroundColor DarkGray
    Confirm-PostgresIdentity -UserName $dataSourceUser -Password $dataSourcePass -Role "DataSource"
    Confirm-PostgresIdentity -UserName $flywayUser -Password $flywayPass -Role "Flyway"
    $identityVerified = $true
}
finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}

if (-not $identityVerified) {
    Stop-Script "No se pudo verificar la identidad y autenticacion de la base de datos de destino." 4
}

if ($SoloVerificarBaseDatos) {
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "VERIFICACION EXITOSA: Base de datos autenticada e identidad confirmada." -ForegroundColor Green
    Write-Host "================================================================================" -ForegroundColor Green
    exit 0
}

# 6. Detección de Conflictos en Puerto HTTP
Write-Host "[6/7] Verificando puerto HTTP $HttpPort..." -ForegroundColor Yellow
$httpConflict = Get-NetTCPConnection -LocalPort $HttpPort -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Listen' }
if ($httpConflict) {
    $procId = $httpConflict[0].OwningProcess
    $procName = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
    Stop-Script "Conflicto de puerto HTTP. El puerto $HttpPort ya esta en uso por el proceso PID $procId ($procName). Detengalo antes de iniciar." 3
}
Write-Host "  -> Puerto HTTP $HttpPort disponible" -ForegroundColor Green

# Si se solicito -SoloVerificar, finalizar con exito aqui sin arrancar Java
if ($SoloVerificar) {
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "VERIFICACION EXITOSA: Todas las comprobaciones completadas estrictamente." -ForegroundColor Green
    Write-Host "El entorno esta listo para arrancar." -ForegroundColor Green
    Write-Host "================================================================================" -ForegroundColor Green
    exit 0
}

# 7. Arranque del Backend con Perfil y Argumentos Sanitizados
Write-Host "[7/7] Iniciando backend Lysandri Executive con perfil '$Perfil'..." -ForegroundColor Cyan
Write-Host "  -> URL Backend: http://localhost:$HttpPort" -ForegroundColor Green
Write-Host "  -> Base de Datos: jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase" -ForegroundColor Green
Write-Host "  -> Moodle URL: $resolvedMoodleUrl" -ForegroundColor Green
Write-Host "  -> Presione Ctrl+C para detener el backend.`n" -ForegroundColor Yellow

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase"
$env:SPRING_FLYWAY_URL = "jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase"
$env:SPRING_DATASOURCE_USERNAME = $dataSourceUser
$env:SPRING_FLYWAY_USER = $flywayUser
$env:SPRING_DATASOURCE_PASSWORD = $dataSourcePass
$env:SPRING_FLYWAY_PASSWORD = $flywayPass
$env:ACTIVATION_ENCRYPTION_KEY = $activationKey

if (-not $env:APP_RECOVERY_TUNNEL_STATE_FILE) {
    $env:APP_RECOVERY_TUNNEL_STATE_FILE = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.tunnel-session.json'))
}

$javaArgs = @(
    "-Dspring.profiles.active=$Perfil",
    "-Dserver.port=$HttpPort",
    "-Dspring.datasource.url=jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase",
    "-Dspring.datasource.username=$dataSourceUser",
    "-Dspring.flyway.url=jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase",
    "-Dspring.flyway.user=$flywayUser",
    "-Dmoodle.url=$resolvedMoodleUrl",
    "-jar", $jarPath
)

if ($UsarBaseOriginal) {
    if (-not $env:APP_RECOVERY_TUNNEL_STATE_FILE) {
    $env:APP_RECOVERY_TUNNEL_STATE_FILE = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.tunnel-session.json'))
}

$javaArgs = @("-DLYSANDRI_ALLOW_ORIGINAL_DB=true") + $javaArgs
}

if ($env:TEST_CAPTURE_JAVA_ARGS_FILE) {
    Set-Content -LiteralPath $env:TEST_CAPTURE_JAVA_ARGS_FILE -Value ($javaArgs -join "`n")
    Write-Host "  -> Argumentos de Java capturados para prueba en: $env:TEST_CAPTURE_JAVA_ARGS_FILE" -ForegroundColor Cyan
    exit 0
}

$ollamaProyecto = $null
if ($env:LYSANDRI_OLLAMA_VULKAN -eq '1' -and -not $env:OLLAMA_URL -and
    $Perfil -match '(^|,)(original-dev|clone)(,|$)' -and $Perfil -notmatch '(^|,)(prod|production)(,|$)') {
    . (Join-Path $PSScriptRoot 'ollama-vulkan.ps1')
    $ollamaProyecto = Start-OllamaProyecto
    if ($ollamaProyecto) {
        $env:OLLAMA_URL = $ollamaProyecto.Url
        $env:OLLAMA_GPU_LAYERS = '-1'
        Write-Host '  -> Ollama local listo con Vulkan.' -ForegroundColor Green
    }
}

try {
    & java @javaArgs
    $codigoJava = $LASTEXITCODE
} finally {
    if ($ollamaProyecto) { Stop-OllamaProyecto $ollamaProyecto.Proceso }
}
exit $codigoJava
