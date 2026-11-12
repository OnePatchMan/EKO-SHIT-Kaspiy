param([string]$Python = 'python', [int]$Port = 8001)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& $Python -m uvicorn ecoshield.api:app --host 127.0.0.1 --port $Port
exit $LASTEXITCODE
