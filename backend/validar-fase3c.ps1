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
    # Testcontainers creates fresh databases with random ports and ephemeral credentials.
    # No datasource environment variable is used by the PostgreSQL integration tests.
    $logPath = Join-Path $PSScriptRoot 'fase3c-verify.log'
    # Merge native stderr inside cmd.exe so Windows PowerShell does not treat
    # ordinary Java warnings as terminating NativeCommandError records.
    $mavenResult = & {
        $PSNativeCommandUseErrorActionPreference = $false
        & $env:ComSpec /d /c '.\mvnw.cmd clean verify 2>&1' |
            Tee-Object -FilePath $logPath | Out-Host
        $LASTEXITCODE
    }
    Write-Host "Resultado Maven: $mavenResult. Registro: $logPath"
    if ($mavenResult -ne 0) { throw 'La validacion fallo. Conserva el registro para diagnostico.' }
} finally {
    Pop-Location
}
