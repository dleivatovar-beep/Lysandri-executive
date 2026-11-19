[CmdletBinding()]
param([switch]$Local)
$ErrorActionPreference = 'Stop'
$archivo = Join-Path $env:LOCALAPPDATA 'Lysandri\correo.xml'
if ($Local) {
    $datos = [pscustomobject]@{Modo='local'}
} else {
    Write-Host 'Use una contrasena de aplicacion de Google; no su contrasena habitual.'
    $correo = (Read-Host 'Gmail que enviara los mensajes').Trim()
    if ($correo -notmatch '^[^\s@]+@gmail\.com$') { throw 'Introduzca una direccion @gmail.com valida.' }
    $secreto = Read-Host 'Contrasena de aplicacion (oculta)' -AsSecureString
    $credencial = New-Object System.Management.Automation.PSCredential($correo, $secreto)
    $clave = $credencial.GetNetworkCredential().Password -replace '\s',''
    if ($clave -notmatch '^[A-Za-z]{16}$') { throw 'La contrasena de aplicacion de Google debe tener 16 letras. No use la contrasena de su cuenta.' }
    $segura = ConvertTo-SecureString $clave -AsPlainText -Force
    $credencial = New-Object System.Management.Automation.PSCredential($correo, $segura)
    $clave = $null
    $datos = [pscustomobject]@{Modo='gmail'; Credencial=$credencial}
}
$carpeta = Split-Path -Parent $archivo
New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
$temporal = Join-Path $carpeta ('correo-' + [guid]::NewGuid().ToString('N') + '.xml')
try {
    $datos | Export-Clixml -LiteralPath $temporal -Encoding UTF8
    Move-Item -LiteralPath $temporal -Destination $archivo -Force
} finally {
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Force }
}
Write-Host 'Configuracion guardada para su usuario de Windows. La clave esta cifrada y no esta en el repositorio.'
Write-Host 'Detenga npm run dev con Ctrl+C y vuelva a iniciarlo para aplicar el cambio.'
Write-Host 'Gmail enviara todos los correos de la plataforma; el buzon local ya no recibira esos mensajes.'
