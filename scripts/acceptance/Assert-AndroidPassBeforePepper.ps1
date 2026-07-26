[CmdletBinding()]
param(
    [Alias("AndroidPassManifestPath")]
    [string]$EntryAndroidPassManifestPath,
    [Alias("ScenarioCatalogPath")]
    [string]$EntryScenarioCatalogPath = (Join-Path $PSScriptRoot "scenarios.json"),
    [Alias("PepperScenarioIds")]
    [string[]]$EntryPepperScenarioIds = @(),
    [Alias("SkipEvidenceFileVerification")]
    [switch]$EntrySkipEvidenceFileVerification
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "AcceptanceCommon.ps1")

function Assert-AndroidPassBeforePepper {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$AndroidPassManifestPath,
        [Parameter(Mandatory = $true)][string]$ScenarioCatalogPath,
        [string[]]$PepperScenarioIds = @(),
        [switch]$SkipEvidenceFileVerification
    )

    if (-not (Test-Path -LiteralPath $AndroidPassManifestPath -PathType Leaf)) {
        throw "Pepper execution blocked: Android pass manifest not found: $AndroidPassManifestPath"
    }
    $catalog = Get-AcceptanceScenarioCatalog -Path $ScenarioCatalogPath
    $manifest = Get-Content -Raw -LiteralPath $AndroidPassManifestPath | ConvertFrom-Json
    $validation = Test-AcceptanceManifest -Manifest $manifest -Catalog $catalog `
        -ExpectedTargetClass Android -RequirePass -VerifyEvidenceFiles:(-not $SkipEvidenceFileVerification)
    if (-not $validation.IsValid) {
        throw "Pepper execution blocked: Android manifest validation failed: $($validation.Errors -join '; ')"
    }
    if ("arm64-v8a" -notin @($manifest.device.abiList) -and [string]$manifest.device.abi -ne "arm64-v8a") {
        throw "Pepper execution blocked: Android pass was not recorded on ARM64"
    }

    $expectedPepperIds = @($catalog.scenarios | ForEach-Object { [string]$_.pepperId })
    if ($PepperScenarioIds.Count -gt 0) {
        $actualPepperIds = @($PepperScenarioIds | ForEach-Object { [string]$_ })
        $missing = @($expectedPepperIds | Where-Object { $_ -notin $actualPepperIds })
        $unexpected = @($actualPepperIds | Where-Object { $_ -notin $expectedPepperIds })
        $duplicates = @($actualPepperIds | Group-Object | Where-Object Count -ne 1)
        if ($missing.Count -gt 0 -or $unexpected.Count -gt 0 -or $duplicates.Count -gt 0) {
            throw "Pepper execution blocked: Pepper scenario IDs differ from the Android scenario set"
        }
    }

    [pscustomobject]@{
        authorized = $true
        androidRunId = [string]$manifest.runId
        scenarioSetId = [string]$manifest.scenarioSetId
        androidScenarioIds = @($catalog.scenarios | ForEach-Object { [string]$_.androidId })
        pepperScenarioIds = @($expectedPepperIds)
        scenarioKeys = @($catalog.scenarios | ForEach-Object { [string]$_.key })
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    if ([string]::IsNullOrWhiteSpace($EntryAndroidPassManifestPath)) {
        throw "-AndroidPassManifestPath is required"
    }
    Assert-AndroidPassBeforePepper -AndroidPassManifestPath $EntryAndroidPassManifestPath `
        -ScenarioCatalogPath $EntryScenarioCatalogPath -PepperScenarioIds $EntryPepperScenarioIds `
        -SkipEvidenceFileVerification:$EntrySkipEvidenceFileVerification
}
