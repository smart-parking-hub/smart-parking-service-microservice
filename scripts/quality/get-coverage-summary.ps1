if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN)) { throw 'Set SONAR_TOKEN before running this script.' }
$modules = @('api-gateway', 'booking-service', 'common-library', 'config-server', 'discovery-server', 'identity-service', 'notification-service', 'parking-lot-service', 'payment-service')
Write-Host "========================================================================================="
Write-Host " [SmartParkingHub] Per-Module Coverage Report"
Write-Host "========================================================================================="
foreach ($m in $modules) {
    $key = "SmartParkingHub:$m/src"
    $url = "http://localhost:9000/api/measures/component?component=$key&metricKeys=coverage,line_coverage,branch_coverage,lines_to_cover,uncovered_lines,conditions_to_cover,uncovered_conditions"
    $json = curl.exe -s -u "${env:SONAR_TOKEN}:" $url | ConvertFrom-Json
    $cov = ($json.component.measures | Where-Object { $_.metric -eq 'coverage' }).value
    $lcov = ($json.component.measures | Where-Object { $_.metric -eq 'line_coverage' }).value
    $lines = ($json.component.measures | Where-Object { $_.metric -eq 'lines_to_cover' }).value
    $uncov = ($json.component.measures | Where-Object { $_.metric -eq 'uncovered_lines' }).value
    $cond = ($json.component.measures | Where-Object { $_.metric -eq 'conditions_to_cover' }).value
    $ucond = ($json.component.measures | Where-Object { $_.metric -eq 'uncovered_conditions' }).value
    Write-Host ("{0,-22} | Coverage: {1,5}% | Line: {2,5}% | Lines: {3,4} (Uncov: {4,3}) | Conds: {5,3} (Uncov: {6,3})" -f $m, $cov, $lcov, $lines, $uncov, $cond, $ucond)
}
Write-Host "========================================================================================="
