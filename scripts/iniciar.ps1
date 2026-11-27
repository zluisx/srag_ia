$ErrorActionPreference = 'Stop'
$backend = Join-Path (Split-Path $PSScriptRoot -Parent) 'backend'
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem -Directory -LiteralPath (Join-Path $env:TEMP 'srag-jdk21') -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } | Select-Object -First 1
    if (-not $jdk) { throw 'Instale um JDK 21 e configure JAVA_HOME.' }
    $env:JAVA_HOME = $jdk.FullName
}
function Ler-SenhaInicial([string]$perfil) {
    $segura = Read-Host "Crie a senha do perfil $perfil (12 caracteres ou mais)" -AsSecureString
    return [System.Net.NetworkCredential]::new('', $segura).Password
}
Push-Location $backend
try {
    if (-not $env:SRAG_DB -and -not (Test-Path 'data/srag.mv.db')) {
        if (-not $env:SRAG_OPERADOR_SENHA) { $env:SRAG_OPERADOR_SENHA = Ler-SenhaInicial 'operador@demo.local' }
        if (-not $env:SRAG_GESTOR_SENHA) { $env:SRAG_GESTOR_SENHA = Ler-SenhaInicial 'gestor@demo.local' }
        foreach ($senha in @($env:SRAG_OPERADOR_SENHA, $env:SRAG_GESTOR_SENHA)) {
            if ($senha.Trim().Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($senha) -gt 72) {
                throw 'Cada senha inicial exige ao menos 12 caracteres e no maximo 72 bytes.'
            }
        }
    }
    Write-Host 'Aplicacao local: http://127.0.0.1:8080'
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Maven retornou $LASTEXITCODE." }
} finally {
    Pop-Location
}
