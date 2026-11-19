param(
    [string]$Api = 'http://127.0.0.1:8080',
    [string]$Archivo = (Join-Path $PSScriptRoot '..\ai-service\data\docs\Globalización de Mercados y Antropología del Consumidor.md'),
    [Nullable[long]]$Programa = $null
)
$ErrorActionPreference = 'Stop'
$Api = $Api.TrimEnd('/')
if (-not (Test-Path -LiteralPath $Archivo -PathType Leaf)) { throw 'No se encontró el sílabo.' }
$correo = Read-Host 'Correo de ADMIN de este backend'
$segura = Read-Host 'Contraseña (no se mostrará)' -AsSecureString
$credencial = New-Object System.Management.Automation.PSCredential($correo, $segura)
try {
    $json = @{email=$correo;password=$credencial.GetNetworkCredential().Password} | ConvertTo-Json
    $sesion = Invoke-RestMethod -Method Post -Uri "$Api/api/v1/auth/login" -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 30
    if ($sesion.rol -ne 'ADMIN' -or -not $sesion.token) { throw 'La carga requiere una cuenta ADMIN.' }
    $cuerpo = @{
        titulo='Globalización de Mercados y Antropología del Consumidor'
        version='2'
        estado='PROPUESTA'
        idPrograma=$Programa
        contenido=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $Archivo), [Text.Encoding]::UTF8)
    } | ConvertTo-Json -Depth 5
    $resultado = Invoke-RestMethod -Method Post -Uri "$Api/api/v1/admin/conocimiento" -Headers @{Authorization="Bearer $($sesion.token)"} -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($cuerpo)) -TimeoutSec 900
    $resultado | Select-Object idDocumento,fragmentos,checksum,version | Format-List
    Write-Host 'Documento cargado como PROPUESTA. Guarda el idDocumento para la evaluación.'
}
finally { $json=$null; $cuerpo=$null; $sesion=$null; $credencial=$null; $segura=$null }
