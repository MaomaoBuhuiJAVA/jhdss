param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath($ProjectRoot)
$schema = Join-Path $project 'src\main\resources\sql\init.sql'
if (-not (Test-Path -LiteralPath $schema)) { throw "Database schema not found: $schema" }

function Resolve-MySqlClient {
    if ($env:MYSQL_CLIENT_PATH -and (Test-Path -LiteralPath $env:MYSQL_CLIENT_PATH)) {
        return $env:MYSQL_CLIENT_PATH
    }
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $command) { return $command.Source }
    $roots = @((Join-Path $env:ProgramFiles 'MySQL'), (Join-Path ${env:ProgramFiles(x86)} 'MySQL'))
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        $found = Get-ChildItem -LiteralPath $root -Filter mysql.exe -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Directory.Name -ieq 'bin' } | Select-Object -First 1
        if ($null -ne $found) { return $found.FullName }
    }
    throw 'mysql.exe was not found after MySQL installation'
}

$mysql = Resolve-MySqlClient
$username = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'root' }
$password = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'a123456' }
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $password
try {
    $tableCount = & $mysql '--protocol=tcp' '--host=127.0.0.1' '--port=3306' "--user=$username" `
        --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='jhds';" 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot authenticate to local MySQL with SPRING_DATASOURCE credentials' }
    $count = 0
    if (-not [int]::TryParse(([string]$tableCount).Trim(), [ref]$count)) {
        throw 'MySQL returned an invalid schema status'
    }
    if ($count -gt 0) {
        Write-Host "[OK] Existing jhds database retained ($count tables)."
        exit 0
    }
    Write-Host '[SETUP] The jhds database is empty; initializing its schema...'
    Get-Content -LiteralPath $schema -Raw -Encoding UTF8 |
        & $mysql '--protocol=tcp' '--host=127.0.0.1' '--port=3306' "--user=$username" '--default-character-set=utf8mb4'
    if ($LASTEXITCODE -ne 0) { throw 'Database schema initialization failed' }
    Write-Host '[OK] New jhds database initialized.'
} finally {
    $env:MYSQL_PWD = $previousPassword
}
