$acceptanceRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $acceptanceRoot "AcceptanceCommon.ps1")

function Test-Throws([scriptblock]$Action) {
    try {
        & $Action
        return $false
    } catch {
        return $true
    }
}

Describe "Acceptance manifest schema" {
    BeforeAll {
        $catalogPath = Join-Path $acceptanceRoot "scenarios.json"
        $catalog = Get-AcceptanceScenarioCatalog -Path $catalogPath
    }

    BeforeEach {
        $evidenceRoot = Join-Path $TestDrive "evidence"
        New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
        $scenarioResults = @()
        foreach ($definition in @($catalog.scenarios)) {
            $evidence = @()
            foreach ($kind in @($definition.requiredEvidence)) {
                $safeKind = ([string]$kind) -replace '[^a-zA-Z0-9_-]', '_'
                $path = Join-Path $evidenceRoot "$($definition.key)-$safeKind.txt"
                "evidence for $($definition.key) $kind" | Set-Content -LiteralPath $path -Encoding UTF8
                $evidence += [pscustomobject]@{
                    kind = [string]$kind
                    path = $path
                    sha256 = Get-FileSha256 -Path $path
                }
            }
            $scenarioResults += [pscustomobject]@{
                key = [string]$definition.key
                id = [string]$definition.androidId
                status = "PASS"
                evidence = @($evidence)
            }
        }
        $manifest = [pscustomobject]@{
            schemaVersion = 1
            runId = "android-test-run"
            capturedAtUtc = [DateTime]::UtcNow.ToString("o")
            targetClass = "Android"
            serial = "phone-serial"
            scenarioSetId = [string]$catalog.scenarioSetId
            device = @{ apiLevel = 30; abi = "arm64-v8a"; abiList = @("arm64-v8a") }
            application = @{
                packageName = "com.example.pepper_person_id_poc"
                activity = ".MainActivity"
                variant = "benchmarkDebug"
                apkSha256 = ("a" * 64)
            }
            scenarios = @($scenarioResults)
            overallStatus = "PASS"
        }
    }

    It "accepts the exact complete Android pass schema and evidence hashes" {
        $result = Test-AcceptanceManifest -Manifest $manifest -Catalog $catalog `
            -ExpectedTargetClass Android -RequirePass -VerifyEvidenceFiles
        $result.IsValid | Should Be $true
        @($result.Errors).Count | Should Be 0
    }

    It "rejects a missing scenario" {
        $manifest.scenarios = @($manifest.scenarios | Where-Object id -ne "A-SPK-004")
        $result = Test-AcceptanceManifest -Manifest $manifest -Catalog $catalog `
            -ExpectedTargetClass Android -RequirePass
        $result.IsValid | Should Be $false
        ($result.Errors -join ";") | Should Match "missing scenarios"
    }

    It "rejects a failed required scenario even when overallStatus says PASS" {
        ($manifest.scenarios | Where-Object id -eq "A-FACE-001").status = "FAIL"
        $result = Test-AcceptanceManifest -Manifest $manifest -Catalog $catalog `
            -ExpectedTargetClass Android -RequirePass
        $result.IsValid | Should Be $false
        ($result.Errors -join ";") | Should Match "A-FACE-001 status must be PASS"
    }

    It "rejects evidence hash drift" {
        $item = ($manifest.scenarios | Where-Object id -eq "A-DIAG-001").evidence[0]
        "changed" | Set-Content -LiteralPath $item.path -Encoding UTF8
        $result = Test-AcceptanceManifest -Manifest $manifest -Catalog $catalog `
            -ExpectedTargetClass Android -RequirePass -VerifyEvidenceFiles
        $result.IsValid | Should Be $false
        ($result.Errors -join ";") | Should Match "hash mismatch"
    }

    It "requires exact A/P prefix mapping in the catalog" {
        $badCatalogPath = Join-Path $TestDrive "bad-scenarios.json"
        @{
            schemaVersion = 1
            scenarioSetId = "bad"
            scenarios = @(@{
                key = "FACE-001"
                androidId = "A-WRONG"
                pepperId = "P-FACE-001"
                requiredEvidence = @()
            })
        } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $badCatalogPath -Encoding UTF8
        (Test-Throws { Get-AcceptanceScenarioCatalog -Path $badCatalogPath }) | Should Be $true
    }
}
