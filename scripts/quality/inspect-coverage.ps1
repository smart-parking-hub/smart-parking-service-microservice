if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN)) { throw 'Set SONAR_TOKEN before running this script.' }
$sonarCredentials = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${env:SONAR_TOKEN}:"))
$headers = @{ Authorization = "Basic $sonarCredentials" }
$url = "http://localhost:9000/api/measures/component_tree?component=SmartParkingHub&qualifiers=FIL&metricKeys=coverage,uncovered_lines,uncovered_conditions&ps=500"
$res = Invoke-RestMethod -Uri $url -Headers $headers

$items = foreach ($c in $res.components) {
    $covMeasure = $c.measures | Where-Object { $_.metric -eq 'coverage' }
    $lineMeasure = $c.measures | Where-Object { $_.metric -eq 'uncovered_lines' }
    $condMeasure = $c.measures | Where-Object { $_.metric -eq 'uncovered_conditions' }

    $cov = if ($covMeasure) { [double]$covMeasure.value } else { 0 }
    $lines = if ($lineMeasure) { [int]$lineMeasure.value } else { 0 }
    $cond = if ($condMeasure) { [int]$condMeasure.value } else { 0 }

    if ($lines -gt 0 -or $cond -gt 0) {
        [PSCustomObject]@{
            Path = $c.path
            Coverage = "$cov %"
            UncoveredLines = $lines
            UncoveredCond = $cond
        }
    }
}

$items | Sort-Object UncoveredLines -Descending | Select-Object -First 35 | Format-Table -AutoSize
