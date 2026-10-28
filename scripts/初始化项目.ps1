param(
    [string]$JavaExecutable = 'java',
    [string]$MavenExecutable = 'mvn',
    [string]$ConfigPath = '',
    [switch]$UseBuiltJar
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $projectRoot 'server/src/main/resources/application-dev.yml'
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw '请先填写 server/src/main/resources/application-dev.yml。'
}
$ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path
$jarPath = Join-Path $projectRoot 'server/target/qivo-server.jar'
$assetPath = Join-Path $projectRoot 'assets/qivo-catalog-images'
function Resolve-InitializationMaven([string]$Requested) {
    $command = Get-Command $Requested -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    if ($Requested -ne 'mvn') { throw '指定的 Maven 不存在。' }
    foreach ($homePath in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($homePath)) {
            $candidate = Join-Path $homePath 'bin/mvn.cmd'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    foreach ($idea in @(Get-Process idea64 -ErrorAction SilentlyContinue)) {
        if ($idea.Path) {
            $ideaRoot = Split-Path -Parent (Split-Path -Parent $idea.Path)
            $candidate = Join-Path $ideaRoot 'plugins/maven/lib/maven3/bin/mvn.cmd'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    throw '未找到 Maven。请将 Maven 加入 PATH，启动已安装的 IDEA，或用 -MavenExecutable 指定 mvn.cmd。'
}
if (-not $UseBuiltJar) {
    $MavenExecutable = Resolve-InitializationMaven $MavenExecutable
    & $MavenExecutable '-f' (Join-Path $projectRoot 'server/pom.xml') '-DskipTests' 'package'
    if ($LASTEXITCODE -ne 0) { throw '后端构建失败，尚未初始化数据库。' }
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { throw '未找到后端 JAR，请先构建。' }
$launch = @('-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
    '-Dloader.main=com.qivo.development.ProjectInitializer', '-cp', $jarPath,
    'org.springframework.boot.loader.launch.PropertiesLauncher')
& $JavaExecutable @launch 'inspect' $ConfigPath $assetPath
if ($LASTEXITCODE -eq 10) { return }
if ($LASTEXITCODE -ne 0) { throw '首次初始化检查未通过，尚未写入业务数据。' }
# 凭据只在当前子进程环境中短暂传递，不写入配置、命令行或日志。
$previousAdmin = [Environment]::GetEnvironmentVariable('QIVO_INIT_ADMIN_PASSWORD', 'Process')
$previousUser = [Environment]::GetEnvironmentVariable('QIVO_INIT_USER_PASSWORD', 'Process')
function Read-InitialPassword([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle)
        if ($plain.Length -lt 8 -or [Text.Encoding]::UTF8.GetByteCount($plain) -gt 72) {
            throw '初始密码须至少8位，且UTF-8编码不超过72字节。请重新执行脚本。'
        }
        return $plain
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle)
        $secure.Dispose()
    }
}
try {
    $env:QIVO_INIT_ADMIN_PASSWORD = Read-InitialPassword '设置管理员初始密码（admin、product_admin）'
    $env:QIVO_INIT_USER_PASSWORD = Read-InitialPassword '设置消费者初始密码（buyer、buyer_other）'
    $answer = Read-Host '是否添加两笔交易样例？[y/N]'
    $arguments = @('initialize', $ConfigPath, $assetPath)
    if ($answer.Trim().ToLowerInvariant() -in @('y', 'yes', '是')) { $arguments += '--transactions' }
    & $JavaExecutable @launch @arguments
    if ($LASTEXITCODE -notin @(0, 10)) { throw '首次初始化未完成，请检查上方提示。' }
} finally {
    [Environment]::SetEnvironmentVariable('QIVO_INIT_ADMIN_PASSWORD', $previousAdmin, 'Process')
    [Environment]::SetEnvironmentVariable('QIVO_INIT_USER_PASSWORD', $previousUser, 'Process')
    $plain = $null
}
