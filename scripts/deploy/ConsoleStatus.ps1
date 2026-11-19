# Presentation only: run the action synchronously; a separate runspace reports
# elapsed time without capturing its output or changing its command arguments.
function Clear-ConsolePendingInput {
    try {
        if (-not [Console]::IsInputRedirected) {
            for ($i = 0; $i -lt 256 -and [Console]::KeyAvailable; $i++) {
                $null = [Console]::ReadKey($true)
            }
        }
    } catch { } # Hosts without a console must keep the original input behavior.
}

function Invoke-ConsoleStep {
    param(
        [Parameter(Mandatory=$true)][string]$Label,
        [Parameter(Mandatory=$true)][scriptblock]$Action,
        [ValidateRange(50,60000)][int]$IntervalMilliseconds = 5000
    )
    $watch = [Diagnostics.Stopwatch]::StartNew()
    Write-Host "[DANG CHAY] $Label" -ForegroundColor Cyan
    $stop = [Threading.ManualResetEventSlim]::new($false)
    $heartbeat = $null
    $pending = $null
    try {
        $heartbeat = [PowerShell]::Create()
        $null = $heartbeat.AddScript({
            param($signal, $label, $timer, $interval)
            while (-not $signal.Wait($interval)) {
                [Console]::Error.WriteLine(('[DANG CHAY] {0} | {1:0}s da qua - lenh chua ket thuc.' -f $label, $timer.Elapsed.TotalSeconds))
            }
        }).AddArgument($stop).AddArgument($Label).AddArgument($watch).AddArgument($IntervalMilliseconds)
        $pending = $heartbeat.BeginInvoke()
    } catch {
        # A limited host can still execute the action with start/end notices.
        if ($heartbeat) { $heartbeat.Dispose(); $heartbeat = $null }
    }
    # Native applications set the global automatic variable. Do not shadow it
    # with a function/script-local value: that would hide Docker/SSH failures.
    $global:LASTEXITCODE = 0
    try {
        . $Action
    } finally {
        $operationExit = $global:LASTEXITCODE
        $stop.Set()
        if ($heartbeat) {
            try { $null = $heartbeat.EndInvoke($pending) } catch { }
            $heartbeat.Dispose()
        }
        $stop.Dispose()
        $watch.Stop()
        Write-Host ('[KET THUC BUOC] {0} | {1:0.0}s - xem ket qua ben duoi.' -f $Label, $watch.Elapsed.TotalSeconds) -ForegroundColor DarkCyan
        Clear-ConsolePendingInput
        $global:LASTEXITCODE = $operationExit
    }
}
