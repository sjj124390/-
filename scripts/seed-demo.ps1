<#
.SYNOPSIS
    在线购物系统 · 演示数据初始化（在售商品）

.DESCRIPTION
    通过后端接口（而非直接改库）创建一件在售商品，保证状态机与存储过程一致。
    重复执行时若已有在售商品则跳过。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1
    powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1 -Force
#>
param(
    [string]$ApiBase = "http://localhost:8080/api",
    [string]$FrontendBase = "http://127.0.0.1:5500",
    [string]$Name = "九成新城市通勤山地车",
    [decimal]$Price = 880,
    [string]$Description = "捷安特 ATX 系列，骑行约 3000 公里，刚做过全车保养：更换刹车皮、链条上油。车架无磕碰，变速顺畅，随车赠送车锁与打气筒。支持当面验车。",
    [string]$ImageUrl = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not $ImageUrl) { $ImageUrl = "$FrontendBase/img/product-bike.svg" }

function Post-Json([string]$url, $body) {
    $json = $body | ConvertTo-Json -Depth 5 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json; charset=utf-8" -Body $bytes
}

Write-Host "==> 检查当前在售商品" -ForegroundColor Cyan
$current = Invoke-RestMethod -Uri "$ApiBase/products/current" -Method Get
if ($current.code -eq 200 -and -not $Force) {
    Write-Host ("已有在售商品：{0}（id={1}），跳过初始化。加 -Force 可再建一件。" -f $current.data.name, $current.data.id) -ForegroundColor Yellow
    exit 0
}

Write-Host "==> 创建草稿商品" -ForegroundColor Cyan
$draft = Post-Json "$ApiBase/admin/products" @{
    name        = $Name
    description = $Description
    imageUrl    = $ImageUrl
    price       = [double]$Price
}
if ($draft.code -ne 200) { throw ("创建商品失败：" + $draft.message) }
$productId = $draft.data.id
Write-Host ("  草稿已创建 id={0}" -f $productId)

Write-Host "==> 上架商品" -ForegroundColor Cyan
$publish = Invoke-RestMethod -Uri "$ApiBase/admin/products/$productId/publish" -Method Post
if ($publish.code -ne 200) { throw ("上架失败：" + $publish.message) }

Write-Host "==> 完成" -ForegroundColor Green
Invoke-RestMethod -Uri "$ApiBase/products/current" -Method Get | ConvertTo-Json -Depth 5
