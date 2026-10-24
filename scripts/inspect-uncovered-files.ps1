$services = @('booking-service', 'identity-service', 'notification-service', 'parking-lot-service', 'payment-service')
foreach ($svc in $services) {
    Write-Host "`n>>> $svc Files with Uncovered Lines:" -ForegroundColor Cyan
    $url = "http://localhost:9000/api/measures/component_tree?component=SmartParkingHub:$svc/src&metricKeys=coverage,line_coverage,lines_to_cover,uncovered_lines,conditions_to_cover,uncovered_conditions&qualifiers=FIL&s=metric&metricSort=uncovered_lines&asc=false&ps=15"
    $json = curl.exe -s -u squ_84023b026d3de2492660f253645160fa27ca8f90: $url | ConvertFrom-Json
    foreach ($c in $json.components) {
        $name = $c.name
        $cov = ($c.measures | Where-Object { $_.metric -eq 'coverage' }).value
        $lcov = ($c.measures | Where-Object { $_.metric -eq 'line_coverage' }).value
        $lines = ($c.measures | Where-Object { $_.metric -eq 'lines_to_cover' }).value
        $uncov = ($c.measures | Where-Object { $_.metric -eq 'uncovered_lines' }).value
        $cond = ($c.measures | Where-Object { $_.metric -eq 'conditions_to_cover' }).value
        $ucond = ($c.measures | Where-Object { $_.metric -eq 'uncovered_conditions' }).value
        if ([int]$uncov -gt 0 -or [int]$ucond -gt 0) {
            Write-Host ("  {0,-35} | Cov: {1,5}% | Lines: {2,3} (Uncov: {3,2}) | Conds: {4,2} (Uncov: {5,2})" -f $name, $cov, $lines, $uncov, $cond, $ucond)
        }
    }
}
