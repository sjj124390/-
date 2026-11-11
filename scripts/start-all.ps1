<#
.SYNOPSIS
    在线购物系统 · 一键启动（数据库 + 后端 + 前端）

.DESCRIPTION
    1. 检查并启动本机 MySQL 服务
    2. 初始化数据库（可选：-ResetDb 会清空重建）
    3. 构建后端 jar（缺失或指定 -Build 时）
    4. 启动后端 8080
    5. 启动前端静态服务器 5500

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
    powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 -ResetDb
#>
param(
    [string]$DbUser = "root",
    [string]$DbPassword = "123456",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5500,
    [switch]$ResetDb,
    [switch]$Build
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$logDir = Join-Path $root "logs"
$jarPath = Join-Path $backendDir "target\online-shopping-backend-1.0.0.jar"
$schemaPath = Join-Path $backendDir "src\main\resources\sql\schema-memberC.sql"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-Step($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $handle = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        if ($handle.AsyncWaitHandle.WaitOne(800, $false) -and $client.Connected) { return $true }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Find-MySql {
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = Get-ChildItem "C:\Program Files\MySQL" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending
    foreach ($dir in $candidates) {
        $exe = Join-Path $dir.FullName "bin\mysql.exe"
        if (Test-Path $exe) { return $exe }
    }
    throw "未找到 mysql.exe，请安装 MySQL 8 或把 bin 目录加入 PATH"
}

function Find-Maven {
    if ($env:MAVEN_HOME -and (Test-Path (Join-Path $env:MAVEN_HOME "bin\mvn.cmd"))) {
        return (Join-Path $env:MAVEN_HOME "bin\mvn.cmd")
    }
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = Get-ChildItem "$env:USERPROFILE\tools" -Directory -Filter "apache-maven-*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending
    foreach ($dir in $candidates) {
        $exe = Join-Path $dir.FullName "bin\mvn.cmd"
        if (Test-Path $exe) { return $exe }
    }
    return $null
}

# ---------- 1. MySQL ----------
Write-Step "检查 MySQL 服务"
$svc = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
if (-not $svc) { $svc = Get-Service | Where-Object { $_.Name -match "mysql|maria" } | Select-Object -First 1 }
if ($svc -and $svc.Status -ne "Running") {
    Write-Host "启动服务 $($svc.Name) ..."
    Start-Service $svc.Name
    Start-Sleep -Seconds 2
}
if (-not (Test-Port 3306)) { throw "MySQL 3306 端口未就绪，请确认数据库已安装并启动" }
Write-Host "MySQL 已就绪（3306）" -ForegroundColor Green

$mysql = Find-MySql

# ---------- 2. 数据库初始化 ----------
if ($ResetDb) {
    Write-Step "重建数据库 single_item_shop（-ResetDb）"
    $dropArgs = @("-u$DbUser", "-p$DbPassword", "-e", "DROP DATABASE IF EXISTS single_item_shop;")
    & $mysql @dropArgs 2>&1 | Out-Null
}

Write-Step "初始化数据库结构"
$tmpSchema = Join-Path $env:TEMP "single_item_shop_schema.sql"
Copy-Item $schemaPath $tmpSchema -Force
$schemaForSource = $tmpSchema.Replace("\", "/")
$sourceArgs = @("-u$DbUser", "-p$DbPassword", "--default-character-set=utf8mb4", "-e", "source $schemaForSource")
& $mysql @sourceArgs 2>&1 | Out-Null

# 以「表是否齐全」作为初始化成功的判定，避免依赖 mysql 客户端退出码
$countArgs = @("-u$DbUser", "-p$DbPassword", "-N", "-B", "-e",
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='single_item_shop';")
$tableCount = (& $mysql @countArgs 2>$null) | Select-Object -Last 1
if (-not $tableCount -or [int]$tableCount -lt 4) {
    throw "数据库初始化失败，请检查账号密码（-DbUser/-DbPassword）"
}
Write-Host "数据库结构已就绪（表 + 存储过程）" -ForegroundColor Green

# ---------- 3. 构建后端 ----------
if ($Build -or -not (Test-Path $jarPath)) {
    Write-Step "构建后端 jar（首次构建需下载依赖，耗时较长）"
    $mvn = Find-Maven
    if (-not $mvn) { throw "未找到 Maven。请安装 Maven 后重试，或用 IDE 打包出 $jarPath" }
    Push-Location $backendDir
    try {
        & $mvn -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }
    } finally {
        Pop-Location
    }
    Write-Host "后端构建完成：$jarPath" -ForegroundColor Green
}

# ---------- 4. 启动后端 ----------
Write-Step "启动后端（$BackendPort）"
if (Test-Port $BackendPort) {
    Write-Host "端口 $BackendPort 已被占用，跳过启动（如需重启请先执行 scripts\stop-all.ps1）" -ForegroundColor Yellow
} else {
    $outLog = Join-Path $logDir "backend.out.log"
    $errLog = Join-Path $logDir "backend.err.log"
    Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $jarPath, "--server.port=$BackendPort", "--spring.datasource.username=$DbUser", "--spring.datasource.password=$DbPassword") `
        -WorkingDirectory $backendDir -WindowStyle Hidden `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 1000
        if (Test-Port $BackendPort) { $ok = $true; break }
    }
    if (-not $ok) { throw "后端启动超时，请查看 logs\backend.out.log" }
    Write-Host "后端已启动：http://localhost:$BackendPort/api" -ForegroundColor Green
}

# ---------- 5. 启动前端 ----------
Write-Step "启动前端静态服务器（$FrontendPort）"
if (Test-Port $FrontendPort) {
    Write-Host "端口 $FrontendPort 已被占用，跳过启动" -ForegroundColor Yellow
} else {
    $feOut = Join-Path $logDir "frontend.out.log"
    $feErr = Join-Path $logDir "frontend.err.log"
    Start-Process -FilePath "python" `
        -ArgumentList @("-m", "http.server", "$FrontendPort", "--bind", "127.0.0.1") `
        -WorkingDirectory $frontendDir -WindowStyle Hidden `
        -RedirectStandardOutput $feOut -RedirectStandardError $feErr
    $ok = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Port $FrontendPort) { $ok = $true; break }
    }
    if (-not $ok) { throw "前端静态服务器启动超时，请查看 logs\frontend.log" }
    Write-Host "前端已启动：http://127.0.0.1:$FrontendPort/" -ForegroundColor Green
}

Write-Host ""
Write-Host "================ 系统已就绪 ================" -ForegroundColor Green
Write-Host "买家前台： http://127.0.0.1:$FrontendPort/index.html"
Write-Host "卖家后台： http://127.0.0.1:$FrontendPort/login.html"
Write-Host "后端接口： http://localhost:$BackendPort/api"
Write-Host "默认账号： admin / admin123"
Write-Host "日志目录： $logDir"
Write-Host "停止服务： powershell -ExecutionPolicy Bypass -File scripts\stop-all.ps1"
Write-Host "============================================" -ForegroundColor Green
