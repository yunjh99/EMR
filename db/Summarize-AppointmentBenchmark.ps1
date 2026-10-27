param([string]$ReportDirectory = 'build/reports/appointment-benchmark')
$ErrorActionPreference = 'Stop'
function Get-Median([double[]]$values) {
    $ordered = @($values | Sort-Object)
    if ($ordered.Count -eq 0) { return [double]::NaN }
    $middle = [int][math]::Floor($ordered.Count / 2)
    if ($ordered.Count % 2 -eq 1) { return $ordered[$middle] }
    return ($ordered[$middle-1] + $ordered[$middle]) / 2
}
$rows = @()
Get-ChildItem -LiteralPath $ReportDirectory -File | Where-Object { $_.Name -match '^(PESSIMISTIC|UNIQUE)-[24]\.csv$' } | ForEach-Object {
    $rows += Import-Csv -LiteralPath $_.FullName
}
$summary = foreach ($group in ($rows | Group-Object mode,isolation,scenario,phase)) {
    $runs = @($group.Group)
    $item = [ordered]@{
        mode=$runs[0].mode; isolation=$runs[0].isolation; scenario=$runs[0].scenario; phase=$runs[0].phase
        rounds=$runs.Count; requests=($runs | Measure-Object requests -Sum).Sum
        success=($runs | Measure-Object success -Sum).Sum; duplicate=($runs | Measure-Object duplicate -Sum).Sum
        other=($runs | Measure-Object other -Sum).Sum
    }
    foreach ($metric in @('stored','active','elapsedMs','rps','successP50','successP95','successP99',
                         'duplicateP50','duplicateP95','duplicateP99','globalLockMs','globalLockWaits',
                         'maxPoolPending','maxPoolActive','maxDbWaiters')) {
        [double[]]$values = @($runs | ForEach-Object { [double]::Parse($_.$metric,[cultureinfo]::InvariantCulture) } |
                Where-Object { -not [double]::IsNaN($_) })
        $item[$metric + 'Median'] = Get-Median $values
        $item[$metric + 'Min'] = if ($values.Count) { ($values | Measure-Object -Minimum).Minimum } else { [double]::NaN }
        $item[$metric + 'Max'] = if ($values.Count) { ($values | Measure-Object -Maximum).Maximum } else { [double]::NaN }
    }
    [pscustomobject]$item
}
$summary | Export-Csv -NoTypeInformation -Encoding UTF8 -Path (Join-Path $ReportDirectory 'summary.csv')
$summary | Where-Object phase -eq 'MEASURE' | Sort-Object isolation,scenario,mode |
    Select-Object mode,isolation,scenario,success,duplicate,other,elapsedMsMedian,elapsedMsMin,elapsedMsMax,rpsMedian |
    Format-Table -AutoSize
