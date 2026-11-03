# PowerShell script to check git branch synchronization
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "Dang cap nhat metadata tu remote (git fetch --all --prune)..." -ForegroundColor DarkGray
git fetch --all --prune 2>$null

Write-Host "`n========================================================================" -ForegroundColor Cyan
Write-Host "   SMARTPARKINGHUB — KIEM TRA TRANG THAI DONG BO CAC NHANH GIT          " -ForegroundColor Cyan
Write-Host "========================================================================`n" -ForegroundColor Cyan

# 1. Kiem tra working tree hien tai
$currentBranch = (git rev-parse --abbrev-ref HEAD 2>$null).Trim()
Write-Host "Nhanh dang lam viec: " -NoNewline
Write-Host "$currentBranch" -ForegroundColor Green

$statusOutput = (git status --short 2>$null)
if ($statusOutput) {
    Write-Host "Canh bao: Co thay doi chua commit trong working directory:" -ForegroundColor Yellow
    $statusOutput | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
} else {
    Write-Host "Working directory sach se (khong co thay doi do dang)." -ForegroundColor Green
}
Write-Host ""

# 2. Lay danh sach nhanh da merge vao main
$mergedIntoMain = @()
try {
    $mergedLines = git branch --merged main 2>$null
    if ($mergedLines) {
        $mergedIntoMain = $mergedLines | ForEach-Object { $_.Trim().TrimStart('*').Trim() }
    }
} catch {
    # Bo qua neu loi
}

# 3. Lay danh sach tat ca cac nhanh kem upstream tu git for-each-ref
$rawRefs = git for-each-ref --format="%(refname:short)|%(upstream:short)" refs/heads/ 2>$null
if (-not $rawRefs) {
    Write-Host "Khong tim thay nhanh local nao!" -ForegroundColor Red
    exit 0
}

Write-Host "BANG TONG HOP DONG BO CAC NHANH:" -ForegroundColor White
Write-Host ("-" * 72) -ForegroundColor DarkGray
Write-Host ("{0,-36} {1,-22} {2}" -f "TEN NHANH", "UPSTREAM", "TRANG THAI") -ForegroundColor Cyan
Write-Host ("-" * 72) -ForegroundColor DarkGray

$branchesToPush = @()
$branchesToPull = @()
$mergedList = @()

foreach ($line in $rawRefs) {
    if (-not $line) { continue }
    $parts = $line.Split('|')
    $branch = $parts[0].Trim()
    $upstream = if ($parts.Length -gt 1) { $parts[1].Trim() } else { "" }
    
    $isCurrent = ($branch -eq $currentBranch)
    $displayName = if ($isCurrent) { "* $branch" } else { "  $branch" }
    $displayUpstream = if ($upstream) { $upstream } else { "[Chua co]" }
    
    $ahead = 0
    $behind = 0
    $statusText = ""
    $statusColor = "Green"
    
    if (-not $upstream) {
        $statusText = "Chua set upstream"
        $statusColor = "DarkYellow"
    } else {
        $aheadStr = (git rev-list --count "$upstream..$branch" 2>$null)
        if ($aheadStr) { [int]::TryParse($aheadStr.Trim(), [ref]$ahead) | Out-Null }
        
        $behindStr = (git rev-list --count "$branch..$upstream" 2>$null)
        if ($behindStr) { [int]::TryParse($behindStr.Trim(), [ref]$behind) | Out-Null }
        
        if ($ahead -gt 0 -and $behind -gt 0) {
            $statusText = "Bi lech (Ahead $ahead, Behind $behind)"
            $statusColor = "Red"
            $branchesToPush += [PSCustomObject]@{ Branch = $branch; Upstream = $upstream; Ahead = $ahead }
            $branchesToPull += [PSCustomObject]@{ Branch = $branch; Upstream = $upstream; Behind = $behind }
        } elseif ($ahead -gt 0) {
            $statusText = "Chua push: $ahead commit"
            $statusColor = "Yellow"
            $branchesToPush += [PSCustomObject]@{ Branch = $branch; Upstream = $upstream; Ahead = $ahead }
        } elseif ($behind -gt 0) {
            $statusText = "Can pull: $behind commit"
            $statusColor = "Blue"
            $branchesToPull += [PSCustomObject]@{ Branch = $branch; Upstream = $upstream; Behind = $behind }
        } else {
            $statusText = "Dong bo 100%"
            $statusColor = "Green"
        }
    }
    
    $isMerged = ($branch -ne "main" -and ($mergedIntoMain -contains $branch))
    if ($isMerged) {
        $statusText += " [Da merge main]"
        $mergedList += $branch
    }
    
    Write-Host ("{0,-36} {1,-22} " -f $displayName, $displayUpstream) -NoNewline
    Write-Host $statusText -ForegroundColor $statusColor
}

Write-Host ("-" * 72) -ForegroundColor DarkGray
Write-Host ""

# 4. Chi tiet cac commit chua push
if ($branchesToPush.Count -gt 0) {
    Write-Host "=== CHI TIET CAC COMMIT CHUA PUSH LEN REMOTE (AHEAD) ===" -ForegroundColor Yellow
    foreach ($item in $branchesToPush) {
        Write-Host "`nNhanh: $($item.Branch) ($($item.Ahead) commit chua push len $($item.Upstream)):" -ForegroundColor Yellow
        git log --oneline "$($item.Upstream)..$($item.Branch)" 2>$null | ForEach-Object { Write-Host "  ^ $_" -ForegroundColor Yellow }
        Write-Host "  Goi y lenh push: " -NoNewline -ForegroundColor DarkGray
        Write-Host "git push origin $($item.Branch)" -ForegroundColor Cyan
    }
    Write-Host ""
}

# 5. Chi tiet cac commit tren remote chua pull
if ($branchesToPull.Count -gt 0) {
    Write-Host "=== CHI TIET CAC COMMIT TREN REMOTE CHUA PULL (BEHIND) ===" -ForegroundColor Blue
    foreach ($item in $branchesToPull) {
        Write-Host "`nNhanh: $($item.Branch) ($($item.Behind) commit moi tren $($item.Upstream)):" -ForegroundColor Blue
        git log --oneline "$($item.Branch)..$($item.Upstream)" 2>$null | ForEach-Object { Write-Host "  v $_" -ForegroundColor Blue }
        Write-Host "  Goi y lenh pull: " -NoNewline -ForegroundColor DarkGray
        Write-Host "git checkout $($item.Branch) ; git pull" -ForegroundColor Cyan
    }
    Write-Host ""
}

# 6. Danh sach nhanh da merge vao main
if ($mergedList.Count -gt 0) {
    Write-Host "=== CAC NHANH DA MERGE XONG VAO MAIN (CO THE DON DEP) ===" -ForegroundColor Magenta
    foreach ($mb in $mergedList) {
        Write-Host "  - $mb -> " -NoNewline -ForegroundColor Magenta
        Write-Host "git branch -d $mb" -ForegroundColor DarkGray
    }
    Write-Host ""
}

# 7. Tong ket hanh dong
Write-Host "HOAN TAT KIEM TRA!" -ForegroundColor Cyan
Write-Host ""
