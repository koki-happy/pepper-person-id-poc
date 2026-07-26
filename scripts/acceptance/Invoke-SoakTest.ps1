[CmdletBinding()]
param(
    [string]$Serial,
    [string]$PackageName = "com.example.pepper_person_id_poc",
    [ValidateSet(15, 30, 60)]
    [int]$DurationMinutes = 15,
    [ValidateRange(5, 300)]
    [int]$SampleIntervalSeconds = 30,
    [string]$OutputDirectory,
    [string]$AdbPath = "adb"
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "AcceptanceCommon.ps1")
. (Join-Path $PSScriptRoot "Get-DeviceEvidence.ps1")

function Get-SoakSchedule {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(15, 30, 60)]
        [int]$DurationMinutes,
        [Parameter(Mandatory = $true)]
        [ValidateRange(5, 300)]
        [int]$SampleIntervalSeconds
    )

    $durationSeconds = $DurationMinutes * 60
    [pscustomobject]@{
        durationMinutes = $DurationMinutes
        durationSeconds = $durationSeconds
        sampleIntervalSeconds = $SampleIntervalSeconds
        maximumSamples = [int][Math]::Floor($durationSeconds / $SampleIntervalSeconds) + 1
    }
}

function Invoke-SoakTest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$PackageName,
        [Parameter(Mandatory = $true)]
        [ValidateSet(15, 30, 60)]
        [int]$DurationMinutes,
        [Parameter(Mandatory = $true)]
        [ValidateRange(5, 300)]
        [int]$SampleIntervalSeconds,
        [Parameter(Mandatory = $true)][string]$OutputDirectory,
        [string]$AdbPath = "adb"
    )

    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $schedule = Get-SoakSchedule -DurationMinutes $DurationMinutes -SampleIntervalSeconds $SampleIntervalSeconds
    $samplesPath = Join-Path $OutputDirectory "soak-samples.jsonl"
    $startedUtc = [DateTime]::UtcNow
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $sampleCount = 0
    while ($stopwatch.Elapsed.TotalSeconds -lt $schedule.durationSeconds -and
        $sampleCount -lt $schedule.maximumSamples) {
        $pidResult = Invoke-AcceptanceProcess -FilePath $AdbPath `
            -Arguments @("-s", $Serial, "shell", "pidof", $PackageName) -AllowFailure
        $memResult = Invoke-AcceptanceProcess -FilePath $AdbPath `
            -Arguments @("-s", $Serial, "shell", "dumpsys", "meminfo", $PackageName) -AllowFailure
        $topResult = Invoke-AcceptanceProcess -FilePath $AdbPath `
            -Arguments @("-s", $Serial, "shell", "top", "-b", "-n", "1") -AllowFailure
        $sample = [pscustomobject]@{
            capturedAtUtc = [DateTime]::UtcNow.ToString("o")
            elapsedSeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 3)
            processId = (($pidResult.Output -join " ").Trim())
            processPresent = ($pidResult.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace(($pidResult.Output -join "")))
            meminfo = @($memResult.Output)
            topPackageLines = @($topResult.Output | Where-Object { $_ -match [regex]::Escape($PackageName) })
        }
        $sample | ConvertTo-Json -Depth 6 -Compress | Add-Content -LiteralPath $samplesPath -Encoding UTF8
        $sampleCount++
        $remaining = $schedule.durationSeconds - $stopwatch.Elapsed.TotalSeconds
        if ($remaining -gt 0 -and $sampleCount -lt $schedule.maximumSamples) {
            Start-Sleep -Milliseconds ([int]([Math]::Min($SampleIntervalSeconds, $remaining) * 1000))
        }
    }
    $stopwatch.Stop()

    $finalEvidence = Get-DeviceEvidence -Serial $Serial -PackageName $PackageName `
        -OutputDirectory (Join-Path $OutputDirectory "final-evidence") -AdbPath $AdbPath
    $summary = [pscustomobject]@{
        schemaVersion = 1
        serial = $Serial
        packageName = $PackageName
        durationMinutes = $DurationMinutes
        sampleIntervalSeconds = $SampleIntervalSeconds
        maximumSamples = $schedule.maximumSamples
        sampleCount = $sampleCount
        startedAtUtc = $startedUtc.ToString("o")
        completedAtUtc = [DateTime]::UtcNow.ToString("o")
        actualElapsedSeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 3)
        bounded = ($sampleCount -le $schedule.maximumSamples)
        processPresentAtEnd = [bool]$finalEvidence.activity
        crashPassed = [bool]$finalEvidence.crash.passed
        privacyPassed = [bool]$finalEvidence.privacy.scanPassed
        samples = @{ path = $samplesPath; sha256 = Get-FileSha256 -Path $samplesPath }
    }
    $summaryPath = Join-Path $OutputDirectory "soak-summary.json"
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
    return $summary
}

if ($MyInvocation.InvocationName -ne ".") {
    if ([string]::IsNullOrWhiteSpace($Serial)) { throw "-Serial is required" }
    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { throw "-OutputDirectory is required" }
    Invoke-SoakTest -Serial $Serial -PackageName $PackageName -DurationMinutes $DurationMinutes `
        -SampleIntervalSeconds $SampleIntervalSeconds -OutputDirectory $OutputDirectory -AdbPath $AdbPath
}
