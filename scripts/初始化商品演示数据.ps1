param(
    [ValidateSet('Seed', 'Cleanup')]
    [string]$Action = 'Seed',
    [Parameter(Mandatory = $true)]
    [string]$UploadPath,
    [string]$JavaExecutable = 'java'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'server/target/qivo-server.jar'
$assetPath = Join-Path $projectRoot 'assets/qivo-catalog-images'
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw '请先在 server 目录执行 mvn package。'
}
if (-not (Test-Path -LiteralPath $assetPath -PathType Container)) {
    throw '找不到商品演示图片目录。'
}
if (-not (Test-Path -LiteralPath $UploadPath -PathType Container)) {
    throw '上传目录不存在，请传入后端 qivo.upload.path 对应的目录。'
}
foreach ($name in @('QIVO_DEV_DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'QIVO_DEV_DATABASE_CONFIRM')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "缺少环境变量：$name"
    }
}

$env:QIVO_DEMO_ASSET_DIR = (Resolve-Path -LiteralPath $assetPath).Path
$env:QIVO_DEMO_UPLOAD_DIR = (Resolve-Path -LiteralPath $UploadPath).Path
$command = $Action.ToLowerInvariant()
& $JavaExecutable '-Dloader.main=com.qivo.development.CatalogDataInitializer' -cp $jarPath org.springframework.boot.loader.launch.PropertiesLauncher $command
if ($LASTEXITCODE -ne 0) {
    throw "商品演示数据 $Action 未完成；数据库事务已回滚，已有业务数据未自动清理。"
}
