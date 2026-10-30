<#
.SYNOPSIS
    Script reproducible de arranque seguro del backend Lysandri Executive desde la raíz del proyecto.
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

$backendScript = Join-Path $PSScriptRoot "backend/iniciar-backend.ps1"
if (-not (Test-Path -LiteralPath $backendScript)) {
    Write-Error "No se encontro el script backend/iniciar-backend.ps1"
    exit 1
}

$params = @{
    Perfil = $Perfil
    HttpPort = $HttpPort
    CloneHost = $CloneHost
    ClonePort = $ClonePort
    CloneDatabase = $CloneDatabase
}
if ($SoloVerificar) {
    $params["SoloVerificar"] = $true
}
if ($ForzarHerramientaAusente) {
    $params["ForzarHerramientaAusente"] = $true
}

& $backendScript @params
exit $LASTEXITCODE
