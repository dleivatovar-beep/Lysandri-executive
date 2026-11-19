param([string]$Api = 'http://127.0.0.1:8080', [switch]$IncluirSilabos)
$ErrorActionPreference = 'Stop'
$configuracion = @{}
$sesion = $null
try {
    Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\.env') | ForEach-Object {
        $linea = $_.Trim()
        if ($linea -and -not $linea.StartsWith('#') -and $linea.Contains('=')) {
            $par = $linea.Split('=', 2)
            $configuracion[$par[0].Trim()] = $par[1].Trim().Trim('"').Trim("'")
        }
    }
    $psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
    if (-not $psql) { $psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe' }
    $env:PGPASSWORD = $configuracion['DB_APP_PASSWORD']
    $env:PGCLIENTENCODING = 'UTF8'
    $consulta = "SELECT json_agg(p ORDER BY id_programa)::text FROM (SELECT id_programa,titulo,precio,moneda,duracion_horas,descripcion_corta,descripcion_detallada,syllabus_url FROM public.programa WHERE activo = true) p;"
    $salida = & $psql -h 127.0.0.1 -p 5432 -U $configuracion['DB_APP_USER'] -d lysandri_db -w -At -v ON_ERROR_STOP=1 -c $consulta
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar el catalogo original.' }
    $programas = @((($salida -join "`n") | ConvertFrom-Json))
    if (-not $programas.Count -or -not $programas[0]) { throw 'No hay programas activos para cargar.' }
    $correo = Read-Host 'Correo de ADMIN'
    $segura = Read-Host 'Contrasena' -AsSecureString
    $credencial = New-Object System.Management.Automation.PSCredential($correo, $segura)
    $login = @{email=$correo;password=$credencial.GetNetworkCredential().Password} | ConvertTo-Json
    $Api = $Api.TrimEnd('/')
    $sesion = Invoke-RestMethod -Method Post -Uri "$Api/api/v1/auth/login" -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($login))
    if ($sesion.rol -ne 'ADMIN' -or -not $sesion.token) { throw 'Se requiere una cuenta ADMIN.' }
    function Send-Knowledge($Titulo, $Contenido, $Programa, $Estado = 'VIGENTE', $Version = '2') {
        $cuerpo = @{titulo=$Titulo;contenido=$Contenido;idPrograma=$Programa;version=$Version;estado=$Estado} | ConvertTo-Json -Depth 5
        $resultado = Invoke-RestMethod -Method Post -Uri "$Api/api/v1/admin/conocimiento" -Headers @{Authorization="Bearer $($sesion.token)"} -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($cuerpo)) -TimeoutSec 900
        Write-Host "$Titulo -> documento $($resultado.idDocumento), $($resultado.fragmentos) fragmentos"
    }
    $resumen = @('# Cursos de Lysandri Executive', '', 'Precios y duraciones del catalogo registrado en PostgreSQL. Los precios estan expresados en la moneda indicada; no implican conversion a soles ni disponibilidad de cobro o acceso a Moodle.', '', '## Cursos y precios')
    foreach ($curso in $programas) {
        $precio = ([decimal]$curso.precio).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
        $resumen += "- $($curso.titulo): $($curso.moneda) $precio. Duracion registrada: $($curso.duracion_horas) horas."
        $contenido = "# $($curso.titulo)`n`n## Precio`nEl precio registrado es $($curso.moneda) $precio.`n`n## Duracion`n$($curso.duracion_horas) horas.`n`n## Descripcion`n$($curso.descripcion_corta)`n`n$($curso.descripcion_detallada)`n`n## Silabo`n"
        if ($curso.syllabus_url) { $contenido += "Enlace registrado: $($curso.syllabus_url). El contenido del enlace no se ha importado automaticamente." }
        else { $contenido += 'No hay un enlace de silabo registrado. No afirmar modulos, requisitos ni certificaciones que no esten descritos en las fuentes.' }
        Send-Knowledge -Titulo "Curso: $($curso.titulo)" -Contenido $contenido -Programa $curso.id_programa
    }
    Send-Knowledge -Titulo 'Catalogo de Lysandri Executive' -Contenido ($resumen -join "`n") -Programa $null
    if ($IncluirSilabos) {
        $carpeta = Join-Path $PSScriptRoot '..\ai-service\data\docs\Cursos'
        $indice = Get-Content -LiteralPath (Join-Path $carpeta 'indice.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($documento in $indice) {
            if ($documento.idPrograma -notin @($programas.id_programa)) { throw 'El silabo no corresponde a un curso activo.' }
            $texto = Get-Content -LiteralPath (Join-Path $carpeta $documento.archivo) -Raw -Encoding UTF8
            Send-Knowledge -Titulo $documento.titulo -Contenido $texto -Programa $documento.idPrograma -Estado $documento.estado -Version $documento.version
        }
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue
    $configuracion.Clear()
    $login=$null; $sesion=$null; $credencial=$null; $segura=$null
}
