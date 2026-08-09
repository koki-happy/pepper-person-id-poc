param(
    [string[]]$ModelId = @("campplus-zh-en")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$manifestPath = Join-Path $repoRoot "config\models.json"
$sourceDirectory = Join-Path $repoRoot "models"
$destinationDirectory = Join-Path $repoRoot "app\src\main\assets\models"
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null

foreach ($id in $ModelId) {
    $entry = @($manifest.models | Where-Object { $_.id -eq $id })
    if ($entry.Count -ne 1) {
        throw "Expected exactly one config/models.json entry for modelId '$id', found $($entry.Count)."
    }

    $model = $entry[0]
    $sourcePath = Join-Path $sourceDirectory $model.filename
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Model source is missing: $sourcePath. Run download-speaker-models.ps1 first."
    }

    $sourceFile = Get-Item -LiteralPath $sourcePath
    if ($sourceFile.Length -ne [long]$model.fileSizeBytes) {
        throw "Size mismatch for '$id': expected $($model.fileSizeBytes), actual $($sourceFile.Length)."
    }

    $actualHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne ([string]$model.sha256).ToLowerInvariant()) {
        throw "SHA-256 mismatch for '$id': expected $($model.sha256), actual $actualHash."
    }

    $destinationPath = Join-Path $destinationDirectory $model.filename
    Copy-Item -LiteralPath $sourcePath -Destination $destinationPath -Force
    $copiedHash = (Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($copiedHash -ne $actualHash) {
        throw "Post-copy SHA-256 mismatch for '$id': source $actualHash, destination $copiedHash."
    }

    Write-Host "Synced $id -> $destinationPath ($($sourceFile.Length) bytes, SHA-256 $actualHash)"
}

Write-Host "Android speaker model sync completed. Assets remain excluded from Git."
