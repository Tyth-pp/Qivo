param([string]$RedisExecutable = $env:QIVO_REDIS_EXECUTABLE)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.local/redis.conf'
if (-not (Test-Path -LiteralPath $configPath)) { throw '缺少本机 .local/redis.conf，请按开发环境说明配置。' }
if ([string]::IsNullOrWhiteSpace($RedisExecutable)) {
    $redisCommand = Get-Command redis-server -ErrorAction SilentlyContinue
    if ($redisCommand) { $RedisExecutable = $redisCommand.Source }
    elseif (Test-Path -LiteralPath 'D:/software/Redis-x64-3.2.100/redis-server.exe') { $RedisExecutable = 'D:/software/Redis-x64-3.2.100/redis-server.exe' }
    else { throw '请通过 QIVO_REDIS_EXECUTABLE 指定本机 Redis 可执行文件。' }
}
$listener = Get-NetTCPConnection -LocalPort 6380 -State Listen -ErrorAction SilentlyContinue
if ($listener) { Write-Output '6380 端口已被监听，请确认该实例是否为本项目 Redis；未重复启动。'; exit 0 }
$process = Start-Process -FilePath $RedisExecutable -ArgumentList ('"' + $configPath + '"') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $projectRoot '.local/redis.pid')
Write-Output "已启动本地开发 Redis，端口 6380，PID $($process.Id)。"
