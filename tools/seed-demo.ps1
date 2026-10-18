$ErrorActionPreference = 'Stop'
$seed = Get-Content -Raw (Join-Path $PSScriptRoot 'demo-seed.sql')
$seed | docker compose exec -T postgres psql -U graphchart -d graphdb
Write-Host 'Demo hierarchy seeded. Open http://localhost:5174 and start the simulator with:'
Write-Host 'docker compose --profile simulator up -d --build sensor-simulator'
