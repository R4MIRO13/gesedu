# Borra el volumen de PostgreSQL y vuelve a crear la base de datos vacía (roles incluidos).
# Úsalo cuando alguien edite una migración V1..V11 antes del primer despliegue compartido (docs/PLAN.md § 4.4).
# Funciona en Windows PowerShell 5.1 y en PowerShell 7: Docker escribe su progreso en stderr, así que los comandos
# nativos no se tratan como error por lo que escriban; solo cuenta su código de salida ($LASTEXITCODE).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# PowerShell 7.4+ puede convertir un código de salida distinto de 0 en excepción por su cuenta; aquí lo decidimos nosotros.
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) { $PSNativeCommandUseErrorActionPreference = $false }
Set-Location (Join-Path $PSScriptRoot '..')

function Invoke-Nativo {
    param([Parameter(Mandatory)][string]$Descripcion, [Parameter(Mandatory)][string[]]$Comando)
    $previo = $ErrorActionPreference
    # En 5.1, con 'Stop', cualquier línea en stderr de un ejecutable se vuelve NativeCommandError: se baja a 'Continue'.
    $ErrorActionPreference = 'Continue'
    try {
        # El flujo de error se une al de salida y se muestra como texto, sin ErrorRecord.
        & $Comando[0] $Comando[1..($Comando.Length - 1)] 2>&1 | ForEach-Object { "$_" } | Write-Host
        $codigo = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previo
    }
    if ($codigo -ne 0) {
        throw "$Descripcion falló con el código de salida $codigo."
    }
}

Invoke-Nativo 'docker compose down -v' @('docker', 'compose', 'down', '-v')
Invoke-Nativo 'docker compose up' @('docker', 'compose', 'up', '-d', '--wait', 'postgres', 'mailpit')
Write-Host 'Base de datos reiniciada (postgres y mailpit en marcha). Arranca el backend para que Flyway aplique las migraciones.'
