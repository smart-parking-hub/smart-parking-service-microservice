# Runs only against a temporary repo prepared by test_local_launcher.py.
param([Parameter(Mandatory=$true)][string]$Runner)
$ErrorActionPreference = 'Stop'
$global:RecordedCommands = [Collections.Generic.List[string]]::new()
function global:git { $global:LASTEXITCODE=0; return 'mock-git' }
function global:docker {
    $line = $args -join ' '
    if ($line -eq 'info') { $global:LASTEXITCODE=0; return 'ready' }
    if ($line -like 'network ls *') { $global:LASTEXITCODE=0; return 'smart-parking-net' }
    if ($line -like 'inspect *') { $global:LASTEXITCODE=0; return 'healthy' }
    if ($line -like 'exec * mysqladmin ping*') { $global:LASTEXITCODE=0; return 'mysqld is alive' }
    if ($line -like 'logs *') { $global:LASTEXITCODE=0; return 'Started TestApplication' }
    if ($line -like 'ps *') { $global:LASTEXITCODE=0; return 'mock containers' }
    throw "Unexpected Docker operation in mock launcher: $line"
}
function global:cmd {
    $line = $args -join ' '
    if ($line -notlike '/c docker compose *' -and $line -notlike '/c .\gradlew.bat *') { throw 'Unexpected native command' }
    $global:RecordedCommands.Add($line)
    $global:LASTEXITCODE=0
}
function global:Start-Sleep {}
function global:Start-Process { throw 'Unexpected process launch' }
$tokens=$null; $parseErrors=$null
$null=[Management.Automation.Language.Parser]::ParseFile($Runner,[ref]$tokens,[ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw 'Launcher PowerShell parsing failed' }
$results=@{}
foreach ($mode in @('local-dev','local-full','local-integration')) {
    $global:RecordedCommands.Clear()
    & $Runner -Mode $mode -SkipBuild *> $null
    $results[$mode]=@($global:RecordedCommands.ToArray())
}
$global:RecordedCommands.Clear()
& $Runner -Mode local-integration -Service payment *> $null
$results['payment']=@($global:RecordedCommands.ToArray())
$results | ConvertTo-Json -Depth 4 -Compress
