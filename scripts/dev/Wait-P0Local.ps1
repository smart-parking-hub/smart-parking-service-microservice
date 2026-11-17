[CmdletBinding()]
param (
    [ValidateRange(1, 600)]
    [int]$TimeoutSeconds = 180,
    [ValidateNotNullOrEmpty()]
    [int[]]$Ports = @(8081, 8082, 8083, 8084, 8085, 8086, 8088)
)

# Docker "running" precedes Spring readiness, especially on a cold local start.
$pendingPorts = [System.Collections.Generic.HashSet[int]]::new()
foreach ($port in $Ports) { $null = $pendingPorts.Add($port) }
$timer = [System.Diagnostics.Stopwatch]::StartNew()
Write-Host 'Waiting for local application health...'
while ($pendingPorts.Count -gt 0 -and $timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    foreach ($port in @($pendingPorts)) {
        $remaining = $TimeoutSeconds - $timer.Elapsed.TotalSeconds
        if ($remaining -le 0) { break }
        try {
            $response = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" `
                -TimeoutSec ([Math]::Max(1, [Math]::Min(3, [Math]::Ceiling($remaining)))) -ErrorAction Stop
            if ($response.status -eq 'UP') {
                $null = $pendingPorts.Remove($port)
                Write-Host "Port ${port}: UP"
            }
        } catch {
            # A starting or unhealthy service remains pending until the deadline.
        }
    }
    if ($pendingPorts.Count -gt 0 -and $timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        Start-Sleep -Milliseconds 500
    }
}
if ($pendingPorts.Count -gt 0) {
    Write-Host "Local startup timed out. Unready ports: $($pendingPorts -join ', ')"
    exit 1
}
Write-Host 'Local applications are ready.'
exit 0
