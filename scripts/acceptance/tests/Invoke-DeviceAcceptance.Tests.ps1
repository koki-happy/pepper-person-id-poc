$acceptanceRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $acceptanceRoot "Get-DevicePreflight.ps1")
. (Join-Path $acceptanceRoot "Assert-AndroidPassBeforePepper.ps1")
. (Join-Path $acceptanceRoot "Invoke-DeviceAcceptance.ps1")

function Test-Throws([scriptblock]$Action) {
    try {
        & $Action
        return $false
    } catch {
        return $true
    }
}

Describe "Exact serial and device classification guards" {
    It "selects only an exact case-sensitive serial" {
        $records = @(
            [pscustomobject]@{ Serial = "192.168.1.10:5555"; State = "device" },
            [pscustomobject]@{ Serial = "192.168.1.100:5555"; State = "device" }
        )
        $selected = Get-ExactAdbDevice -Serial "192.168.1.10:5555" -Records $records
        $selected.Serial | Should Be "192.168.1.10:5555"
        (Test-Throws { Get-ExactAdbDevice -Serial "192.168.1.1" -Records $records }) | Should Be $true
    }

    It "rejects an offline exact serial" {
        $records = @([pscustomobject]@{ Serial = "phone"; State = "offline" })
        (Test-Throws { Get-ExactAdbDevice -Serial "phone" -Records $records }) | Should Be $true
    }

    It "accepts an exact ARM64 Android API and rejects API drift" {
        (Test-Throws { Assert-DeviceClassification -Serial "phone" -TargetClass Android -ApiLevel 30 `
            -AbiList @("arm64-v8a", "armeabi-v7a") -ExpectedAbi "arm64-v8a" -ExpectedApiLevel 30 }) | Should Be $false
        (Test-Throws { Assert-DeviceClassification -Serial "phone" -TargetClass Android -ApiLevel 29 `
            -AbiList @("arm64-v8a") -ExpectedAbi "arm64-v8a" -ExpectedApiLevel 30 }) | Should Be $true
    }

    It "accepts only API 23 armeabi-v7a as Pepper" {
        (Test-Throws { Assert-DeviceClassification -Serial "pepper" -TargetClass Pepper -ApiLevel 23 `
            -AbiList @("armeabi-v7a", "armeabi") -ExpectedAbi "armeabi-v7a" -ExpectedApiLevel 23 }) | Should Be $false
        (Test-Throws { Assert-DeviceClassification -Serial "phone" -TargetClass Pepper -ApiLevel 23 `
            -AbiList @("arm64-v8a", "armeabi-v7a") -ExpectedAbi "armeabi-v7a" -ExpectedApiLevel 23 }) | Should Be $true
    }
}

Describe "Android pass prerequisite" {
    BeforeAll {
        $catalogPath = Join-Path $acceptanceRoot "scenarios.json"
        $catalog = Get-AcceptanceScenarioCatalog -Path $catalogPath
    }

    It "blocks Pepper when the Android manifest is absent" {
        (Test-Throws { Assert-AndroidPassBeforePepper -AndroidPassManifestPath (Join-Path $TestDrive "missing.json") `
            -ScenarioCatalogPath $catalogPath -SkipEvidenceFileVerification }) | Should Be $true
    }

    It "authorizes only the complete matching Pepper scenario set after Android PASS" {
        $androidScenarios = @()
        foreach ($definition in @($catalog.scenarios)) {
            $evidence = @($definition.requiredEvidence | ForEach-Object {
                [pscustomobject]@{ kind = [string]$_; path = "fixture.txt"; sha256 = ("b" * 64) }
            })
            $androidScenarios += [pscustomobject]@{
                key = [string]$definition.key
                id = [string]$definition.androidId
                status = "PASS"
                evidence = $evidence
            }
        }
        $manifestPath = Join-Path $TestDrive "android-pass.json"
        [pscustomobject]@{
            schemaVersion = 1
            runId = "android-pass"
            capturedAtUtc = [DateTime]::UtcNow.ToString("o")
            targetClass = "Android"
            serial = "phone"
            scenarioSetId = [string]$catalog.scenarioSetId
            device = @{ apiLevel = 30; abi = "arm64-v8a"; abiList = @("arm64-v8a") }
            application = @{
                packageName = "pkg"; activity = ".MainActivity"; variant = "benchmarkDebug"; apkSha256 = ("a" * 64)
            }
            scenarios = $androidScenarios
            overallStatus = "PASS"
        } | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

        $pepperIds = @($catalog.scenarios | ForEach-Object { [string]$_.pepperId })
        $authorization = Assert-AndroidPassBeforePepper -AndroidPassManifestPath $manifestPath `
            -ScenarioCatalogPath $catalogPath -PepperScenarioIds $pepperIds -SkipEvidenceFileVerification
        $authorization.authorized | Should Be $true
        @($authorization.scenarioKeys).Count | Should Be @($catalog.scenarios).Count

        $wrongIds = @($pepperIds | Where-Object { $_ -ne "P-SPK-004" })
        (Test-Throws { Assert-AndroidPassBeforePepper -AndroidPassManifestPath $manifestPath `
            -ScenarioCatalogPath $catalogPath -PepperScenarioIds $wrongIds -SkipEvidenceFileVerification }) | Should Be $true
    }

    It "uses distinct guarded build defaults for Android and Pepper" {
        $android = Resolve-DeviceAcceptanceDefaults -TargetClass Android -ProjectRoot $TestDrive
        $pepper = Resolve-DeviceAcceptanceDefaults -TargetClass Pepper -ProjectRoot $TestDrive
        $android.expectedAbi | Should Be "arm64-v8a"
        $android.variant | Should Be "benchmarkDebug"
        $pepper.expectedAbi | Should Be "armeabi-v7a"
        $pepper.variant | Should Be "candidateDebug"
        ($pepper.gradleTasks -join ",") | Should Match "verifyCandidateModelLicenses"
    }
}
