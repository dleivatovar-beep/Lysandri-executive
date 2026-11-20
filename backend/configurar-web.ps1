[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'busqueda.ps1')

Write-Host 'Configura la clave de tu cuenta gratuita Researcher de Tavily.'
Write-Host 'La clave se escribira de forma oculta y se guardara cifrada para tu usuario de Windows.'
$segura = Read-Host 'Clave de Tavily (oculta)' -AsSecureString
$credencial = New-Object System.Management.Automation.PSCredential('Tavily Search', $segura)
$clave = $credencial.GetNetworkCredential().Password
if ([string]::IsNullOrWhiteSpace($clave) -or $clave -match '\s') {
    throw 'La clave esta vacia o contiene espacios.'
}

$consulta = @{
    query = 'Lysandri Executive'
    search_depth = 'basic'
    max_results = 1
    language = 'es'
    topic = 'general'
    auto_parameters = $false
    include_answer = $false
    include_raw_content = $false
    include_images = $false
} | ConvertTo-Json -Compress

Write-Host 'Se realizara una busqueda basica para validar la clave (1 credito).'
try {
    $respuesta = Invoke-RestMethod 'https://api.tavily.com/search' -Method Post -ContentType 'application/json' -Headers @{Authorization = "Bearer $clave"} -Body $consulta -TimeoutSec 15 -ErrorAction Stop
    if ($null -eq $respuesta.results) { throw 'Respuesta inesperada.' }
} catch {
    throw 'Tavily no valido la conexion. Revisa tu clave, los creditos disponibles de tu plan gratuito y la conexion a internet.'
} finally {
    $clave = $null
}

$archivo = Get-BusquedaConfigPath
$carpeta = Split-Path -Parent $archivo
New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
$temporal = Join-Path $carpeta ('busqueda-tavily-' + [guid]::NewGuid().ToString('N') + '.xml')
try {
    $credencial | Export-Clixml -LiteralPath $temporal -Encoding UTF8
    if (Test-Path -LiteralPath $archivo) {
        [IO.File]::Replace($temporal, $archivo, $null)
    } else {
        [IO.File]::Move($temporal, $archivo)
    }
} finally {
    if (Test-Path -LiteralPath $temporal) { Remove-Item -LiteralPath $temporal -Force }
}

Write-Host 'Clave validada y guardada para tu usuario. La busqueda web se cargara al iniciar el proyecto.'
Write-Host 'Reinicia npm run dev para aplicar la conexion.'
