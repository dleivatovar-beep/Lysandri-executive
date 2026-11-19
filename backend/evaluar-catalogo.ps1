param([string]$Api = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
$carpeta = Join-Path $PSScriptRoot 'evaluacion'
$archivo = Join-Path $carpeta ('catalogo-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
$casos = Get-Content -LiteralPath (Join-Path $carpeta 'catalogo.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$resultados = @()
foreach ($caso in $casos) {
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    try {
        $datos = @{pregunta=$caso.pregunta}
        if ($caso.programa) { $datos.idPrograma = $caso.programa }
        $cuerpo = $datos | ConvertTo-Json
        $respuesta = Invoke-RestMethod -Method Post -Uri ($Api.TrimEnd('/') + '/api/v1/chat/rag') -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($cuerpo)) -TimeoutSec 180
        $reloj.Stop()
        $resultado = [PSCustomObject]@{
            id=$caso.id; pregunta=$caso.pregunta; tipo=$caso.tipo
            esperado=$caso.esperado; respuesta=$respuesta.respuesta; fuentes=@($respuesta.fuentes)
            tiempoMs=$reloj.ElapsedMilliseconds; latenciaBackendMs=$respuesta.latenciaMs
            revision='PENDIENTE'; error=$null
        }
    }
    catch {
        $reloj.Stop()
        $resultado = [PSCustomObject]@{
            id=$caso.id; pregunta=$caso.pregunta; tipo=$caso.tipo
            esperado=$caso.esperado; respuesta=$null; fuentes=@()
            tiempoMs=$reloj.ElapsedMilliseconds; latenciaBackendMs=$null
            revision='NO EJECUTADA'; error=$_.Exception.Message
        }
    }
    $resultados += $resultado
    $resultados | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $archivo -Encoding UTF8
    Write-Host "$($resultados.Count)/$($casos.Count): $($caso.id), $($resultado.tiempoMs) ms, revision $($resultado.revision)"
}
Write-Host "Resultados: $archivo"
Write-Host 'Revise dato, moneda, unidad, fuentes y reconocimiento de datos no confirmados. Una respuesta HTTP 200 no acredita exactitud.'
if (@($resultados | Where-Object { $_.error }).Count) { exit 1 }
