# 本机经 ECS 47.99.95.38 转发到这台机器上的 MySQL
# 用法：另开一个终端保持运行，再启动 BackEnd（profile=local）
#   .\deploy\tunnel-test-rds.ps1
param(
  [string]$Identity = '',
  [string]$EcsHost = '47.99.95.38',
  [string]$EcsUser = 'root',
  [int]$LocalPort = 13307,
  [string]$RdsInternalHost = '127.0.0.1',
  [int]$RdsPort = 3306
)

$ErrorActionPreference = 'Stop'

Write-Host "==> 本机 127.0.0.1:${LocalPort} -> ${EcsHost} -> ${RdsInternalHost}:${RdsPort}"
Write-Host "    保持此窗口运行；BackEnd application-local.yml 已指向该端口"
Write-Host "    按 Ctrl+C 结束隧道"

$sshArgs = @(
  '-o', 'StrictHostKeyChecking=accept-new',
  '-o', 'ServerAliveInterval=30',
  '-N',
  '-L', "${LocalPort}:${RdsInternalHost}:${RdsPort}",
  "${EcsUser}@${EcsHost}"
)
if ($Identity) {
  if (-not (Test-Path $Identity)) {
    throw "找不到 SSH 私钥: $Identity"
  }
  $sshArgs = @('-i', $Identity, '-o', 'IdentitiesOnly=yes') + $sshArgs
}
& ssh @sshArgs
