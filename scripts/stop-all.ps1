<#
.SYNOPSIS
    在线购物系统 · 停止后端与前端服务
#>
param(
    [int]$FrontendPort = 5500
)

$ErrorActionPreference = "Continue"

Write-Host "==> 停止后端（java -jar online-shopping-backend）" -ForegroundColor Cyan
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like "*online-shopping-backend*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host ("  已停止 java pid=" + $_.ProcessId)
    }

Write-Host "==> 停止前端静态服务器（python http.server $FrontendPort）" -ForegroundColor Cyan
Get-CimInstance Win32_Process -Filter "Name='python.exe'" |
    Where-Object { $_.CommandLine -like "*http.server*$FrontendPort*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host ("  已停止 python pid=" + $_.ProcessId)
    }

Write-Host "完成。" -ForegroundColor Green
