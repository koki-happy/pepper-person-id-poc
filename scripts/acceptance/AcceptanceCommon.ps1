Set-StrictMode -Version Latest

function Get-AcceptanceScenarioCatalog {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Scenario catalog not found: $Path"
    }
    $catalog = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    if ($catalog.schemaVersion -ne 1) {
        throw "Unsupported scenario catalog schemaVersion: $($catalog.schemaVersion)"
    }
    if ([string]::IsNullOrWhiteSpace([string]$catalog.scenarioSetId)) {
        throw "Scenario catalog scenarioSetId is required"
    }
    $keys = @($catalog.scenarios | ForEach-Object { [string]$_.key })
    if ($keys.Count -eq 0 -or @($keys | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
        throw "Every scenario requires a key"
    }
    if (@($keys | Group-Object | Where-Object Count -ne 1).Count -gt 0) {
        throw "Scenario keys must be unique"
    }
    foreach ($scenario in @($catalog.scenarios)) {
        if ([string]::IsNullOrWhiteSpace([string]$scenario.androidId) -or
            [string]::IsNullOrWhiteSpace([string]$scenario.pepperId)) {
            throw "Scenario '$($scenario.key)' requires androidId and pepperId"
        }
        if ([string]$scenario.androidId -ne "A-$($scenario.key)" -or
            [string]$scenario.pepperId -ne "P-$($scenario.key)") {
            throw "Scenario '$($scenario.key)' IDs must use the exact A-/P- prefix mapping"
        }
    }
    return $catalog
}

function Get-ScenarioId {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Scenario,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$TargetClass
    )
    if ($TargetClass -eq "Android") {
        return [string]$Scenario.androidId
    }
    return [string]$Scenario.pepperId
}

function Get-FileSha256 {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Test-AcceptanceManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)]$Catalog,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$ExpectedTargetClass,
        [switch]$RequirePass,
        [switch]$VerifyEvidenceFiles
    )

    $errors = New-Object System.Collections.Generic.List[string]
    if ($Manifest.schemaVersion -ne 1) { $errors.Add("schemaVersion must be 1") }
    if ([string]$Manifest.scenarioSetId -ne [string]$Catalog.scenarioSetId) {
        $errors.Add("scenarioSetId does not match the catalog")
    }
    if ([string]$Manifest.targetClass -ne $ExpectedTargetClass) {
        $errors.Add("targetClass must be $ExpectedTargetClass")
    }
    foreach ($field in @("runId", "capturedAtUtc", "serial")) {
        if ([string]::IsNullOrWhiteSpace([string]$Manifest.$field)) {
            $errors.Add("$field is required")
        }
    }
    if ($null -eq $Manifest.device) {
        $errors.Add("device is required")
    } else {
        if ([int]$Manifest.device.apiLevel -le 0) { $errors.Add("device.apiLevel must be positive") }
        if ([string]::IsNullOrWhiteSpace([string]$Manifest.device.abi)) {
            $errors.Add("device.abi is required")
        }
    }
    if ($null -eq $Manifest.application) {
        $errors.Add("application is required")
    } else {
        foreach ($field in @("packageName", "activity", "variant", "apkSha256")) {
            if ([string]::IsNullOrWhiteSpace([string]$Manifest.application.$field)) {
                $errors.Add("application.$field is required")
            }
        }
    }

    $expected = @($Catalog.scenarios | ForEach-Object { Get-ScenarioId -Scenario $_ -TargetClass $ExpectedTargetClass })
    $actual = @($Manifest.scenarios | ForEach-Object { [string]$_.id })
    if (@($actual | Group-Object | Where-Object Count -ne 1).Count -gt 0) {
        $errors.Add("scenario IDs must be unique")
    }
    $missing = @($expected | Where-Object { $_ -notin $actual })
    $unexpected = @($actual | Where-Object { $_ -notin $expected })
    if ($missing.Count -gt 0) { $errors.Add("missing scenarios: $($missing -join ', ')") }
    if ($unexpected.Count -gt 0) { $errors.Add("unexpected scenarios: $($unexpected -join ', ')") }

    foreach ($definition in @($Catalog.scenarios)) {
        $expectedId = Get-ScenarioId -Scenario $definition -TargetClass $ExpectedTargetClass
        $result = @($Manifest.scenarios | Where-Object { [string]$_.id -eq $expectedId }) | Select-Object -First 1
        if ($null -eq $result) { continue }
        if ([string]$result.key -ne [string]$definition.key) {
            $errors.Add("$expectedId key does not match the catalog")
        }
        if ($RequirePass -and [string]$result.status -ne "PASS") {
            $errors.Add("$expectedId status must be PASS")
        }
        $requiredKinds = @($definition.requiredEvidence | ForEach-Object { [string]$_ })
        $evidence = @($result.evidence)
        foreach ($kind in $requiredKinds) {
            $matches = @($evidence | Where-Object { [string]$_.kind -eq $kind })
            if ($matches.Count -eq 0) {
                $errors.Add("$expectedId is missing evidence kind '$kind'")
                continue
            }
            foreach ($item in $matches) {
                if ([string]::IsNullOrWhiteSpace([string]$item.path) -or
                    [string]::IsNullOrWhiteSpace([string]$item.sha256)) {
                    $errors.Add("$expectedId evidence '$kind' requires path and sha256")
                    continue
                }
                if ($VerifyEvidenceFiles) {
                    if (-not (Test-Path -LiteralPath ([string]$item.path) -PathType Leaf)) {
                        $errors.Add("$expectedId evidence file not found: $($item.path)")
                    } elseif ((Get-FileSha256 -Path ([string]$item.path)) -ne ([string]$item.sha256).ToLowerInvariant()) {
                        $errors.Add("$expectedId evidence hash mismatch: $($item.path)")
                    }
                }
            }
        }
    }
    if ($RequirePass -and [string]$Manifest.overallStatus -ne "PASS") {
        $errors.Add("overallStatus must be PASS")
    }
    [pscustomobject]@{
        IsValid = ($errors.Count -eq 0)
        Errors = @($errors)
    }
}

function Invoke-AcceptanceProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [switch]$AllowFailure
    )

    $output = & $FilePath @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "Command failed ($exitCode): $FilePath $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output | ForEach-Object { [string]$_ })
    }
}
