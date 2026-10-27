param(
    [Parameter(Mandatory=$true)][string]$MysqlConnectorJar,
    [Parameter(Mandatory=$true)][string]$BaseJdbcUrl,
    [string]$GradleCommand = '.\gradlew.bat'
)
$ErrorActionPreference = 'Stop'
# Run from the project root. Credentials are inherited from application.properties / environment.
$suffix = (Get-Date -Format 'yyyyMMddHHmmss') + '_' + [guid]::NewGuid().ToString('N').Substring(0,6)
$lockSchema = 'emr_bench_lock_' + $suffix
$uniqueSchema = 'emr_bench_unique_' + $suffix
$environmentNames = @('APPOINTMENT_TEST_MYSQL_URL','APPOINTMENT_TEST_ISOLATION','APPOINTMENT_BENCHMARK_MODE')
$originalValues = @{}
foreach ($key in $environmentNames) { $originalValues[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
$runFailures = @()
try {
    $env:APPOINTMENT_TEST_MYSQL_URL = $BaseJdbcUrl
    foreach ($schema in @($lockSchema,$uniqueSchema)) {
        & java --class-path $MysqlConnectorJar db/AppointmentSchemaTool.java create-baseline $schema
        if ($LASTEXITCODE -ne 0) { throw 'Failed to create an isolated original baseline; no existing schema is overwritten.' }
    }
    & java --class-path $MysqlConnectorJar db/AppointmentSchemaTool.java apply $uniqueSchema
    if ($LASTEXITCODE -ne 0) { throw 'Failed to apply UNIQUE to isolated comparison schema.' }
    $cases = @(
        @('PESSIMISTIC','TRANSACTION_REPEATABLE_READ',$lockSchema),
        @('UNIQUE','TRANSACTION_REPEATABLE_READ',$uniqueSchema),
        @('UNIQUE','TRANSACTION_READ_COMMITTED',$uniqueSchema),
        @('PESSIMISTIC','TRANSACTION_READ_COMMITTED',$lockSchema)
    )
    foreach ($case in $cases) {
        $env:APPOINTMENT_BENCHMARK_MODE = $case[0]
        $env:APPOINTMENT_TEST_ISOLATION = $case[1]
        $env:APPOINTMENT_TEST_MYSQL_URL = [regex]::Replace($BaseJdbcUrl, '/[^/?]+(?=\?|$)', '/' + $case[2])
        & $GradleCommand test --tests '*AppointmentPerformanceTest' --no-daemon --rerun-tasks
        if ($LASTEXITCODE -ne 0) { $runFailures += ($case[0] + '/' + $case[1]) }
    }
    & (Join-Path $PSScriptRoot 'Summarize-AppointmentBenchmark.ps1')
    Write-Output ('Isolated schemas retained (fixtures cleaned by tests): ' + $lockSchema + ', ' + $uniqueSchema)
    if ($runFailures.Count -gt 0) { throw ('Correctness failures recorded, not suppressed: ' + ($runFailures -join ', ')) }
} finally {
    foreach ($key in $environmentNames) { [Environment]::SetEnvironmentVariable($key, $originalValues[$key], 'Process') }
}
