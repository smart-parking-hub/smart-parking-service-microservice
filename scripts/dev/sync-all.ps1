# ==============================================================================
# Script: sync-all.ps1
# Description: Đồng bộ toàn diện: Build JAR mới + Chạy Docker + Tự động Migrate DB
# Dành cho các thành viên chạy ngay sau khi pull code mới về
# ==============================================================================

param (
    [ValidateSet("local-dev", "local-full", "local-integration")]
    [string]$Mode = "local-dev",      # Existing presets plus opt-in Kafka integration
    [switch]$CleanDb = $false,        # Xóa sạch volume DB cũ để migrate lại từ đầu
    [switch]$SkipBuild = $false,      # Bỏ qua bước compile JAR nếu vừa compile xong
    [string]$Service = ""             # Cập nhật riêng 1 service nếu có chỉ định
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$ProjectName = "docker"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir\..\.."
Set-Location $ProjectRoot

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   SMARTPARKINGHUB - DONG BO TOAN DIEN DOCKER & DATABASE" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 0. Thong tin Git hien tai
try {
    $branch = git rev-parse --abbrev-ref HEAD 2>$null
    $commit = git log -1 --oneline 2>$null
    Write-Host "[GIT] Nhanh hien tai: $branch | Commit: $commit" -ForegroundColor Gray
} catch {}

# 0.1 Kiem tra va khoi tao tep .env neu chua co
$envFile = Join-Path $ProjectRoot ".env"
$envExample = Join-Path $ProjectRoot ".env.example"
if (-not (Test-Path $envFile)) {
    if (Test-Path $envExample) {
        Write-Host "[SETUP] Chua co tep .env. Tu dong sao chep tu .env.example..." -ForegroundColor Cyan
        Copy-Item $envExample $envFile
    } else {
        Write-Host "[CANH BAO] Khong tim thay tep .env va .env.example!" -ForegroundColor Yellow
    }
} else {
    Write-Host "[ENV] Tep .env da san sang day du." -ForegroundColor Gray
}

# 1. Kiem tra ket noi Docker Daemon
Write-Host "`n[1/5] Kiem tra Docker Daemon..." -ForegroundColor Yellow
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

    Write-Host "Dang cho Docker Daemon khoi dong (toi da 60 giay)..." -ForegroundColor Yellow
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
        Write-Host "[LOI] Khong the ket noi toi Docker Daemon. Vui long mo Docker Desktop truoc!" -ForegroundColor Red
        exit 1
    }
}
Write-Host "-> Docker Daemon dang hoat dong tot." -ForegroundColor Green

# 2. Xử lý Reset Database (Nếu người dùng chọn CleanDb)
if ($CleanDb) {
    Write-Host "`n[CANH BAO] Dang thuc hien RESET SACH DATABASE..." -ForegroundColor Magenta
    Write-Host "Dung cac container va xoa sach cac volume du lieu cu..." -ForegroundColor Yellow
    
    cmd /c "docker compose -p $ProjectName --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml down -v"
    
    # Xoa triet de cac container cu neu bi ket
    $strayContainers = docker ps -a --filter "name=smart-parking-" --format "{{.ID}}"
    if ($strayContainers) {
        Write-Host "Don dep cac container cu bi xung dot..." -ForegroundColor Gray
        $strayContainers | ForEach-Object { docker rm -f $_ 2>&1 | Out-Null }
    }

    # Xoa them named volume neu con sot
    $volumesToClean = @("docker_mysql_data", "mysql_data", "docker_redis_data", "redis_data")
    foreach ($v in $volumesToClean) {
        try {
            $null = docker volume rm $v 2>&1
        } catch {}
    }
    Write-Host "-> Da lam sach toan bo Database Volume cu. MySQL se khoi tao lai tu dau." -ForegroundColor Green
}

# 3. Khoi dong tang Ha tang (Infrastructure: MySQL, Redis) & Cho MySQL Healthy
Write-Host "`n[2/5] Khoi dong Ha tang va cho MySQL san sang..." -ForegroundColor Yellow

# Đảm bảo network smart-parking-net tồn tại (Idempotent - không báo lỗi nếu đã có)
$networkExists = $false
try {
    $netCheck = docker network ls --filter "name=^smart-parking-net$" --format "{{.Name}}" 2>&1
    if ($netCheck -match "smart-parking-net") { $networkExists = $true }
} catch {}
if (-not $networkExists) {
    Write-Host "Khoi tao Docker network smart-parking-net..." -ForegroundColor Gray
    $null = docker network create smart-parking-net 2>&1
} else {
    Write-Host "-> Docker network smart-parking-net da san sang." -ForegroundColor Gray
}

# Khoi dong MySQL & Redis truoc
$infraComposeCmd = "docker compose -p $ProjectName --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml"
if ($Mode -eq "local-full") {
    $infraComposeCmd += " -f docker\compose\local\compose.local-full.yaml"
} elseif ($Mode -eq "local-integration") {
    $infraComposeCmd += " -f docker\compose\local\compose.kafka.yaml"
}
if ($Mode -in @("local-full", "local-integration")) {
    cmd /c "$infraComposeCmd up -d mysql redis kafka"
} else {
    cmd /c "$infraComposeCmd up -d mysql redis"
}

# Neu phat hien xung dot container ha tang cu, tu dong don dep container ha tang va thu lai
if ($LASTEXITCODE -ne 0) {
    Write-Host "[CANH BAO] Phat hien xung dot container ha tang cu. Dang tu dong don dep va thu lai..." -ForegroundColor Yellow
    $infraContainers = @("smart-parking-mysql", "smart-parking-redis")
    if ($Mode -in @("local-full", "local-integration")) { $infraContainers += "smart-parking-kafka" }
    foreach ($c in $infraContainers) {
        $null = docker rm -f $c 2>&1
    }
    if ($Mode -in @("local-full", "local-integration")) {
        cmd /c "$infraComposeCmd up -d mysql redis kafka"
    } else {
        cmd /c "$infraComposeCmd up -d mysql redis"
    }
}

Write-Host "Dang kiem tra trang thai Healthcheck cua MySQL..." -ForegroundColor Yellow
$mysqlReady = $false
$retryMySql = 0
while ($retryMySql -lt 30) {
    try {
        $status = docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" smart-parking-mysql 2>&1
        if ($status -eq "healthy" -or $status -eq "running") {
            # Thu ping truc tiep vao MySQL
            $ping = docker exec smart-parking-mysql mysqladmin ping -h localhost -uroot -proot 2>&1
            if ($ping -match "mysqld is alive") {
                $mysqlReady = $true
                break
            }
        }
    } catch {}
    Start-Sleep -Seconds 2
    $retryMySql++
}

if ($mysqlReady) {
    Write-Host "-> MySQL da san sang ket noi (Port 3307 / 3306)." -ForegroundColor Green
} else {
    Write-Host "[CANH BAO] MySQL chua phan hoi ping, nhung se tiep tuc tien trinh..." -ForegroundColor Yellow
}

# 4. Bien dich ma nguon Backend bang Gradle
if (-not $SkipBuild) {
    $gradleTask = "bootJar -x test"
    if ($Service -ne "") {
        $serviceMap = @{
            "identity" = ":identity-service:bootJar -x test"
            "identity-service" = ":identity-service:bootJar -x test"
            "booking" = ":booking-service:bootJar -x test"
            "booking-service" = ":booking-service:bootJar -x test"
            "parking" = ":parking-lot-service:bootJar -x test"
            "parking-lot" = ":parking-lot-service:bootJar -x test"
            "parking-lot-service" = ":parking-lot-service:bootJar -x test"
            "payment" = ":payment-service:bootJar -x test"
            "payment-service" = ":payment-service:bootJar -x test"
            "notification" = ":notification-service:bootJar -x test"
            "notification-service" = ":notification-service:bootJar -x test"
            "ai" = ":ai-service:bootJar -x test"
            "ai-service" = ":ai-service:bootJar -x test"
            "gateway" = ":api-gateway:bootJar -x test"
            "api-gateway" = ":api-gateway:bootJar -x test"
        }
        $key = $Service.ToLower()
        if ($serviceMap.ContainsKey($key)) {
            $gradleTask = $serviceMap[$key]
            Write-Host "`n[3/5] Bien dich rieng cho service: $Service ($gradleTask)..." -ForegroundColor Yellow
        } else {
            Write-Host "`n[3/5] Bien dich toan bo ma nguon Backend ($gradleTask)..." -ForegroundColor Yellow
        }
    } else {
        Write-Host "`n[3/5] Bien dich toan bo ma nguon Backend & dong goi Flyway SQL ($gradleTask)..." -ForegroundColor Yellow
    }

    cmd /c ".\gradlew.bat $gradleTask"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n[LOI BIEN DICH] Gradle build that bai! Vui long kiem tra code hoac loi cu phap truoc." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "-> Bien dich JAR thanh cong. Toan bo ma nguon va file Flyway moi nhat da duoc dong goi." -ForegroundColor Green
} else {
    Write-Host "`n[3/5] Bo qua buoc bien dich Gradle (--SkipBuild)." -ForegroundColor Gray
}

# 5. Build Docker Image & Recreate Container
Write-Host "`n[4/5] Build Docker Images va Recreate Containers..." -ForegroundColor Yellow

$composeCmd = "docker compose -p $ProjectName --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml"
if ($Mode -eq "local-full") {
    $composeCmd += " -f docker\compose\local\compose.local-full.yaml"
} elseif ($Mode -eq "local-integration") {
    $composeCmd += " -f docker\compose\local\compose.kafka.yaml"
}

$allMicroservices = "api-gateway identity-service parking-lot-service booking-service payment-service notification-service ai-service"

if ($Service -ne "") {
    $targetContainer = $Service
    if (-not $targetContainer.EndsWith("-service") -and $targetContainer -ne "api-gateway") {
        $targetContainer += "-service"
    }
    cmd /c "$composeCmd up -d --build --force-recreate $targetContainer"
} else {
    # Chi cap nhat va tai tao cac container Microservices, giu nguyen MySQL va Redis dang chay
    cmd /c "$composeCmd up -d --build --force-recreate $allMicroservices"
}

# Neu dinh loi conflict ten container, chi go bo cac container microservices bi chiem ten va chay lai
if ($LASTEXITCODE -ne 0) {
    Write-Host "[CANH BAO] Phat hien xung dot container khi build/up. Dang tu dong go bo container microservices bi chiem ten..." -ForegroundColor Yellow
    $appContainers = @("smart-parking-gateway", "smart-parking-identity", "smart-parking-parkinglot", "smart-parking-booking", "smart-parking-payment", "smart-parking-notification", "smart-parking-ai")
    foreach ($c in $appContainers) {
        $null = docker rm -f $c 2>&1
    }
    if ($Service -ne "") {
        cmd /c "$composeCmd up -d --build --force-recreate $targetContainer"
    } else {
        cmd /c "$composeCmd up -d --build --force-recreate $allMicroservices"
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[LOI] Docker Compose build/up that bai!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "-> Rebuild containers thanh cong." -ForegroundColor Green

# 6. Kiem tra dong bo Database va Flyway Migrations
Write-Host "`n[5/5] Dang giam sat tien do khoi dong va Flyway Migrations cua cac Service..." -ForegroundColor Yellow
Write-Host "Dang cho Spring Boot khoi dong va chay Flyway (toi da 30 giay)..." -ForegroundColor Gray

$servicesToCheck = @(
    @{ name = "Booking Service"; container = "smart-parking-booking" }
    @{ name = "Payment Service"; container = "smart-parking-payment" }
    @{ name = "Identity Service"; container = "smart-parking-identity" }
    @{ name = "Parking Lot Service"; container = "smart-parking-parkinglot" }
    @{ name = "Notification Service"; container = "smart-parking-notification" }
)

$allDone = $false
$pollRetry = 0
while ($pollRetry -lt 6 -and -not $allDone) {
    Start-Sleep -Seconds 5
    $pollRetry++
    
    $readyCount = 0
    foreach ($s in $servicesToCheck) {
        $cName = $s.container
        try {
            $logs = docker logs --tail 50 $cName 2>&1
            if ($logs -match "Tomcat started on port|Started \w+Application|Successfully applied|Schema .+ is up to date") {
                $readyCount++
            }
        } catch {}
    }
    if ($readyCount -ge 3) {
        $allDone = $true
        break
    }
}

Write-Host "----------------------------------------------------------------" -ForegroundColor Gray
foreach ($s in $servicesToCheck) {
    $cName = $s.container
    $sTitle = $s.name
    try {
        $logs = docker logs --tail 100 $cName 2>&1
        $appliedMatch = $logs | Select-String -Pattern "Successfully applied (\d+) migration" | Select-Object -Last 1
        $upToDateMatch = $logs | Select-String -Pattern "Schema .+ is up to date" | Select-Object -Last 1
        $errorMatch = $logs | Select-String -Pattern "FlywayException|Validate failed|Migration checksum mismatch" | Select-Object -Last 1

        if ($errorMatch) {
            Write-Host " [!] $sTitle : PHAT HIEN XUNG DOT FLYWAY!" -ForegroundColor Red
            Write-Host "     -> $($errorMatch.Line.Trim())" -ForegroundColor Red
            Write-Host "     -> Goi y: Chon menu 1.6 de Reset sach DB neu bi loi Checksum." -ForegroundColor Yellow
        } elseif ($appliedMatch) {
            Write-Host " [✓] $sTitle : $($appliedMatch.Line.Trim())" -ForegroundColor Green
        } elseif ($upToDateMatch) {
            Write-Host " [✓] $sTitle : Schema Database da o phien ban moi nhat." -ForegroundColor Green
        } else {
            Write-Host " [✓] $sTitle : Service da san sang hoat dong tren Docker." -ForegroundColor Green
        }
    } catch {
        Write-Host " [?] $sTitle : Chua the lay log tu container $cName." -ForegroundColor Gray
    }
}
Write-Host "----------------------------------------------------------------" -ForegroundColor Gray

# 7. Trang thai cac container
Write-Host "`nTrang thai cac container Docker hien tai:" -ForegroundColor Yellow
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "   DA HOAN TAT DONG BO MICROSERVICES VA DATABASE!" -ForegroundColor Green
Write-Host "   - API Gateway:    http://localhost:8088" -ForegroundColor Green
Write-Host "   - Identity:       http://localhost:8081" -ForegroundColor Green
Write-Host "   - Parking Lot:    http://localhost:8082" -ForegroundColor Green
Write-Host "   - Booking:        http://localhost:8083" -ForegroundColor Green
Write-Host "   - Payment:        http://localhost:8084" -ForegroundColor Green
Write-Host "   - Notification:   http://localhost:8085" -ForegroundColor Green
Write-Host "   - AI Service:     http://localhost:8086" -ForegroundColor Green
Write-Host "   - MySQL:          localhost:3307 (root/root)" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
