[CmdletBinding()]
param(
    [string]$Serial,
    [ValidateSet("Android", "Pepper")]
    [string]$TargetClass = "Android",
    [int]$ExpectedApiLevel = 0,
    [string]$ExpectedAbi,
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$ScenarioCatalogPath = (Join-Path $PSScriptRoot "scenarios.json"),
    [string]$AndroidPassManifestPath,
    [string]$ApkPath,
    [string]$PackageName = "com.example.pepper_person_id_poc",
    [string]$ActivityName = ".MainActivity",
    [string]$Variant,
    [string[]]$GradleTasks = @(),
    [string]$ScenarioResultsPath,
    [string]$EvidenceRoot,
    [string]$AdbPath = "adb",
    [string]$JavaPath = "java",
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "AcceptanceCommon.ps1")
. (Join-Path $PSScriptRoot "Get-DevicePreflight.ps1")
. (Join-Path $PSScriptRoot "Get-DeviceEvidence.ps1")
. (Join-Path $PSScriptRoot "Assert-AndroidPassBeforePepper.ps1")

function Resolve-DeviceAcceptanceDefaults {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$TargetClass,
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [string]$ExpectedAbi,
        [string]$Variant,
        [string]$ApkPath,
        [string[]]$GradleTasks = @()
    )

    if ($TargetClass -eq "Android") {
        if ([string]::IsNullOrWhiteSpace($ExpectedAbi)) { $ExpectedAbi = "arm64-v8a" }
        if ([string]::IsNullOrWhiteSpace($Variant)) { $Variant = "benchmarkDebug" }
        if ([string]::IsNullOrWhiteSpace($ApkPath)) {
            $ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\benchmark\debug\app-benchmark-debug.apk"
        }
        if ($GradleTasks.Count -eq 0) {
            $GradleTasks = @(":app:verifyModelCatalog", ":app:assembleBenchmarkDebug")
        }
    } else {
        if ([string]::IsNullOrWhiteSpace($ExpectedAbi)) { $ExpectedAbi = "armeabi-v7a" }
        if ([string]::IsNullOrWhiteSpace($Variant)) { $Variant = "candidateDebug" }
        if ([string]::IsNullOrWhiteSpace($ApkPath)) {
            $ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\candidate\debug\app-candidate-debug.apk"
        }
        if ($GradleTasks.Count -eq 0) {
            $GradleTasks = @(
                ":app:verifyCandidateModelLicenses",
                ":app:assembleCandidateDebug",
                ":app:auditCandidateDebugApk"
            )
        }
    }
    [pscustomobject]@{
        expectedAbi = $ExpectedAbi
        variant = $Variant
        apkPath = [IO.Path]::GetFullPath($ApkPath)
        gradleTasks = @($GradleTasks)
    }
}

function New-EvidenceReference {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Kind,
        [Parameter(Mandatory = $true)][string]$Path
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Evidence file not found for '$Kind': $Path"
    }
    [pscustomobject]@{
        kind = $Kind
        path = [IO.Path]::GetFullPath($Path)
        sha256 = Get-FileSha256 -Path $Path
    }
}

function Get-ScenarioResultMap {
    [CmdletBinding()]
    param(
        [string]$ScenarioResultsPath,
        [Parameter(Mandatory = $true)]$Catalog,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$TargetClass
    )

    $map = @{}
    if ([string]::IsNullOrWhiteSpace($ScenarioResultsPath)) { return $map }
    if (-not (Test-Path -LiteralPath $ScenarioResultsPath -PathType Leaf)) {
        throw "Scenario results file not found: $ScenarioResultsPath"
    }
    $results = Get-Content -Raw -LiteralPath $ScenarioResultsPath | ConvertFrom-Json
    if ($results.schemaVersion -ne 1 -or [string]$results.scenarioSetId -ne [string]$Catalog.scenarioSetId) {
        throw "Scenario results schema or scenarioSetId mismatch"
    }
    if ([string]$results.targetClass -ne $TargetClass) {
        throw "Scenario results targetClass mismatch"
    }
    $expectedIds = @($Catalog.scenarios | ForEach-Object { Get-ScenarioId -Scenario $_ -TargetClass $TargetClass })
    foreach ($result in @($results.scenarios)) {
        $id = [string]$result.id
        if ($id -notin $expectedIds) { throw "Unexpected scenario result ID: $id" }
        if ($map.ContainsKey($id)) { throw "Duplicate scenario result ID: $id" }
        $map[$id] = $result
    }
    return $map
}

function Invoke-DeviceAcceptance {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$TargetClass,
        [Parameter(Mandatory = $true)][int]$ExpectedApiLevel,
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$ScenarioCatalogPath,
        [string]$ExpectedAbi,
        [string]$AndroidPassManifestPath,
        [string]$ApkPath,
        [Parameter(Mandatory = $true)][string]$PackageName,
        [Parameter(Mandatory = $true)][string]$ActivityName,
        [string]$Variant,
        [string[]]$GradleTasks = @(),
        [string]$ScenarioResultsPath,
        [Parameter(Mandatory = $true)][string]$EvidenceRoot,
        [string]$AdbPath = "adb",
        [string]$JavaPath = "java",
        [switch]$SkipBuild
    )

    if ($ExpectedApiLevel -le 0) {
        throw "-ExpectedApiLevel is required and must exactly match the selected serial"
    }
    $catalog = Get-AcceptanceScenarioCatalog -Path $ScenarioCatalogPath
    $defaults = Resolve-DeviceAcceptanceDefaults -TargetClass $TargetClass -ProjectRoot $ProjectRoot `
        -ExpectedAbi $ExpectedAbi -Variant $Variant -ApkPath $ApkPath -GradleTasks $GradleTasks

    # This is the only step before the Pepper prerequisite gate, and it is read-only.
    $preflight = Get-DevicePreflight -Serial $Serial -TargetClass $TargetClass `
        -ExpectedAbi $defaults.expectedAbi -ExpectedApiLevel $ExpectedApiLevel -AdbPath $AdbPath
    if ($TargetClass -eq "Pepper") {
        if ([string]::IsNullOrWhiteSpace($AndroidPassManifestPath)) {
            throw "Pepper execution blocked: -AndroidPassManifestPath is required"
        }
        $pepperIds = @($catalog.scenarios | ForEach-Object { [string]$_.pepperId })
        Assert-AndroidPassBeforePepper -AndroidPassManifestPath $AndroidPassManifestPath `
            -ScenarioCatalogPath $ScenarioCatalogPath -PepperScenarioIds $pepperIds | Out-Null
    }

    $runId = "$($TargetClass.ToLowerInvariant())-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))-$([guid]::NewGuid().ToString('N').Substring(0,8))"
    $runDirectory = Join-Path $EvidenceRoot $runId
    New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
    $preflightPath = Join-Path $runDirectory "device-preflight.json"
    $preflight | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $preflightPath -Encoding UTF8

    if (-not $SkipBuild) {
        $wrapperJar = Join-Path $ProjectRoot "gradle\wrapper\gradle-wrapper.jar"
        if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
            throw "Gradle wrapper jar not found: $wrapperJar"
        }
        $buildArguments = @("-jar", $wrapperJar, "--no-daemon", "-PtargetAbi=$($defaults.expectedAbi)") + @($defaults.gradleTasks)
        Invoke-AcceptanceProcess -FilePath $JavaPath -Arguments $buildArguments | Out-Null
    }
    if (-not (Test-Path -LiteralPath $defaults.apkPath -PathType Leaf)) {
        throw "Guarded install blocked: APK not found: $($defaults.apkPath)"
    }
    $apkHash = Get-FileSha256 -Path $defaults.apkPath
    Invoke-AcceptanceProcess -FilePath $AdbPath -Arguments @("-s", $Serial, "install", "-r", $defaults.apkPath) | Out-Null
    $packageResult = Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "pm", "path", $PackageName)
    if (-not (($packageResult.Output -join "`n") -match '^package:')) {
        throw "Install command returned success but package '$PackageName' is not visible"
    }

    Invoke-AcceptanceProcess -FilePath $AdbPath -Arguments @("-s", $Serial, "logcat", "-c") | Out-Null
    $component = "$PackageName/$ActivityName"
    $launch = Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "am", "start", "-W", "-n", $component)
    if (($launch.Output -join "`n") -match '(?i)error:|exception|does not exist') {
        throw "Launch failed: $($launch.Output -join '; ')"
    }

    $uiRemotePath = "/sdcard/person-id-acceptance-$runId.xml"
    $uiPath = Join-Path $runDirectory "ui-hierarchy.xml"
    Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "uiautomator", "dump", $uiRemotePath) | Out-Null
    Invoke-AcceptanceProcess -FilePath $AdbPath -Arguments @("-s", $Serial, "pull", $uiRemotePath, $uiPath) | Out-Null
    Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "rm", "-f", $uiRemotePath) -AllowFailure | Out-Null
    $deviceEvidence = Get-DeviceEvidence -Serial $Serial -PackageName $PackageName `
        -OutputDirectory (Join-Path $runDirectory "device") -AdbPath $AdbPath

    $resultMap = Get-ScenarioResultMap -ScenarioResultsPath $ScenarioResultsPath `
        -Catalog $catalog -TargetClass $TargetClass
    $scenarioResults = @()
    foreach ($definition in @($catalog.scenarios)) {
        $id = Get-ScenarioId -Scenario $definition -TargetClass $TargetClass
        $provided = $resultMap[$id]
        $evidence = New-Object System.Collections.Generic.List[object]
        if ($id -match '-DIAG-001$') {
            $evidence.Add((New-EvidenceReference -Kind "device-preflight" -Path $preflightPath))
            $evidence.Add((New-EvidenceReference -Kind "ui-hierarchy" -Path $uiPath))
            $evidence.Add((New-EvidenceReference -Kind "targeted-logcat" -Path ([string]$deviceEvidence.targetedLogcat.path)))
        }
        if ($id -match '-PRIV-001$') {
            $evidence.Add((New-EvidenceReference -Kind "privacy-scan" -Path ([string]$deviceEvidence.privacy.path)))
            $evidence.Add((New-EvidenceReference -Kind "crash-report" -Path ([string]$deviceEvidence.crash.path)))
            $evidence.Add((New-EvidenceReference -Kind "package-state" -Path ([string]$deviceEvidence.package.path)))
        }
        if ($null -ne $provided) {
            foreach ($item in @($provided.evidence)) {
                $evidence.Add((New-EvidenceReference -Kind ([string]$item.kind) -Path ([string]$item.path)))
            }
        }
        $status = if ($null -ne $provided) { [string]$provided.status } else { "PENDING_HUMAN" }
        $blocker = if ($null -ne $provided -and $null -ne $provided.PSObject.Properties["blocker"]) {
            [string]$provided.blocker
        } else {
            "Required scenario checkpoint has not been supplied"
        }
        if ($id -match '-PRIV-001$' -and -not [bool]$deviceEvidence.privacy.scanPassed) {
            $status = "BLOCKED"
            $blocker = [string]$deviceEvidence.privacy.blocker
            if ([string]::IsNullOrWhiteSpace($blocker)) { $blocker = "Privacy scan found prohibited files" }
        }
        if ($id -match '-PRIV-001$' -and -not [bool]$deviceEvidence.crash.passed) {
            $status = "FAIL"
            $blocker = "Crash evidence contains one or more package crashes"
        }
        if ($status -notin @("PASS", "FAIL", "BLOCKED", "PENDING_HUMAN")) {
            throw "Invalid status '$status' for $id"
        }
        $scenarioResults += [pscustomobject]@{
            key = [string]$definition.key
            id = $id
            counterpartScenarioId = $(if ($TargetClass -eq "Android") { [string]$definition.pepperId } else { [string]$definition.androidId })
            requiresHumanPresence = [bool]$definition.requiresHumanPresence
            status = $status
            blocker = $blocker
            evidence = @($evidence)
        }
    }

    $allScenarioStatusesPass = (@($scenarioResults | Where-Object { [string]$_.status -ne "PASS" }).Count -eq 0)
    $eligibleForPass = ($allScenarioStatusesPass -and -not $SkipBuild)
    $manifest = [pscustomobject]@{
        schemaVersion = 1
        runId = $runId
        capturedAtUtc = [DateTime]::UtcNow.ToString("o")
        targetClass = $TargetClass
        serial = $Serial
        scenarioSetId = [string]$catalog.scenarioSetId
        device = @{
            apiLevel = [int]$preflight.apiLevel
            abi = [string]$preflight.abi
            abiList = @($preflight.abiList)
            model = [string]$preflight.model
        }
        application = @{
            packageName = $PackageName
            activity = $ActivityName
            variant = [string]$defaults.variant
            apkPath = [string]$defaults.apkPath
            apkSha256 = $apkHash
        }
        stages = @{
            preflight = "PASS"
            build = $(if ($SkipBuild) { "SKIPPED_EXPLICITLY" } else { "PASS" })
            install = "PASS"
            launch = "PASS"
            functional = $(if ($eligibleForPass) { "PASS" } else { "INCOMPLETE" })
            stability = $(if ($eligibleForPass) { "PASS" } else { "INCOMPLETE" })
        }
        evidenceRoot = [IO.Path]::GetFullPath($runDirectory)
        scenarios = @($scenarioResults)
        overallStatus = $(if ($eligibleForPass) { "PASS" } else { "INCOMPLETE" })
    }
    $passValidation = Test-AcceptanceManifest -Manifest $manifest -Catalog $catalog `
        -ExpectedTargetClass $TargetClass -RequirePass -VerifyEvidenceFiles
    if (-not $passValidation.IsValid) {
        $manifest.overallStatus = "INCOMPLETE"
        $manifest.stages.functional = "INCOMPLETE"
        $manifest.stages.stability = "INCOMPLETE"
    }
    $manifestPath = Join-Path $runDirectory "acceptance-manifest.json"
    $manifest | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    return [pscustomobject]@{
        manifestPath = $manifestPath
        overallStatus = [string]$manifest.overallStatus
        runId = $runId
        targetClass = $TargetClass
        serial = $Serial
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    if ([string]::IsNullOrWhiteSpace($Serial)) { throw "-Serial is required" }
    if ($ExpectedApiLevel -le 0) { throw "-ExpectedApiLevel is required" }
    if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
        $EvidenceRoot = Join-Path $ProjectRoot "device-evidence"
    }
    Invoke-DeviceAcceptance -Serial $Serial -TargetClass $TargetClass -ExpectedApiLevel $ExpectedApiLevel `
        -ProjectRoot $ProjectRoot -ScenarioCatalogPath $ScenarioCatalogPath -ExpectedAbi $ExpectedAbi `
        -AndroidPassManifestPath $AndroidPassManifestPath -ApkPath $ApkPath -PackageName $PackageName `
        -ActivityName $ActivityName -Variant $Variant -GradleTasks $GradleTasks `
        -ScenarioResultsPath $ScenarioResultsPath -EvidenceRoot $EvidenceRoot -AdbPath $AdbPath `
        -JavaPath $JavaPath -SkipBuild:$SkipBuild
}
