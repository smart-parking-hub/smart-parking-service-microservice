# PowerShell script to scan and free SmartParkingHub ports

$ports = @(8081, 8082, 8083, 8084, 8085, 8088, 8761)

Write-Host "Scanning for processes on ports: $($ports -join ', ')..." -ForegroundColor Cyan

$killedAny = $false

foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            if ($procId -gt 0) {
                try {
                    $proc = Get-Process -Id $procId -ErrorAction Stop
                    Write-Host "Found process '$($proc.ProcessName)' (PID: $procId) on port $port. Terminating..." -ForegroundColor Yellow
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    Write-Host " -> Killed PID $procId successfully." -ForegroundColor Green
                    $killedAny = $true
                } catch {
                    Write-Host " -> Could not kill PID $procId (may already have exited or require Admin privileges)." -ForegroundColor Red
                }
            }
        }
    } else {
        Write-Host "Port $port is clear." -ForegroundColor DarkGray
    }
}

if ($killedAny) {
    Write-Host "`nStale processes have been terminated! Ports are ready to use." -ForegroundColor Green
} else {
    Write-Host "`nAll target ports were already free!" -ForegroundColor Green
}
