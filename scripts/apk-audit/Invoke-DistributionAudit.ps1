param(
    [Parameter(Mandatory)][string]$ApkPath,
    [Parameter(Mandatory)][ValidateSet('benchmarkDebug', 'candidateDebug')][string]$Variant,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$CatalogPath,
    [Parameter(Mandatory)][string]$ReadElfPath,
    [Parameter(Mandatory)][string[]]$AllowedAbis,
    [string]$AllowlistPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot 'Invoke-ApkAudit.ps1')
. (Join-Path $PSScriptRoot 'Test-NativeCompatibility.ps1')
. (Join-Path $PSScriptRoot 'New-DistributionManifest.ps1')

[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$inventoryPath = Join-Path $OutputDirectory 'inventory.json'
$nativePath = Join-Path $OutputDirectory 'native-compatibility.json'
$manifestPath = Join-Path $OutputDirectory 'distribution-manifest.json'

$inventory = Invoke-ApkAudit -ApkPath $ApkPath -OutputPath $inventoryPath
$nativeResult = Invoke-NativeCompatibilityAudit `
    -ApkPath $ApkPath `
    -AllowedAbis $AllowedAbis `
    -ReadElfPath $ReadElfPath
$nativeResult | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $nativePath -Encoding utf8
if (-not $nativeResult.passed) {
    throw "Native compatibility audit failed: $($nativeResult.errors -join '; ')"
}

if ($Variant -eq 'candidateDebug') {
    if ([string]::IsNullOrWhiteSpace($AllowlistPath) -or
        -not (Test-Path -LiteralPath $AllowlistPath -PathType Leaf)) {
        throw 'Candidate APK audit requires a generated model/runtime allowlist.'
    }
    $allowlist = Get-Content -LiteralPath $AllowlistPath -Raw | ConvertFrom-Json
    $expectedAssets = @($allowlist.assetFilenames | Sort-Object -Unique)
    $actualAssets = @($inventory.modelAssets | ForEach-Object {
        [IO.Path]::GetFileName($_.path)
    } | Sort-Object -Unique)
    $unexpectedAssets = @($actualAssets | Where-Object { $_ -notin $expectedAssets })
    $missingAssets = @($expectedAssets | Where-Object { $_ -notin $actualAssets })
    if ($unexpectedAssets.Count -gt 0 -or $missingAssets.Count -gt 0) {
        throw "Candidate model assets differ from allowlist. Unexpected=$($unexpectedAssets -join ','); Missing=$($missingAssets -join ',')"
    }

    $selectedRuntimes = @($allowlist.runtimeIds)
    $nativeNames = @($inventory.nativeLibraries.name | Sort-Object -Unique)
    $runtimeNativeMarkers = @{
        'ncnn-20260526-android-cpu' = '^libncnn\.so$'
        'mnn-3.5.0-android-cpu' = '^libMNN\.so$'
        'onnxruntime-android-1.20.0-cpu' = '^libonnxruntime\.so$'
        'onnxruntime-mobile-1.27.0-android-cpu' = '^libonnxruntime\.so$'
        'sherpa-onnx-1.13.4-android-cpu' = 'sherpa'
        'opencv-5.0.0-android-cpu' = 'opencv'
        'litert-2.1.6-android-cpu' = '(?i)litert'
    }
    foreach ($entry in $runtimeNativeMarkers.GetEnumerator()) {
        if ($entry.Key -in $selectedRuntimes) { continue }
        $unexpectedLibraries = @($nativeNames | Where-Object { $_ -match $entry.Value })
        if ($unexpectedLibraries.Count -gt 0) {
            throw "Candidate contains native libraries for nonselected runtime $($entry.Key): $($unexpectedLibraries -join ',')"
        }
    }
}

New-DistributionManifest `
    -InventoryPath $inventoryPath `
    -CatalogPath $CatalogPath `
    -Variant $Variant `
    -OutputPath $manifestPath | Out-Null

[pscustomobject]@{
    schemaVersion = 1
    variant = $Variant
    apkSizeBytes = $inventory.apkSizeBytes
    modelAssetCount = @($inventory.modelAssets).Count
    nativeLibraryCount = @($inventory.nativeLibraries).Count
    inventoryPath = $inventoryPath
    nativeCompatibilityPath = $nativePath
    distributionManifestPath = $manifestPath
} | ConvertTo-Json -Depth 4
