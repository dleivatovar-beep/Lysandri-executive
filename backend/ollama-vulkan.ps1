function Stop-OllamaProyecto {
    param($Servicio)
    if ($null -ne $Servicio -and -not $Servicio.HasExited) {
        $Servicio.Kill()
        $Servicio.WaitForExit(3000) | Out-Null
    }
}

function Start-OllamaProyecto {
    $ejecutable = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
    if (-not (Test-Path -LiteralPath $ejecutable)) {
        Write-Warning 'Ollama no encontrado para Vulkan. Se usará la configuración habitual.'
        return
    }

    $servidor = $null
    try {
        $puertoLocal = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        $puertoLocal.Start()
        try { $puerto = $puertoLocal.LocalEndpoint.Port } finally { $puertoLocal.Stop() }
        $url = "http://127.0.0.1:$puerto"

        $inicio = [Diagnostics.ProcessStartInfo]::new($ejecutable, 'serve')
        $inicio.UseShellExecute = $false
        $inicio.CreateNoWindow = $true
        # Ollama recibe solo el entorno necesario para arrancar y ubicar los modelos.
        $inicio.EnvironmentVariables.Clear()
        foreach ($nombre in @('SystemRoot', 'Path', 'TEMP', 'TMP', 'USERPROFILE', 'LOCALAPPDATA',
                'APPDATA', 'ProgramFiles', 'ProgramFiles(x86)', 'OLLAMA_MODELS')) {
            $valor = [Environment]::GetEnvironmentVariable($nombre)
            if ($valor) { $inicio.EnvironmentVariables[$nombre] = $valor }
        }
        $inicio.EnvironmentVariables['OLLAMA_HOST'] = "127.0.0.1:$puerto"
        $inicio.EnvironmentVariables['CUDA_VISIBLE_DEVICES'] = '-1'
        $inicio.EnvironmentVariables['OLLAMA_VULKAN'] = '1'
        $servidor = [Diagnostics.Process]::Start($inicio)

        $responde = $false
        for ($intento = 0; $intento -lt 12 -and -not $servidor.HasExited; $intento++) {
            try {
                Invoke-RestMethod "$url/api/tags" -TimeoutSec 1 -ErrorAction Stop | Out-Null
                $responde = $true
                break
            } catch {
                Start-Sleep -Milliseconds 250
            }
        }
        if (-not $responde -or $servidor.HasExited) { throw 'Ollama no inició.' }

        $modelo = if ($env:OLLAMA_MODEL) { $env:OLLAMA_MODEL } else { 'llama3:latest' }
        $vector = if ($env:OLLAMA_EMBEDDING_MODEL) { $env:OLLAMA_EMBEDDING_MODEL } else { 'embeddinggemma:latest' }
        $permanencia = if ($env:OLLAMA_KEEP_ALIVE) { $env:OLLAMA_KEEP_ALIVE } else { '10m' }
        $warmupChat = @{model = $modelo; prompt = ''; keep_alive = $permanencia} | ConvertTo-Json -Compress
        $warmupVector = @{model = $vector; input = 'Lysandri Executive'; keep_alive = $permanencia} | ConvertTo-Json -Compress
        Invoke-RestMethod "$url/api/generate" -Method Post -ContentType 'application/json' -TimeoutSec 18 -Body $warmupChat -ErrorAction Stop | Out-Null
        Invoke-RestMethod "$url/api/embed" -Method Post -ContentType 'application/json' -TimeoutSec 15 -Body $warmupVector -ErrorAction Stop | Out-Null
        $activos = (Invoke-RestMethod "$url/api/ps" -TimeoutSec 3 -ErrorAction Stop).models
        $modeloGpu = $activos | Where-Object { $_.name -eq $modelo -and $_.size_vram -gt 0 }
        if (-not $modeloGpu) { throw 'Ollama no cargó el modelo de chat en Vulkan.' }
        return [PSCustomObject]@{ Proceso = $servidor; Url = $url }
    } catch {
        Stop-OllamaProyecto $servidor
        Write-Warning 'Vulkan no quedó disponible. Se usará la configuración habitual de Ollama.'
    }
}
