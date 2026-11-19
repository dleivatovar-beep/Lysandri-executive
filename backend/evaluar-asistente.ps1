param([string]$Api='http://127.0.0.1:8080', [Parameter(Mandatory=$true)][long]$Documento)
$ErrorActionPreference='Stop'
$salida=Join-Path $PSScriptRoot ('evaluacion\resultado-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.csv')
$resultados=@()
foreach ($caso in (Import-Csv -LiteralPath (Join-Path $PSScriptRoot 'evaluacion\preguntas.csv'))) {
    $reloj=[Diagnostics.Stopwatch]::StartNew()
    try {
        $cuerpo=@{pregunta=$caso.pregunta} | ConvertTo-Json
        $respuesta=Invoke-RestMethod -Uri ($Api.TrimEnd('/') + '/api/v1/chat/rag') -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($cuerpo)) -TimeoutSec 180
        $reloj.Stop()
        $fuentes=@($respuesta.fuentes)
        $esperada=@($fuentes | Where-Object { $_.idDocumento -eq $Documento -and $_.seccion -match ('^' + [regex]::Escape($caso.seccion) + '\s') })
        $resultado=[PSCustomObject]@{
            id=$caso.id;pregunta=$caso.pregunta;respuesta=$respuesta.respuesta
            tiempoExternoMs=$reloj.ElapsedMilliseconds;latenciaBackendMs=$respuesta.latenciaMs
            fuentes=($fuentes | ConvertTo-Json -Depth 6 -Compress)
            fuenteEsperada=if ($caso.seccion) { $esperada.Count -gt 0 } else { $null }
            criterio=$caso.criterio;revisionHumana='PENDIENTE';error=''
        }
    } catch {
        $reloj.Stop()
        $resultado=[PSCustomObject]@{id=$caso.id;pregunta=$caso.pregunta;respuesta='';tiempoExternoMs=$reloj.ElapsedMilliseconds;latenciaBackendMs=$null;fuentes='';fuenteEsperada=$false;criterio=$caso.criterio;revisionHumana='NO EJECUTADA';error=$_.Exception.Message}
    }
    $resultados += $resultado
    $resultados | Export-Csv -LiteralPath $salida -NoTypeInformation -Encoding UTF8
    Write-Host ('Pregunta ' + $caso.id + ' registrada. Revisión de contenido: ' + $resultado.revisionHumana)
}
Write-Host "Resultados: $salida"
if (@($resultados | Where-Object {$_.error}).Count -gt 0) { exit 1 }
Write-Host 'Recorrido completado. Esto no aprueba automáticamente la precisión: revise cada respuesta y su fuente.'
