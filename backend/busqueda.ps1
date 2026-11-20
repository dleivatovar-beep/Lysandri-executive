function Get-BusquedaConfigPath {
    return Join-Path $env:LOCALAPPDATA 'Lysandri\busqueda-tavily.xml'
}

function Import-Busqueda {
    if (-not [string]::IsNullOrWhiteSpace($env:TAVILY_API_KEY)) { return }
    $archivo = Get-BusquedaConfigPath
    if (-not (Test-Path -LiteralPath $archivo)) { return }
    try {
        $credencial = Import-Clixml -LiteralPath $archivo -ErrorAction Stop
        if ($credencial -isnot [System.Management.Automation.PSCredential] -or $credencial.UserName -ne 'Tavily Search') {
            throw 'Formato de credencial invalido.'
        }
        $env:TAVILY_API_KEY = $credencial.GetNetworkCredential().Password
    } catch {
        Write-Warning 'No se pudo leer la clave de busqueda web. Ejecute npm run configurar:web con su usuario de Windows.'
    }
}
