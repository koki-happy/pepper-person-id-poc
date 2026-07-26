param(
    [string[]]$ModelId,
    [string]$Config = "config/models.json",
    [string]$Destination = "models",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$configPath = Join-Path $repoRoot $Config
$destinationRoot = Join-Path $repoRoot $Destination

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "Model config not found: $configPath"
}

$catalog = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$selected = @($catalog.models)
if ($ModelId.Count -gt 0) {
    $selected = @($selected | Where-Object { $_.id -in $ModelId })
    $missing = @($ModelId | Where-Object { $_ -notin $selected.id })
    if ($missing.Count -gt 0) {
        throw "Unknown model id(s): $($missing -join ', ')"
    }
}

New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
foreach ($model in $selected) {
    $target = Join-Path $destinationRoot $model.filename
    $expectedHash = $model.sha256.ToUpperInvariant()
    $validExisting = (Test-Path -LiteralPath $target -PathType Leaf) -and
        ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $expectedHash) -and
        ((Get-Item -LiteralPath $target).Length -eq [long]$model.fileSizeBytes)

    if ($validExisting -and -not $Force) {
        Write-Host "Verified $($model.id): $target"
        continue
    }

    if ($model.id -eq "wespeaker-resnet34-lm") {
        & (Join-Path $repoRoot "scripts/prepare-wespeaker-resnet34-lm.ps1") -Force:$Force
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to prepare $($model.id)"
        }
        $prepared = Join-Path $repoRoot "app/src/benchmark/assets/models/$($model.filename)"
        Copy-Item -LiteralPath $prepared -Destination $target -Force
        if (((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expectedHash) -or
            ((Get-Item -LiteralPath $target).Length -ne [long]$model.fileSizeBytes)) {
            throw "Prepared $($model.id) does not match config/models.json"
        }
        Write-Host "Prepared and verified $($model.id): $target"
        continue
    }

    $temporary = "$target.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    Write-Host "Downloading $($model.id) from pinned revision $($model.revision)"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $model.downloadUrl -OutFile $temporary
        $actualFile = Get-Item -LiteralPath $temporary
        if ($actualFile.Length -ne [long]$model.fileSizeBytes) {
            throw "Size mismatch for $($model.id): expected=$($model.fileSizeBytes), actual=$($actualFile.Length)"
        }
        $actualHash = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash
        if ($actualHash -ne $expectedHash) {
            throw "SHA-256 mismatch for $($model.id): expected=$expectedHash, actual=$actualHash"
        }
        Move-Item -LiteralPath $temporary -Destination $target -Force
    } catch {
        Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
        throw
    }

    Write-Host "Downloaded and verified $($model.id): $target"
}

Write-Host "Speaker models are ready under $destinationRoot. The directory is excluded from Git."
