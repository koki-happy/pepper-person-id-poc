Set-StrictMode -Version Latest

function New-DistributionManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$InventoryPath,
        [Parameter(Mandatory)][string]$CatalogPath,
        [Parameter(Mandatory)][string]$Variant,
        [Parameter(Mandatory)][string]$OutputPath
    )
    $inventory = Get-Content -LiteralPath $InventoryPath -Raw | ConvertFrom-Json
    $catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json
    $includedNames = @($inventory.modelAssets | ForEach-Object {
        [System.IO.Path]::GetFileName($_.path)
    })
    $includedArtifacts = @($catalog.artifacts | Where-Object {
        $_.filename -and $_.filename -in $includedNames
    } | ForEach-Object {
        [pscustomobject]@{
            artifactId = $_.artifactId
            filename = $_.filename
            sha256 = $_.sha256
            weightLicense = $_.weightLicense
            commercialUse = $_.commercialUse
            licenseEvidence = $_.licenseEvidence
        }
    })
    $manifest = [pscustomobject]@{
        schemaVersion = 1
        createdAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        variant = $Variant
        apk = [pscustomobject]@{
            path = $inventory.apkPath
            sha256 = $inventory.apkSha256
            sizeBytes = $inventory.apkSizeBytes
            abis = @($inventory.abiList)
        }
        includedArtifacts = $includedArtifacts
        nativeLibraries = @($inventory.nativeLibraries)
    }
    $parent = Split-Path -Parent $OutputPath
    if ($parent) { [System.IO.Directory]::CreateDirectory($parent) | Out-Null }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8
    $manifest
}

if ($MyInvocation.InvocationName -ne '.') {
    New-DistributionManifest @args
}
