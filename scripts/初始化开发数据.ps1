param([string]$JavaExecutable = 'java')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'server/target/qivo-server.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw '请先在 server 目录执行 mvn package。' }
# 所有凭据通过环境变量注入，命令行和公共脚本不保存密码。
$requiredNames = @('QIVO_DEV_DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'QIVO_DEV_DATABASE_CONFIRM', 'QIVO_DEV_ADMIN_PASSWORD', 'QIVO_DEV_OPERATOR_PASSWORD', 'QIVO_DEV_USER_PASSWORD')
foreach ($name in $requiredNames) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "缺少环境变量：$name" }
}
& $JavaExecutable '-Dloader.main=com.qivo.development.DevelopmentDataInitializer' -cp $jarPath org.springframework.boot.loader.launch.PropertiesLauncher
if ($LASTEXITCODE -ne 0) { throw '初始化未成功，请检查目标开发库及环境变量；命令不会覆盖已有业务数据。' }
