<#
==============================================================================
 Script: deploy-vps.ps1
 Description: Tu dong bien dich JAR, build Docker Image, push len GHCR va
              SSH cap nhat toan bo cum VPS (Multi-VPS Cluster).
 Project: SmartParkingHub Microservices
==============================================================================
#>

param (
    [string]$Target = "all",           # "all", "vps1", "vps2", "vps3"
    [string]$Service = "",             # "api-gateway", "identity-service", "booking-service", ...
    [switch]$SkipBuild,                # Bo qua buoc Gradle bootJar
    [switch]$SkipPush,                 # Bo qua buoc Docker push len Registry
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

# Helper: Resolve path co chua "~"
function Resolve-UserPath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return "" }
    if ($path.StartsWith("~")) {
        return $path.Replace("~", $env:USERPROFILE)
    }
    return $path
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   SMARTPARKINGHUB - LOCAL TO VPS AUTO-DEPLOYMENT (OPTION 1)" -ForegroundColor Cyan
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
# 3. PRE-FLIGHT CHECK (KIEM TRA DOCKER LOCAL & KET NOI SSH TOI VPS)
# ------------------------------------------------------------------------------
Write-Host "`n[1/5] Kiem tra moi truong va ket noi tien tram..." -ForegroundColor Yellow

# Check ssh.exe
if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    Write-Host "[LOI] Khong tim thay lenh 'ssh.exe'. Vui long cai dat OpenSSH Client!" -ForegroundColor Red
    exit 1
}

# Check docker.exe
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[LOI] Khong tim thay lenh 'docker.exe'. Vui long cai dat Docker Desktop!" -ForegroundColor Red
    exit 1
}

# Check Docker Daemon local
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker not running" }
    Write-Host "  [OK] Docker Daemon local dang hoat dong tot." -ForegroundColor DarkGreen
} catch {
    Write-Host "  [LOI] Docker Daemon chua chay. Vui long bat Docker Desktop truoc!" -ForegroundColor Red
    exit 1
}

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
        "-o", "StrictHostKeyChecking=no",
        "$user@$hostAddr",
        "echo",
        "PING_OK"
    )

    $testResult = $null
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $testResult = (& ssh @sshArgs 2>&1) -join "`n"
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
    Write-Host "`n[CANH BAO] Co $($failedNodes.Count) VPS khong the ket noi. Chi tiep tuc voi cac VPS dang online:" -ForegroundColor Yellow
    foreach ($fn in $failedNodes) {
        Write-Host "   - $($fn.Node.name) ($($fn.Node.host)): $($fn.Reason)" -ForegroundColor Yellow
    }
    
    # Loc lai targetServices theo aliveNodes
    $aliveNodeIds = $aliveNodes | ForEach-Object { $_.id }
    $targetServices = $targetServices | Where-Object { $aliveNodeIds -contains $_.node }
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

    foreach ($node in $aliveNodes) {
        $keyPath = Resolve-UserPath $node.keyPath
        $hostAddr = $node.host
        $port = $node.port
        $user = $node.user

        Write-Host "`n-> Dang dang nhap GHCR tren $($node.name) ($hostAddr)..." -ForegroundColor Cyan
        $cleanUser = $GhcrUser.Trim()
        $cleanToken = $GhcrToken.Trim()
        $loginScript = "echo `"$cleanToken`" | docker login -u `"$cleanUser`" --password-stdin ghcr.io"
        $loginScript | & ssh -i $keyPath -p $port -o StrictHostKeyChecking=no "$user@$hostAddr" "tr -d '\r' | bash"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  [OK] $($node.name) da dang nhap GHCR thanh cong!" -ForegroundColor Green
        } else {
            Write-Host "  [LOI] Khong the dang nhap GHCR tren $($node.name)!" -ForegroundColor Red
        }
    }

    Write-Host "`n[HOAN TAT] Da cap nhat xac thuc GHCR cho cac VPS." -ForegroundColor Green
    exit 0
}

# ------------------------------------------------------------------------------
# 4. BIEN DICH GRADLE LOCAL (BOOTJAR)
# ------------------------------------------------------------------------------
if (-not $SkipBuild) {
    Write-Host "`n[2/5] Bien dich ma nguon Backend bang Gradle..." -ForegroundColor Yellow
    
    if ($Service -ne "") {
        $gradleTask = $targetServices[0].gradleTask
        Write-Host "  -> Chay: .\gradlew.bat $gradleTask" -ForegroundColor Cyan
        cmd /c ".\gradlew.bat $gradleTask"
    } else {
        Write-Host "  -> Chay: .\gradlew.bat bootJar -x test --parallel" -ForegroundColor Cyan
        cmd /c ".\gradlew.bat bootJar -x test --parallel"
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[LOI] Bien dich Gradle that bai! Vui long sua loi code truoc khi deploy." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "  [OK] Bien dich JAR hoan tat thanh cong." -ForegroundColor Green
} else {
    Write-Host "`n[2/5] Bo qua buoc bien dich Gradle (-SkipBuild duoc kich hoat)." -ForegroundColor DarkGray
}

# ------------------------------------------------------------------------------
# 5. BUILD DOCKER IMAGE LOCAL & PUSH LEN REGISTRY
# ------------------------------------------------------------------------------
Write-Host "`n[3/5] Build Docker Images va Push len Registry ($imagePrefix)..." -ForegroundColor Yellow

foreach ($svc in $targetServices) {
    $svcDir = $svc.dir
    $fullImageTag = "$($imagePrefix)/$($svcDir):latest"

    Write-Host "`n  ------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "  [*] Dang build Docker Image: $fullImageTag" -ForegroundColor Cyan
    Write-Host "  ------------------------------------------------------------" -ForegroundColor DarkGray

    cmd /c "docker build -t `"$fullImageTag`" `"$svcDir`""
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[LOI] Build Docker Image that bai cho service $svcDir!" -ForegroundColor Red
        exit $LASTEXITCODE
    }

    if (-not $SkipPush) {
        Write-Host "  -> Dang push image len Registry: $fullImageTag..." -ForegroundColor Cyan
        cmd /c "docker push `"$fullImageTag`""
        if ($LASTEXITCODE -ne 0) {
            Write-Host "`n[LOI] Push image len Registry that bai!" -ForegroundColor Red
            Write-Host "[HUONG DAN] Neu loi Unauthorized / Access Denied, hay dang nhap GHCR bang lenh sau:" -ForegroundColor Yellow
            Write-Host "  echo <GITHUB_PAT_TOKEN> | docker login ghcr.io -u <GITHUB_USERNAME> --password-stdin" -ForegroundColor Yellow
            exit $LASTEXITCODE
        }
        Write-Host "  [OK] Da push image thanh cong!" -ForegroundColor Green
    }
}

if ($SkipPush) {
    Write-Host "`n  [i] Bo qua buoc Docker Push (-SkipPush duoc kich hoat)." -ForegroundColor DarkGray
}

# ------------------------------------------------------------------------------
# 6. SSH CAP NHAT VA TAI KHOI DONG DICH VU TREN TUNG VPS
# ------------------------------------------------------------------------------
Write-Host "`n[4/5] Gui tin hieu SSH cap nhat va reload containers tren cac VPS..." -ForegroundColor Yellow

$deploySummary = @()

foreach ($node in $aliveNodes) {
    $keyPath = Resolve-UserPath $node.keyPath
    $hostAddr = $node.host
    $port = $node.port
    $user = $node.user
    $targetDir = $node.targetDir
    $composeFile = $node.composeFile

    # Tim cac service thuoc node nay can reload
    $nodeSvcs = $targetServices | Where-Object { $_.node -eq $node.id }
    if ($nodeSvcs.Count -eq 0) { continue }

    $composeSvcNames = ($nodeSvcs | ForEach-Object { $_.composeService }) -join " "

    Write-Host "`n================================================================" -ForegroundColor Cyan
    Write-Host "  DANG TRIEN KHAI LEN: $($node.name) ($hostAddr)" -ForegroundColor Cyan
    Write-Host "  Services: $composeSvcNames" -ForegroundColor Cyan
    Write-Host "================================================================" -ForegroundColor Cyan

    # Lenh thuc thi tu xa tren VPS
    $remoteBash = @"
set -e
export IMAGE_REPO="$($config.registry.owner)"
cd $targetDir

echo "[1/4] Kiem tra va cap nhat file cau hinh compose moi nhat tu Git..."
if [ -d ".git" ]; then
    git fetch origin main 2>/dev/null || true
    git checkout origin/main -- docker/compose 2>/dev/null || true
fi

echo "[2/4] Pull Docker Images moi nhat ve VPS..."
docker compose -p docker -f $composeFile pull $composeSvcNames

echo "[3/4] Recreate va khoi dong lai container..."
docker compose -p docker -f $composeFile up -d --remove-orphans $composeSvcNames

echo "[4/4] Don dep image cu (Prune Dangling Images)..."
docker image prune -f

echo ""
echo "Trang thai cac container tren VPS:"
docker ps --filter "name=smart-parking" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
"@

    # Chay SSH thuc thi script tu xa qua stdin (bash -s)
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $remoteBash | & ssh -i $keyPath -p $port -o StrictHostKeyChecking=no "$user@$hostAddr" "tr -d '\r' | bash"
    } finally {
        $ErrorActionPreference = $prevEAP
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host "-> [THANH CONG] Hoan tat deploy tren $($node.name)!" -ForegroundColor Green
        $deploySummary += @{ Node = $node.name; Status = "OK"; Services = $composeSvcNames }
    } else {
        Write-Host "-> [THAT BAI] Co loi trong qua trinh deploy tren $($node.name)!" -ForegroundColor Red
        $deploySummary += @{ Node = $node.name; Status = "FAILED"; Services = $composeSvcNames }
    }
}

# ------------------------------------------------------------------------------
# 7. TONG KET KET QUA TRIEN KHAI
# ------------------------------------------------------------------------------
Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "   [5/5] BANG TONG KET CAP NHAT VPS CLUSTER" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green

foreach ($res in $deploySummary) {
    $stColor = if ($res.Status -eq "OK") { "Green" } else { "Red" }
    Write-Host " Node: $($res.Node.PadRight(30)) Status: " -NoNewline
    Write-Host "[$($res.Status)]" -ForegroundColor $stColor -NoNewline
    Write-Host " Services: $($res.Services)"
}

Write-Host "================================================================" -ForegroundColor Green
Write-Host " Hoan tat toan bo tien trinh!" -ForegroundColor Green
