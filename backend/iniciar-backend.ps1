<#
.SYNOPSIS
    Script reproducible y estricto de arranque seguro del backend Lysandri Executive.
.DESCRIPTION
    Verifica los requisitos previos de Java, JAR, variables de entorno y secretos,
    resuelve y valida MOODLE_URL, comprueba disponibilidad e identidad autenticada
    de DataSource y Flyway en la base autorizada en 127.0.0.1:5434, y detecta
    conflictos en el puerto HTTP antes de arrancar.
    RESTRICCIÓN: Nunca conecta a la base original (puerto 5432), no sustituye
    silenciosamente el clon, no imprime secretos ni crea/reinicializa clones persistentes.
#>
[CmdletBinding()]
param(
    [string]$Perfil = "clone",
    [int]$HttpPort = 8080,
    [string]$CloneHost = "127.0.0.1",
    [int]$ClonePort = 5434,
    [string]$CloneDatabase = "lysandri_clone_db",
    [switch]$SoloVerificar,
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

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "INICIO REPRODUCIBLE Y SEGURO: BACKEND LYSANDRI EXECUTIVE" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

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
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
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

# Verificar contraseñas obligatorias del clon
$cloneAppPass = $env:CLONE_APP_PASSWORD
if ([string]::IsNullOrWhiteSpace($cloneAppPass)) {
    Stop-Script "Falta la variable obligatoria CLONE_APP_PASSWORD. Definala en el entorno o en .env.local." 1
}

$cloneAdminPass = $env:CLONE_ADMIN_PASSWORD
if ([string]::IsNullOrWhiteSpace($cloneAdminPass)) {
    Stop-Script "Falta la variable obligatoria CLONE_ADMIN_PASSWORD. Definala en el entorno o en .env.local." 1
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

# 5. Verificación Estricta de Disponibilidad e Identidad Autenticada de PostgreSQL
Write-Host "[5/7] Verificando PostgreSQL autorizado en $CloneHost`:$ClonePort ($CloneDatabase)..." -ForegroundColor Yellow

# A. Restricción absoluta contra puerto 5432 o base de producción original
if ($ClonePort -eq 5432 -or $CloneDatabase -eq "lysandri_db" -or $CloneHost -eq "lysandri-postgres") {
    Stop-Script "VIOLACION DE SEGURIDAD: El destino especificado ($CloneHost`:$ClonePort/$CloneDatabase) corresponde a la base de datos original de produccion. Solo se permite conectar a bases clon autorizadas." 1
}

# B. Comprobar que el nombre de la base corresponde a un clon autorizado
if (-not ($CloneDatabase -eq "lysandri_clone_db" -or $CloneDatabase.StartsWith("lysandri_clone_"))) {
    Stop-Script "Base de datos no autorizada para perfil clone: '$CloneDatabase'. Solo se admiten 'lysandri_clone_db' o bases clon autorizadas." 4
}

# C. Verificar conectividad TCP al destino exacto
$tcpTest = Test-NetConnection -ComputerName $CloneHost -Port $ClonePort -WarningAction SilentlyContinue
if (-not $tcpTest.TcpTestSucceeded) {
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

$identityVerified = $false
try {
    Write-Host "  -> Verificando identidad del destino exacto ($CloneHost`:$ClonePort/$CloneDatabase) con psql..." -ForegroundColor DarkGray
    
    # 1. Verificar DataSource user (lysandri_app)
    $env:PGPASSWORD = $cloneAppPass
    $appResult = & $psqlPath -h $CloneHost -p $ClonePort -U lysandri_app -d $CloneDatabase -w -t -A -c "SELECT current_database() || '|' || current_user;" 2>&1
    $appExitCode = $LASTEXITCODE

    if ($appExitCode -ne 0 -or $appResult -ne "$CloneDatabase|lysandri_app") {
        Stop-Script "Fallo de autenticacion o identidad para usuario DataSource 'lysandri_app' en $CloneHost`:$ClonePort/$CloneDatabase." 4
    }

    # 2. Verificar Flyway user (lysandri_admin)
    $env:PGPASSWORD = $cloneAdminPass
    $adminResult = & $psqlPath -h $CloneHost -p $ClonePort -U lysandri_admin -d $CloneDatabase -w -t -A -c "SELECT current_database() || '|' || current_user;" 2>&1
    $adminExitCode = $LASTEXITCODE

    if ($adminExitCode -ne 0 -or $adminResult -ne "$CloneDatabase|lysandri_admin") {
        Stop-Script "Fallo de autenticacion o identidad para usuario Flyway 'lysandri_admin' en $CloneHost`:$ClonePort/$CloneDatabase." 4
    }

    Write-Host "  -> Identidad y autenticacion DataSource ('lysandri_app') confirmada" -ForegroundColor Green
    Write-Host "  -> Identidad y autenticacion Flyway ('lysandri_admin') confirmada" -ForegroundColor Green
    $identityVerified = $true

}
finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}

if (-not $identityVerified) {
    Stop-Script "No se pudo verificar la identidad y autenticacion de la base de datos de destino." 4
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
$env:SPRING_DATASOURCE_USERNAME = "lysandri_app"
$env:SPRING_FLYWAY_USER = "lysandri_admin"
$env:SPRING_DATASOURCE_PASSWORD = $cloneAppPass
$env:SPRING_FLYWAY_PASSWORD = $cloneAdminPass
$env:ACTIVATION_ENCRYPTION_KEY = $activationKey

$javaArgs = @(
    "-Dspring.profiles.active=$Perfil",
    "-Dserver.port=$HttpPort",
    "-Dspring.datasource.url=jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase",
    "-Dspring.datasource.username=lysandri_app",
    "-Dspring.flyway.url=jdbc:postgresql://$CloneHost`:$ClonePort/$CloneDatabase",
    "-Dspring.flyway.user=lysandri_admin",
    "-Dmoodle.url=$resolvedMoodleUrl",
    "-jar", $jarPath
)

if ($env:TEST_CAPTURE_JAVA_ARGS_FILE) {
    Set-Content -LiteralPath $env:TEST_CAPTURE_JAVA_ARGS_FILE -Value ($javaArgs -join "`n")
    Write-Host "  -> Argumentos de Java capturados para prueba en: $env:TEST_CAPTURE_JAVA_ARGS_FILE" -ForegroundColor Cyan
    exit 0
}

& java @javaArgs
exit $LASTEXITCODE
