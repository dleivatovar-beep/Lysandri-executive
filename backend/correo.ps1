function Import-Correo {
    [CmdletBinding()]
    param([string]$Archivo = (Join-Path $env:LOCALAPPDATA 'Lysandri\correo.xml'))
    if (-not (Test-Path -LiteralPath $Archivo)) { return }
    try {
        $datos = Import-Clixml -LiteralPath $Archivo -ErrorAction Stop
        if ($datos.Modo -eq 'local') {
            $valores = @{
                SPRING_MAIL_HOST='127.0.0.1'; SPRING_MAIL_PORT='1025'
                SPRING_MAIL_USERNAME='no-reply@lysandri.com'; SPRING_MAIL_PASSWORD=''
                SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH='false'
                SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE='false'
                SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED='false'
            }
        } elseif ($datos.Modo -eq 'gmail' -and $datos.Credencial -is [System.Management.Automation.PSCredential]) {
            $valores = @{
                SPRING_MAIL_HOST='smtp.gmail.com'; SPRING_MAIL_PORT='587'
                SPRING_MAIL_USERNAME=$datos.Credencial.UserName
                SPRING_MAIL_PASSWORD=$datos.Credencial.GetNetworkCredential().Password
                SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH='true'
                SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE='true'
                SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED='true'
            }
            if ([string]::IsNullOrWhiteSpace($valores.SPRING_MAIL_PASSWORD)) { throw 'Credencial no disponible' }
        } else { throw 'Formato no compatible' }
        foreach ($nombre in $valores.Keys) {
            [Environment]::SetEnvironmentVariable($nombre, $valores[$nombre], 'Process')
        }
        Write-Host "  -> Correo configurado: $($datos.Modo). Credenciales protegidas." -ForegroundColor DarkGray
    } catch {
        throw 'No se pudo leer la configuracion de correo de este usuario de Windows. Ejecute de nuevo configurar-correo.ps1.'
    }
}
