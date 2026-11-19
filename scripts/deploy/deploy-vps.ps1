<#
==============================================================================
 Script: deploy-vps.ps1
 Description: Build Local images or reuse CI images for current main, then
              activate over SSH and verify the selected digests/health.
 Project: SmartParkingHub Microservices
==============================================================================
#>

param (
    [ValidateSet('CI', 'Local')]
    [string]$Source = 'CI',
    [string]$Target = "all",           # "all", "vps1", "vps2", "vps3"
    [string]$Service = "",             # "api-gateway", "identity-service", "booking-service", ...
    [switch]$SkipBuild,                # CI compatibility only; Local rejects skip flags
    [switch]$SkipPush,                 # CI compatibility only; Local always publishes
    [switch]$DryRun,                   # Chi kiem tra ket noi va hien thi lenh se chay
    [switch]$LoginVPS,                 # Dang nhap GHCR tren cac VPS
    [string]$GhcrUser = "",
    [string]$GhcrToken = "",
    [string]$ConfigFile = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir\..\.."
Set-Location $ProjectRoot
. (Join-Path $ScriptDir 'ConsoleStatus.ps1')

# Helper: Resolve path co chua "~"
function Resolve-UserPath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return "" }
    if ($path.StartsWith("~")) {
        return $path.Replace("~", $env:USERPROFILE)
    }
    return $path
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   SMARTPARKINGHUB - VERIFIED MAIN RELEASE TO VPS" -ForegroundColor Cyan
Write-Host "   Image source: $Source" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# 1. LOAD CONFIGURATION
# ------------------------------------------------------------------------------
if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    $ConfigFile = Join-Path $ScriptDir "deploy.config.json"
}

if (-not (Test-Path $ConfigFile)) {
    Write-Host "[LOI] Khong tim thay file cau hinh: $ConfigFile" -ForegroundColor Red
    exit 1
}

try {
    $config = Get-Content $ConfigFile -Raw | ConvertFrom-Json
} catch {
    Write-Host "[LOI] Khong the doc file JSON cau hinh: $_" -ForegroundColor Red
    exit 1
}

$imagePrefix = if ($env:IMAGE_REPO) { "ghcr.io/$env:IMAGE_REPO" } else { $config.registry.imagePrefix }
Write-Host "-> Registry Image Prefix: $imagePrefix" -ForegroundColor DarkCyan

# ------------------------------------------------------------------------------
# 2. XAC DINH TARGET SERVICES & NODES
# ------------------------------------------------------------------------------
$targetServices = @()
$targetNodes = @()

if ($Service -ne "") {
    $svcKey = $Service.ToLower().Trim()
    # Tim service phu hop theo ten hoac viet tat
    $matchedKey = $null
    foreach ($k in $config.services.PSObject.Properties.Name) {
        if ($k -eq $svcKey -or $k.Replace("-service", "") -eq $svcKey.Replace("-service", "")) {
            $matchedKey = $k
            break
        }
    }

    if (-not $matchedKey) {
        Write-Host "[LOI] Khong tim thay service '$Service' trong cau hinh!" -ForegroundColor Red
        Write-Host "Danh sach service hop le: $($config.services.PSObject.Properties.Name -join ', ')" -ForegroundColor Yellow
        exit 1
    }

    $targetServices += $config.services.$matchedKey
    $nodeId = $config.services.$matchedKey.node
    $targetNodes += $config.nodes.$nodeId
    Write-Host "-> Che do cap nhat don le: Service '$matchedKey' tren Node '$($config.nodes.$nodeId.name)'" -ForegroundColor Green
} else {
    # Chon Node theo tham so $Target
    $targetParam = $Target.ToLower().Trim()
    if ($targetParam -eq "all") {
        foreach ($prop in $config.nodes.PSObject.Properties) {
            $targetNodes += $prop.Value
        }
        foreach ($prop in $config.services.PSObject.Properties) {
            $targetServices += $prop.Value
        }
        Write-Host "-> Che do cap nhat TOAN BO: Tat ca 3 VPS va 7 Microservices" -ForegroundColor Green
    } elseif ($config.nodes.$targetParam) {
        $nodeObj = $config.nodes.$targetParam
        $targetNodes += $nodeObj
        foreach ($sName in $nodeObj.services) {
            if ($config.services.$sName) {
                $targetServices += $config.services.$sName
            }
        }
        Write-Host "-> Che do cap nhat theo cum: Node '$($nodeObj.name)'" -ForegroundColor Green
    } else {
        Write-Host "[LOI] Target '$Target' khong hop le! (Chon 'all', 'vps1', 'vps2', 'vps3')" -ForegroundColor Red
        exit 1
    }
}

# ------------------------------------------------------------------------------
# 3. PRE-FLIGHT CHECK (KIEM TRA SSH TOI VPS)
# ------------------------------------------------------------------------------
Write-Host "`n[1/5] Kiem tra moi truong va ket noi tien tram..." -ForegroundColor Yellow

# Check ssh.exe
if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    Write-Host "[LOI] Khong tim thay lenh 'ssh.exe'. Vui long cai dat OpenSSH Client!" -ForegroundColor Red
    exit 1
}

# CI mode never probes local Docker. Local mode starts Docker only after the
# source guards and prerequisite checks below have passed.

# Kiem tra ket noi SSH toi tung Node duoc chon
Write-Host "`n  Kiem tra ket noi SSH toi cac VPS:" -ForegroundColor Cyan
$aliveNodes = @()
$failedNodes = @()

foreach ($node in $targetNodes) {
    $keyPath = Resolve-UserPath $node.keyPath
    $hostAddr = $node.host
    $port = $node.port
    $user = $node.user
    $timeout = $config.ssh.connectTimeoutSeconds

    Write-Host "  -> Dang kiem tra $($node.name) ($($user)@$($hostAddr):$($port))... " -NoNewline

    if (-not (Test-Path $keyPath)) {
        Write-Host "[THAT BAI: Khong tim thay file key tai $keyPath]" -ForegroundColor Red
        $failedNodes += @{ Node = $node; Reason = "Missing SSH Key ($keyPath)" }
        continue
    }

    $sshArgs = @(
        "-i", $keyPath,
        "-p", $port,
        "-o", "ConnectTimeout=$timeout",
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=yes",
        "$user@$hostAddr",
        "echo",
        "PING_OK"
    )

    $testResult = $null
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $testResult = (Invoke-ConsoleStep -Label "Ket noi SSH $($node.id)" -Action { & ssh @sshArgs 2>&1 }) -join "`n"
    } catch {
        $testResult = $_.Exception.Message
    } finally {
        $ErrorActionPreference = $prevEAP
    }

    if ($LASTEXITCODE -eq 0 -and $testResult -match "PING_OK") {
        Write-Host "[KET NOI TOT]" -ForegroundColor Green
        $aliveNodes += $node
    } else {
        Write-Host "[KHONG THE KET NOI]" -ForegroundColor Red
        $cleanErr = ($testResult -split "`n" | Where-Object { $_ -notmatch 'CategoryInfo|FullyQualifiedErrorId' }) -join " "
        Write-Host "     Chi tiet loi: $cleanErr" -ForegroundColor DarkRed
        $failedNodes += @{ Node = $node; Reason = $cleanErr }
    }
}

if ($aliveNodes.Count -eq 0) {
    Write-Host "`n[LOI DUNG LAI] Khong the ket noi toi bat ky VPS nao trong danh sach! Vui long kiem tra SSH key / IP / Firewall." -ForegroundColor Red
    exit 1
}

if ($failedNodes.Count -gt 0) {
    Write-Host "[FAILED] At least one requested VPS is unavailable. No deployment will start." -ForegroundColor Red
    exit 1
}

if ($DryRun) {
    Write-Host "`n[DRY-RUN] Ket thuc kiem tra tien tram. Khong thuc hien build/push/deploy." -ForegroundColor Magenta
    exit 0
}

# ------------------------------------------------------------------------------
# 3.1 DANG NHAP GHCR TREN CAC VPS (NEU CHON -LoginVPS)
# ------------------------------------------------------------------------------
if ($LoginVPS) {
    Write-Host "`n================================================================" -ForegroundColor Cyan
    Write-Host "   DANG NHAP GITHUB CONTAINER REGISTRY (GHCR) TREN CAC VPS" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan

    if ([string]::IsNullOrWhiteSpace($GhcrUser)) {
        $GhcrUser = Read-Host " Nhap GitHub Username (vd: HOANGDINHTUNG)"
    }
    if ([string]::IsNullOrWhiteSpace($GhcrToken)) {
        $sec = Read-Host " Nhap GitHub PAT Token (ghp_...)" -AsSecureString
        $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
        $GhcrToken = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    }

    if ([string]::IsNullOrWhiteSpace($GhcrUser) -or [string]::IsNullOrWhiteSpace($GhcrToken)) {
        Write-Host "[LOI] Username hoac Token khong duoc de trong!" -ForegroundColor Red
        exit 1
    }

    $loginFailed = $false
    foreach ($node in $aliveNodes) {
        $keyPath = Resolve-UserPath $node.keyPath
        $hostAddr = $node.host
        $port = $node.port
        $user = $node.user

        Write-Host "`n-> Dang dang nhap GHCR tren $($node.name) ($hostAddr)..." -ForegroundColor Cyan
        $cleanUser = $GhcrUser.Trim()
        $cleanToken = $GhcrToken.Trim()
        $loginScript = "echo `"$cleanToken`" | docker login -u `"$cleanUser`" --password-stdin ghcr.io"
        Invoke-ConsoleStep -Label "Dang nhap GHCR tren $($node.id)" -Action {
            $loginScript | & ssh -i $keyPath -p $port -o StrictHostKeyChecking=yes "$user@$hostAddr" "tr -d '\r' | bash"
        }
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  [OK] $($node.name) da dang nhap GHCR thanh cong!" -ForegroundColor Green
        } else {
            $loginFailed = $true
            Write-Host "  [LOI] Khong the dang nhap GHCR tren $($node.name)!" -ForegroundColor Red
        }
    }

    Write-Host "`n[HOAN TAT] Da cap nhat xac thuc GHCR cho cac VPS." -ForegroundColor Green
    if ($loginFailed) { exit 1 }
    exit 0
}

# A deployment must identify the same clean source as remote main.
Write-Host '[2/5] Doi chieu source voi main tren GitHub...' -ForegroundColor Yellow
$status = @(& git status --porcelain)
if ($LASTEXITCODE -ne 0 -or $status.Count -gt 0) {
    Write-Host "[FAILED] Commit/push task changes first. A dirty checkout cannot publish a production release." -ForegroundColor Red
    exit 1
}
$releaseSha = (& git rev-parse HEAD | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $releaseSha -notmatch '^[a-f0-9]{40}$') { exit 1 }
$remoteHead = (Invoke-ConsoleStep -Label 'Doc commit main tren GitHub' -Action { & git ls-remote origin refs/heads/main } | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or ($remoteHead -split '\s+')[0] -ne $releaseSha) {
    Write-Host "[FAILED] Local HEAD does not match remote main. Update the source before deployment." -ForegroundColor Red
    exit 1
}
$imagePrefix = $config.registry.imagePrefix
if ($env:IMAGE_REPO -and "ghcr.io/$env:IMAGE_REPO" -ne $imagePrefix) {
    Write-Host "[FAILED] IMAGE_REPO differs from deploy.config.json." -ForegroundColor Red
    exit 1
}
$runtimePath = Join-Path $ScriptDir "release-runtime.py"
if (-not (Test-Path $runtimePath) -or -not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "[FAILED] Python 3.10+ and the release helper are required." -ForegroundColor Red
    exit 1
}
$selectedDirs = @($targetServices | ForEach-Object { $_.dir })
Write-Host "[3/5] Chuan bi artifact tu nguon $Source..." -ForegroundColor Yellow
if ($Source -eq 'Local') {
    if ($SkipBuild -or $SkipPush) {
        Write-Host '[FAILED] Local mode requires a fresh JAR/image build and push. Skip flags are not allowed.' -ForegroundColor Red
        exit 1
    }
    try {
        . (Join-Path $ScriptDir 'DockerDesktop.ps1')
        Invoke-ConsoleStep -Label 'Cho Docker Linux engine san sang' -Action { Ensure-LocalLinuxDocker }
    } catch {
        Write-Host ('[FAILED] ' + $_.Exception.Message) -ForegroundColor Red
        exit 1
    }
    Write-Host "[LOCAL] Building JARs from main $releaseSha..." -ForegroundColor Cyan
    Invoke-ConsoleStep -Label 'Build JAR bang Gradle' -Action {
        if ($Service -ne '') { cmd /c ".\gradlew.bat $($targetServices[0].gradleTask)" }
        else { cmd /c '.\gradlew.bat bootJar -x test --parallel' }
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $releaseTag = 'local-' + $releaseSha + '-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
    foreach ($svc in $targetServices) {
        $image = "$imagePrefix/$($svc.dir):$releaseTag"
        Write-Host "[LOCAL] Building and publishing $image" -ForegroundColor Cyan
        Invoke-ConsoleStep -Label "Build image $($svc.dir)" -Action {
            & docker build --platform linux/amd64 --label "org.opencontainers.image.revision=$releaseSha" -t $image $svc.dir
        }
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Invoke-ConsoleStep -Label "Push image $($svc.dir) len GHCR" -Action { & docker push $image }
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    # Generated artifacts are ignored; source changes during a build are not.
    $afterBuild = @(& git status --porcelain)
    if ($LASTEXITCODE -ne 0 -or $afterBuild.Count -gt 0 -or ((& git rev-parse HEAD | Out-String).Trim() -ne $releaseSha)) {
        Write-Host '[FAILED] Source changed during the build; no VPS activation will run.' -ForegroundColor Red
        exit 1
    }
    $manifestJson = (Invoke-ConsoleStep -Label 'Doc digest image Local da push' -Action {
        & python $runtimePath --registry-manifest $releaseSha --image-tag $releaseTag --source LOCAL --config $ConfigFile --services $selectedDirs
    } | Out-String).Trim()
} else {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Host '[FAILED] CI mode requires an authenticated GitHub CLI (gh).' -ForegroundColor Red
        exit 1
    }
    if ($SkipBuild -or $SkipPush) { Write-Host '[INFO] CI mode always reuses built artifacts; compatibility skip flags have no effect.' }
    Write-Host "[CI] Finding successful image artifacts for main $releaseSha..." -ForegroundColor Cyan
    $manifestJson = (Invoke-ConsoleStep -Label 'Lay artifact CI cua main hien tai' -Action {
        & python $runtimePath --ci-manifest --config $ConfigFile --services $selectedDirs
    } | Out-String).Trim()
}
if ($LASTEXITCODE -ne 0 -or -not $manifestJson) { exit 1 }
try { $manifest = $manifestJson | ConvertFrom-Json } catch { exit 1 }
if ($manifest.sourceSha -ne $releaseSha -or ($Source -eq 'Local' -and $manifest.source -ne 'LOCAL')) {
    Write-Host "[FAILED] Release identity differs from the selected source." -ForegroundColor Red
    exit 1
}
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($manifestJson))
$runtimeSource = Get-Content -LiteralPath $runtimePath -Raw -Encoding UTF8
$remotePython = "import base64, json" + [Environment]::NewLine + "RELEASE_INPUT = json.loads(base64.b64decode('$encoded'))" + [Environment]::NewLine + $runtimeSource
$deployFailed = $false
Write-Host '[4/5] Cap nhat va kiem tra image/health tren VPS...' -ForegroundColor Yellow
foreach ($node in $aliveNodes) {
    $keyPath = Resolve-UserPath $node.keyPath
    if ($node.targetDir -notmatch '^~?/[a-zA-Z0-9/_-]+$') { throw "Unsupported remote target path" }
    $remoteDir = if ($node.targetDir.StartsWith("~/")) { '$HOME/' + $node.targetDir.Substring(2) } else { $node.targetDir }
    $remoteCommand = "cd $remoteDir && python3 - --refresh --node $($node.id) --compose-file $($node.composeFile)"
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        Invoke-ConsoleStep -Label "Deploy va cho health $($node.id)" -Action {
            $remotePython | & ssh -i $keyPath -p $node.port -o BatchMode=yes -o StrictHostKeyChecking=yes "$($node.user)@$($node.host)" $remoteCommand
        }
        $remoteExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $prevEAP }
    if ($remoteExit -ne 0) {
        $deployFailed = $true
        Write-Host "[FAILED] $($node.id): activation was not confirmed." -ForegroundColor Red
    } else {
        Write-Host "[VERIFIED] $($node.id): selected images and health match $releaseSha." -ForegroundColor Green
    }
}
if ($deployFailed) {
    Write-Host "[FAILED] One or more requested VPS deployments failed. Do not treat this release as complete." -ForegroundColor Red
    exit 1
}
Write-Host "[SUCCESS] Every requested service passed pinned-image and health verification: $releaseSha" -ForegroundColor Green
Write-Host '[5/5] Da kiem chung xong tat ca service duoc chon.' -ForegroundColor Green
exit 0
