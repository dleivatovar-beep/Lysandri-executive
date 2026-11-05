$ErrorActionPreference = 'Stop'
$dockerCandidates = @(
    (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'),
    (Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin/docker.exe')
)
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$dockerExe = if ($dockerCommand) { $dockerCommand.Source } else {
    $dockerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $dockerExe) { throw 'No se encontro Docker Desktop. Inicialo antes de ejecutar esta validacion.' }
& $dockerExe info --format '{{.OSType}}'
if ($LASTEXITCODE -ne 0) { throw 'No se puede acceder a Docker Desktop desde esta terminal.' }

Push-Location $PSScriptRoot
try {
    # Validacion exclusiva de restauracion aislada en contenedor/volumen efimero nuevo.
    # Valida SHA-256 del respaldo verificado, restauracion limpia, migraciones Flyway V7.1-V25,
    # comparacion completa 100% de datos pre/post migracion, secuencias, claves foraneas y minimo privilegio.
    $logPath = Join-Path $PSScriptRoot 'clon-real-verify.log'
    $mavenResult = & {
        $PSNativeCommandUseErrorActionPreference = $false
        & $env:ComSpec /d /c '.\mvnw.cmd test -P real-clone -Dtest=IsolatedRestorationFullComparisonIntegrationTest 2>&1' |
            Tee-Object -FilePath $logPath | Out-Host
        $LASTEXITCODE
    }
    Write-Host "Resultado Maven Clon Real: $mavenResult. Registro: $logPath"
    if ($mavenResult -ne 0) { throw 'La validacion de clones reales fallo. Conserva el registro para diagnostico.' }
} finally {
    Pop-Location
}
