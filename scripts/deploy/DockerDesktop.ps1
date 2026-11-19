# Local-build mode only. CI deployment must not call these functions.
function Test-LocalLinuxDocker {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $engine = (& docker info --format '{{.OSType}}' 2>$null | Out-String).Trim()
        return ($LASTEXITCODE -eq 0 -and $engine -eq 'linux')
    } catch { return $false }
    finally { $ErrorActionPreference = $previous }
}

function Ensure-LocalLinuxDocker {
    param([ValidateRange(1,600)][int]$TimeoutSeconds = 120, [string]$DesktopPath = '')
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Local mode requires Docker Desktop and its Docker CLI.'
    }
    if (Test-LocalLinuxDocker) { return }
    if (-not $DesktopPath) {
        $candidates = @()
        if ($env:LOCALAPPDATA) {
            $candidates += Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
            $candidates += Join-Path $env:LOCALAPPDATA 'Docker\Docker Desktop.exe'
        }
        if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe' }
        $DesktopPath = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    }
    if (-not $DesktopPath -or -not (Test-Path -LiteralPath $DesktopPath -PathType Leaf)) {
        throw 'Docker Desktop was not found. Open Docker with Linux containers and retry Local mode.'
    }
    Write-Host '[LOCAL] Starting Docker Desktop and waiting for the Linux engine...'
    Start-Process -FilePath $DesktopPath -WindowStyle Hidden
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if (Test-LocalLinuxDocker) { return }
        Start-Sleep -Seconds 1
    }
    throw 'Docker Linux engine startup timed out; no image was built or deployed.'
}
