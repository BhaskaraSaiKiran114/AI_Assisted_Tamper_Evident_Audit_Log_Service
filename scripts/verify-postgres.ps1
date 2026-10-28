param(
    [Parameter(Mandatory = $true)][string]$PostgresBin,
    [int]$Port = 55432,
    [string]$MavenRepository = '',
    [switch]$Offline,
    [switch]$Readiness
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $projectRoot ('.tools/pg-m2-' + [guid]::NewGuid().ToString('N'))
if (-not ([IO.Path]::GetFullPath($runRoot)).StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Temporary cluster must stay inside this project'
}
foreach ($binary in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $binary))) {
        throw "Missing PostgreSQL binary: $binary"
    }
}
$savedEnvironment = @{}
foreach ($name in @('PGPASSWORD', 'AUDIT_TEST_DB_URL', 'AUDIT_TEST_DB_USERNAME',
        'AUDIT_TEST_DB_PASSWORD', 'AUDIT_TEST_DB_DESTRUCTIVE_OK', 'AUDIT_TEST_CLUSTER_ROLES_OK')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$started = $false
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $passwordFile = Join-Path $runRoot 'password.txt'
    Set-Content -LiteralPath $passwordFile -Value $password -Encoding ascii
    & (Join-Path $PostgresBin 'initdb.exe') -D (Join-Path $runRoot 'data') -U audit_test `
        --encoding=UTF8 --locale=C --auth=scram-sha-256 --pwfile=$passwordFile *> (Join-Path $runRoot 'init.log')
    if ($LASTEXITCODE -ne 0) {
        throw "initdb failed; inspect $runRoot/init.log"
    }
    # Explicit output files avoid inherited console pipes keeping a Windows pg_ctl invocation open.
    $control = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -WindowStyle Hidden -PassThru `
        -ArgumentList @('-D', ('"' + (Join-Path $runRoot 'data') + '"'), '-l',
            ('"' + (Join-Path $runRoot 'server.log') + '"'), '-o', ('"-h 127.0.0.1 -p ' + $Port + '"'),
            '-w', '-t', '30', 'start') `
        -RedirectStandardOutput (Join-Path $runRoot 'start.log') `
        -RedirectStandardError (Join-Path $runRoot 'start-error.log')
    $started = $true
    if (-not $control.WaitForExit(40000) -or $control.ExitCode -ne 0) {
        throw "Temporary server failed; inspect $runRoot/server.log (port may be occupied)"
    }
    $env:PGPASSWORD = $password
    & (Join-Path $PostgresBin 'createdb.exe') -w -h 127.0.0.1 -p $Port -U audit_test audit_m2_test
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not create disposable audit_m2_test database'
    }
    $env:AUDIT_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/audit_m2_test"
    $env:AUDIT_TEST_DB_USERNAME = 'audit_test'
    $env:AUDIT_TEST_DB_PASSWORD = $password
    $env:AUDIT_TEST_DB_DESTRUCTIVE_OK = 'yes'
    $env:AUDIT_TEST_CLUSTER_ROLES_OK = 'yes'
    $mavenArguments = @('--batch-mode', '--no-transfer-progress', '-Ppostgres-it', 'clean', 'verify')
    if ($Readiness) {
        $mavenArguments = @('--batch-mode', '--no-transfer-progress', '-Ppostgres-it,readiness', 'clean', 'verify')
    }
    if ($MavenRepository) {
        $mavenArguments += "-Dmaven.repo.local=$MavenRepository"
    }
    if ($Offline) {
        $mavenArguments += '--offline'
    }
    & (Join-Path $projectRoot 'mvnw.cmd') @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL milestone verification failed; inspect Maven test reports'
    }
} finally {
    if ($started) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D (Join-Path $runRoot 'data') -m fast -w -t 30 stop
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Temporary cluster did not stop cleanly: $runRoot/data"
        }
    }
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    Pop-Location
    Write-Output "Temporary cluster artifacts (ignored; may contain local test credentials): $runRoot"
}
