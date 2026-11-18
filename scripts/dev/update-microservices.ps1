# ==============================================================================
# Script: update-microservices.ps1
# Description: Tự động biên dịch JAR và cập nhật các microservice lên Docker
# ==============================================================================

param (
    [string]$Service = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir\..\.."
Set-Location $ProjectRoot

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   SMARTPARKINGHUB - DOCKER MICROSERVICES AUTO-UPDATE" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Kiểm tra Docker Daemon
Write-Host "`n[1/4] Kiem tra ket noi Docker Daemon..." -ForegroundColor Yellow
$dockerReady = $false
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        $dockerReady = $true
    }
} catch {
    $dockerReady = $false
}

if (-not $dockerReady) {
    Write-Host "[CANH BAO] Docker Daemon chua chay. Dang thu mo Docker Desktop..." -ForegroundColor Yellow
    $dockerDesktopExe = "C:\Users\Admin\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"
    if (Test-Path $dockerDesktopExe) {
        Start-Process $dockerDesktopExe
    } else {
        $altExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        if (Test-Path $altExe) {
            Start-Process $altExe
        }
    }

    Write-Host "Dang cho Docker Daemon khoi dong..." -ForegroundColor Yellow
    $retry = 0
    while ($retry -lt 30) {
        Start-Sleep -Seconds 2
        try {
            $null = docker info 2>&1
            if ($LASTEXITCODE -eq 0) {
                $dockerReady = $true
                break
            }
        } catch {}
        $retry++
    }

    if (-not $dockerReady) {
        Write-Host "[LOI] Khong the ket noi toi Docker Daemon. Vui long mo Docker Desktop truoc roi chay lai!" -ForegroundColor Red
        exit 1
    }
}
Write-Host "-> Docker Daemon dang hoat dong tot." -ForegroundColor Green

# 2. Xac dinh target service
$gradleTask = "bootJar -x test"
$composeService = ""

if ($Service -ne "") {
    $serviceMap = @{
        "identity" = @{ task = ":identity-service:bootJar -x test"; svc = "identity-service" }
        "identity-service" = @{ task = ":identity-service:bootJar -x test"; svc = "identity-service" }
        "booking" = @{ task = ":booking-service:bootJar -x test"; svc = "booking-service" }
        "booking-service" = @{ task = ":booking-service:bootJar -x test"; svc = "booking-service" }
        "parking" = @{ task = ":parking-lot-service:bootJar -x test"; svc = "parking-lot-service" }
        "parking-lot" = @{ task = ":parking-lot-service:bootJar -x test"; svc = "parking-lot-service" }
        "parking-lot-service" = @{ task = ":parking-lot-service:bootJar -x test"; svc = "parking-lot-service" }
        "payment" = @{ task = ":payment-service:bootJar -x test"; svc = "payment-service" }
        "payment-service" = @{ task = ":payment-service:bootJar -x test"; svc = "payment-service" }
        "notification" = @{ task = ":notification-service:bootJar -x test"; svc = "notification-service" }
        "notification-service" = @{ task = ":notification-service:bootJar -x test"; svc = "notification-service" }
        "ai" = @{ task = ":ai-service:bootJar -x test"; svc = "ai-service" }
        "ai-service" = @{ task = ":ai-service:bootJar -x test"; svc = "ai-service" }
        "gateway" = @{ task = ":api-gateway:bootJar -x test"; svc = "api-gateway" }
        "api-gateway" = @{ task = ":api-gateway:bootJar -x test"; svc = "api-gateway" }
    }

    $key = $Service.ToLower()
    if ($serviceMap.ContainsKey($key)) {
        $gradleTask = $serviceMap[$key].task
        $composeService = $serviceMap[$key].svc
        Write-Host "`nMuc tieu cap nhat: $composeService" -ForegroundColor Cyan
    } else {
        Write-Host "`n[CANH BAO] Khong tim thay service '$Service'. Mac dinh cap nhat toan bo microservices." -ForegroundColor Yellow
    }
} else {
    Write-Host "`nMuc tieu cap nhat: TOAN BO MICROSERVICES" -ForegroundColor Cyan
}

# 3. Bien dich Gradle
Write-Host "`n[2/4] Bien dich ma nguon Backend (Gradle: $gradleTask)..." -ForegroundColor Yellow
cmd /c ".\gradlew.bat $gradleTask"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[LOI] Bien dich Gradle that bai! Vui long kiem tra code truoc." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "-> Bien dich JAR hoan tat thanh cong." -ForegroundColor Green

# 4. Build Docker & Recreate Container
Write-Host "`n[3/4] Build lai Docker Image va Recreate Container tren Docker..." -ForegroundColor Yellow

# Đảm bảo network smart-parking-net tồn tại (Idempotent - không báo lỗi nếu đã có)
$networkExists = $false
try {
    $netCheck = docker network ls --filter "name=^smart-parking-net$" --format "{{.Name}}" 2>&1
    if ($netCheck -match "smart-parking-net") { $networkExists = $true }
} catch {}
if (-not $networkExists) {
    Write-Host "Khoi tao Docker network smart-parking-net..." -ForegroundColor Gray
    $null = docker network create smart-parking-net 2>&1
}

$composeCmd = "docker compose -p docker --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml"
$allMicroservices = "api-gateway identity-service parking-lot-service booking-service payment-service notification-service ai-service"

if ($composeService -ne "") {
    cmd /c "$composeCmd up -d --build --force-recreate $composeService"
} else {
    cmd /c "$composeCmd up -d --build --force-recreate $allMicroservices"
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[CANH BAO] Phat hien container cu bi xung dot. Dang tu dong go bo container microservices va thu lai..." -ForegroundColor Yellow
    $appContainers = @("smart-parking-gateway", "smart-parking-identity", "smart-parking-parkinglot", "smart-parking-booking", "smart-parking-payment", "smart-parking-notification", "smart-parking-ai")
    foreach ($c in $appContainers) {
        $null = docker rm -f $c 2>&1
    }
    if ($composeService -ne "") {
        cmd /c "$composeCmd up -d --build --force-recreate $composeService"
    } else {
        cmd /c "$composeCmd up -d --build --force-recreate $allMicroservices"
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[LOI] Docker Compose build/up that bai!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "-> Docker containers da duoc cap nhat thanh cong." -ForegroundColor Green

# 5. Kiem tra trang thai
Write-Host "`n[4/4] Trang thai cac container tren Docker:" -ForegroundColor Yellow
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "   DA HOAN TAT CAP NHAT MICROSERVICES MOI NHAT LEN DOCKER!" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
