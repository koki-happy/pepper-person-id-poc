[CmdletBinding()]
param(
    [Alias("Serial")]
    [string]$EntrySerial,
    [Alias("PackageName")]
    [string]$EntryPackageName = "com.example.pepper_person_id_poc",
    [Alias("OutputDirectory")]
    [string]$EntryOutputDirectory,
    [Alias("AdbPath")]
    [string]$EntryAdbPath = "adb"
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "AcceptanceCommon.ps1")

function Get-CrashFindings {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$PackageName
    )

    $findings = New-Object System.Collections.Generic.List[object]
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        $line = [string]$Lines[$index]
        $windowStart = [Math]::Max(0, $index - 4)
        $windowEnd = [Math]::Min($Lines.Count - 1, $index + 8)
        $window = (($Lines[$windowStart..$windowEnd]) -join "`n")
        $kind = $null
        if ($line -match 'FATAL EXCEPTION' -and $window -match [regex]::Escape($PackageName)) {
            $kind = "java-fatal-exception"
        } elseif ($line -match "ANR in\s+$([regex]::Escape($PackageName))") {
            $kind = "anr"
        } elseif (($line -match 'Fatal signal\s+(6|7|11)' -or $line -match 'signal\s+(6|7|11)\s+\(') -and
            $window -match [regex]::Escape($PackageName)) {
            $kind = "native-fatal-signal"
        } elseif ($line -match 'am_crash' -and $line -match [regex]::Escape($PackageName)) {
            $kind = "activity-manager-crash"
        }
        if ($null -ne $kind) {
            $findings.Add([pscustomobject]@{
                kind = $kind
                lineNumber = $index + 1
                line = $line
            })
        }
    }
    return $findings.ToArray()
}

function Test-PrivacyFileList {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string[]]$Paths)

    $prohibitedExtensions = @(
        ".jpg", ".jpeg", ".png", ".webp", ".bmp",
        ".wav", ".pcm", ".mp3", ".m4a", ".aac", ".flac", ".ogg",
        ".npy", ".npz"
    )
    $violations = New-Object System.Collections.Generic.List[object]
    foreach ($path in $Paths) {
        $normalized = ([string]$path).Trim().Replace("\", "/")
        if ([string]::IsNullOrWhiteSpace($normalized)) { continue }
        $extension = [IO.Path]::GetExtension($normalized).ToLowerInvariant()
        $isMedia = $extension -in $prohibitedExtensions
        $isBiometricStore = $normalized -match '(?i)(^|/)(biometric|embeddings?|anonymous[-_]?clusters?)(/|$)' -or
            $normalized -match '(?i)(face|speaker)[-_]?(embedding|cluster)'
        if ($isMedia -or $isBiometricStore) {
            $violations.Add([pscustomobject]@{
                path = $normalized
                reason = $(if ($isMedia) { "prohibited-media-or-array-extension" } else { "prohibited-biometric-store-path" })
            })
        }
    }
    [pscustomobject]@{
        IsClean = ($violations.Count -eq 0)
        Violations = $violations.ToArray()
    }
}

function Save-CommandEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$AllowFailure
    )
    $result = Invoke-AcceptanceProcess -FilePath $AdbPath -Arguments $Arguments -AllowFailure:$AllowFailure
    $result.Output | Set-Content -LiteralPath $Path -Encoding UTF8
    return $result
}

function Get-DeviceEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$PackageName,
        [Parameter(Mandatory = $true)][string]$OutputDirectory,
        [string]$AdbPath = "adb"
    )

    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $packagePath = Join-Path $OutputDirectory "package.txt"
    $activityPath = Join-Path $OutputDirectory "activity.txt"
    $permissionPath = Join-Path $OutputDirectory "permissions.txt"
    $crashLogPath = Join-Path $OutputDirectory "crash-logcat.txt"
    $targetedLogPath = Join-Path $OutputDirectory "targeted-logcat.txt"
    $privacyPath = Join-Path $OutputDirectory "privacy-files.txt"

    Save-CommandEvidence -AdbPath $AdbPath -Arguments @("-s", $Serial, "shell", "dumpsys", "package", $PackageName) -Path $packagePath | Out-Null
    Save-CommandEvidence -AdbPath $AdbPath -Arguments @("-s", $Serial, "shell", "dumpsys", "activity", "activities") -Path $activityPath | Out-Null
    Save-CommandEvidence -AdbPath $AdbPath -Arguments @("-s", $Serial, "shell", "dumpsys", "package", $PackageName) -Path $permissionPath | Out-Null
    $crashResult = Save-CommandEvidence -AdbPath $AdbPath -Arguments @("-s", $Serial, "logcat", "-b", "crash", "-d", "-v", "threadtime") -Path $crashLogPath -AllowFailure
    Save-CommandEvidence -AdbPath $AdbPath -Arguments @("-s", $Serial, "logcat", "-d", "-v", "threadtime", "$PackageName`:V", "AndroidRuntime:E", "libc:F", "*:S") -Path $targetedLogPath -AllowFailure | Out-Null

    $internalPrivacy = Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "run-as", $PackageName, "find", ".", "-type", "f", "-print") -AllowFailure
    $externalPrivacy = Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "find", "/sdcard/Android/data/$PackageName", "-type", "f", "-print") -AllowFailure
    $privacyLines = @($internalPrivacy.Output + $externalPrivacy.Output |
        Where-Object { $_ -notmatch '(?i)(permission denied|no such file|not debuggable)' })
    $privacyLines | Set-Content -LiteralPath $privacyPath -Encoding UTF8
    $privacyScan = Test-PrivacyFileList -Paths $privacyLines
    $crashes = @(Get-CrashFindings -Lines $crashResult.Output -PackageName $PackageName)
    $privacyInspectable = ($internalPrivacy.ExitCode -eq 0)

    $summary = [pscustomobject]@{
        schemaVersion = 1
        capturedAtUtc = [DateTime]::UtcNow.ToString("o")
        serial = $Serial
        packageName = $PackageName
        package = @{ path = $packagePath; sha256 = Get-FileSha256 -Path $packagePath }
        activity = @{ path = $activityPath; sha256 = Get-FileSha256 -Path $activityPath }
        permissions = @{ path = $permissionPath; sha256 = Get-FileSha256 -Path $permissionPath }
        crash = @{
            path = $crashLogPath
            sha256 = Get-FileSha256 -Path $crashLogPath
            findings = @($crashes)
            passed = ($crashes.Count -eq 0)
        }
        targetedLogcat = @{ path = $targetedLogPath; sha256 = Get-FileSha256 -Path $targetedLogPath }
        privacy = @{
            path = $privacyPath
            sha256 = Get-FileSha256 -Path $privacyPath
            internalStorageInspectable = $privacyInspectable
            scanPassed = ($privacyInspectable -and $privacyScan.IsClean)
            violations = @($privacyScan.Violations)
            blocker = $(if ($privacyInspectable) { $null } else { "run-as failed; internal app storage was not inspectable" })
        }
    }
    $summaryPath = Join-Path $OutputDirectory "device-evidence.json"
    $summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
    return $summary
}

if ($MyInvocation.InvocationName -ne ".") {
    if ([string]::IsNullOrWhiteSpace($EntrySerial)) { throw "-Serial is required" }
    if ([string]::IsNullOrWhiteSpace($EntryOutputDirectory)) { throw "-OutputDirectory is required" }
    Get-DeviceEvidence -Serial $EntrySerial -PackageName $EntryPackageName `
        -OutputDirectory $EntryOutputDirectory -AdbPath $EntryAdbPath
}
