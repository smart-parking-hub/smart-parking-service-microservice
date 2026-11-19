param([string]$Runner, [string]$Scenario)
$ErrorActionPreference='Stop'
$global:FakeScenario=$Scenario
$global:Operations=[Collections.Generic.List[string]]::new()
$global:DesktopStarted=$false
$global:StatusCalls=0
$global:IsLocal=$Scenario.StartsWith('local-')
$global:Sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
function global:docker {
    if (-not $global:IsLocal) { throw 'Unexpected local Docker call in CI mode' }
    $line=$args -join ' '
    $global:Operations.Add('DOCKER '+$line)
    $global:LASTEXITCODE=0
    if ($args[0] -eq 'info') {
        if ($global:FakeScenario -eq 'local-ready' -or $global:DesktopStarted) { return 'linux' }
        $global:LASTEXITCODE=1; return
    }
    if ($args[0] -eq 'build' -and $global:FakeScenario -eq 'local-build-failure') { $global:LASTEXITCODE=12 }
    if ($args[0] -eq 'push' -and $global:FakeScenario -eq 'local-push-failure') { $global:LASTEXITCODE=13 }
}
function global:Start-Process {
    param([string]$FilePath,[string]$WindowStyle)
    if (-not $global:IsLocal) { throw 'Unexpected Desktop startup in CI mode' }
    $global:Operations.Add('DESKTOP '+$WindowStyle)
    $global:DesktopStarted=$true
}
function global:Start-Sleep {}

function global:git {
    $line=$args -join ' '
    $global:LASTEXITCODE=0
    if ($line -like 'status*') { $global:StatusCalls++; if ($global:FakeScenario -eq 'dirty' -or ($global:FakeScenario -eq 'local-source-changed' -and $global:StatusCalls -gt 1)) { return ' M example.java' }; return }
    if ($line -like 'rev-parse*') { return $global:Sha }
    if ($line -like 'ls-remote*') {
        if ($global:FakeScenario -eq 'stale') { return (('f' * 40) + [char]9 + 'refs/heads/main') }
        return ($global:Sha + [char]9 + 'refs/heads/main')
    }
    throw 'Unexpected Git mutation'
}
function global:cmd { if (-not $global:IsLocal) { throw 'Unexpected local Gradle in CI mode' }; $global:Operations.Add('GRADLE '+($args -join ' ')); $global:LASTEXITCODE=0 }
function global:python {
    $global:LASTEXITCODE=0
    $payload = '{"sourceSha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","services":{"payment-service":{"node":"vps2","composeService":"smart-parking-payment","image":"ghcr.io/smart-parking-hub/payment-service","digest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}}'
    $manifest=$payload | ConvertFrom-Json
    $manifest | Add-Member -NotePropertyName source -NotePropertyValue $(if ($global:IsLocal) { 'LOCAL' } else { 'CI' })
    return ($manifest | ConvertTo-Json -Depth 6 -Compress)
}
function global:ssh {
    if ($args -contains 'PING_OK') {
        if ($global:FakeScenario -eq 'partial-ssh' -and ($args -join ' ') -match '103.20.102.190') { $global:LASTEXITCODE=1; return 'mock unavailable' }
        $global:LASTEXITCODE=0; return 'PING_OK'
    }
    $global:Operations.Add('REMOTE_ACTIVATE')
    $null=@($input)
    if ($global:FakeScenario -eq 'remote-failure') { $global:LASTEXITCODE=1; return 'MOCK_REMOTE_FAILED' }
    $global:LASTEXITCODE=0; return 'MOCK_RELEASE_VERIFIED'
}
if ($Scenario -eq 'partial-ssh') { & $Runner -Target all -DryRun }
elseif ($Scenario -eq 'local-skip') { & $Runner -Source Local -Service payment-service -SkipBuild }
elseif ($global:IsLocal) { & $Runner -Source Local -Service payment-service }
else { & $Runner -Service payment-service -SkipPush }
$runnerOK=$?
if ($env:SPH_TEST_OPS_PATH) { @($global:Operations.ToArray()) | ConvertTo-Json | Set-Content $env:SPH_TEST_OPS_PATH }
if ($runnerOK) { exit 0 } else { exit 1 }
