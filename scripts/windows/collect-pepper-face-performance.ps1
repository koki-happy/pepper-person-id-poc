param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [ValidateRange(0.01, 1440)]
    [double]$DurationMinutes = 30,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Scenario,
    [string]$OutputDirectory = "evaluation-results/face/pepper",
    [string]$PackageName = "com.example.pepper_person_id_poc",
    [ValidateRange(1, 60)]
    [int]$SampleIntervalSeconds = 5
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Get-EpochMillis {
    return [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
}

function Convert-ToSafeName([string]$Value) {
    return ($Value -replace '[^A-Za-z0-9._-]', '_').Trim('_')
}

function Invoke-Adb {
    param(
        [string[]]$AdbArguments,
        [switch]$AllowFailure
    )
    $output = @(& adb @AdbArguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "adb $($AdbArguments -join ' ') failed with exit code ${exitCode}: $($output -join [Environment]::NewLine)"
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
}

function Start-AdbCapture {
    param([string[]]$AdbArguments)
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = "adb"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    # All arguments used by this collector are adb tokens without whitespace. Using Arguments rather
    # than ProcessStartInfo.ArgumentList keeps the script compatible with Windows PowerShell 5.1.
    if (@($AdbArguments | Where-Object { $_ -match '\s' }).Count -gt 0) {
        throw "Internal error: Start-AdbCapture received an argument containing whitespace."
    }
    $startInfo.Arguments = $AdbArguments -join " "
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw "Could not start adb." }
    return $process
}

function Complete-AdbCapture {
    param([Diagnostics.Process]$Process)
    $stdout = $Process.StandardOutput.ReadToEnd()
    $stderr = $Process.StandardError.ReadToEnd()
    $Process.WaitForExit()
    $result = [pscustomobject]@{
        ExitCode = $Process.ExitCode
        Output = $stdout
        Error = $stderr
    }
    $Process.Dispose()
    return $result
}

function Get-TopMetrics([string]$Text, [string]$TargetPackage) {
    $line = @($Text -split "`r?`n" | Where-Object { $_ -match [regex]::Escape($TargetPackage) }) | Select-Object -First 1
    if (-not $line) { return $null }
    if ($line -match '^\s*(?<pid>\d+)\s+\d+\s+(?<cpu>\d+(?:\.\d+)?)%\s+\S+\s+\d+\s+(?<vss>\d+)K\s+(?<rss>\d+)K') {
        return [pscustomobject]@{
            pid = [int]$Matches.pid
            cpuPercent = [double]$Matches.cpu
            virtualSizeKb = [long]$Matches.vss
            residentSizeKb = [long]$Matches.rss
        }
    }
    return $null
}

function Get-MemoryMetrics([string]$Text) {
    $totalPssKb = $null
    $totalSwapKb = $null
    $nativePssKb = $null
    $javaHeapKb = $null
    foreach ($line in ($Text -split "`r?`n")) {
        if ($line -match '^\s*TOTAL:\s+(?<total>\d+)(?:\s+TOTAL SWAP \(KB\):\s+(?<swap>\d+))?') {
            $totalPssKb = [long]$Matches.total
            if ($Matches.swap) { $totalSwapKb = [long]$Matches.swap }
        } elseif ($line -match '^\s*Native Heap\s+(?<native>\d+)\s+') {
            $nativePssKb = [long]$Matches.native
        } elseif ($null -eq $nativePssKb -and $line -match '^\s*Native Heap:\s+(?<native>\d+)\s*$') {
            # Some dumpsys variants expose only the App Summary value.
            $nativePssKb = [long]$Matches.native
        } elseif ($line -match '^\s*Java Heap:\s+(?<java>\d+)\s*$') {
            $javaHeapKb = [long]$Matches.java
        }
    }
    return [pscustomobject]@{
        totalPssKb = $totalPssKb
        nativePssKb = $nativePssKb
        javaHeapKb = $javaHeapKb
        totalSwapKb = $totalSwapKb
    }
}

function Get-BenchmarkEventLineCount([string]$TargetSerial, [string]$TargetPackage) {
    $result = Invoke-Adb @(
        "-s", $TargetSerial, "shell", "run-as", $TargetPackage,
        "wc", "-l", "files/benchmark/events.jsonl"
    ) -AllowFailure
    if ($result.ExitCode -ne 0) { return 0L }
    $text = ($result.Output -join "`n").Trim()
    if ($text -notmatch '^\s*(?<count>\d+)') {
        throw "Could not parse benchmark event line count: $text"
    }
    return [long]$Matches.count
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

function Write-Json([string]$Path, $Value) {
    Write-Utf8NoBom $Path (($Value | ConvertTo-Json -Depth 12) + [Environment]::NewLine)
}

function Write-JsonLine([string]$Path, $Value) {
    [IO.File]::AppendAllText($Path, (($Value | ConvertTo-Json -Depth 8 -Compress) + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
}

$safeScenario = Convert-ToSafeName $Scenario
$runName = "{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss"), $safeScenario
$baseOutput = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $repoRoot $OutputDirectory }
$runDirectory = Join-Path $baseOutput $runName
[void](New-Item -ItemType Directory -Path $runDirectory -Force)
$samplesPath = Join-Path $runDirectory "samples.jsonl"
$allEventsPath = Join-Path $runDirectory "app-events-all.jsonl"
$eventsPath = Join-Path $runDirectory "app-events.jsonl"
$runPath = Join-Path $runDirectory "run.json"
$invokedUtc = [DateTimeOffset]::UtcNow
$startedUtc = $null
$startedEpochMillis = $null
$targetDurationMillis = [long][Math]::Round($DurationMinutes * 60 * 1000)
$run = [ordered]@{
    schemaVersion = 1
    status = "RUNNING"
    serial = $Serial
    packageName = $PackageName
    scenario = $Scenario
    requestedDurationMillis = $targetDurationMillis
    sampleIntervalMillis = $SampleIntervalSeconds * 1000
    invokedAtUtc = $invokedUtc.ToString("o")
    startedAtUtc = $null
    startedAtEpochMillis = $startedEpochMillis
    endedAtUtc = $null
    endedAtEpochMillis = $null
    collectionFinishedAtUtc = $null
    actualDurationMillis = $null
    sampleCount = 0
    appEventCount = 0
    startAppEventLineCount = $null
    endAppEventLineCount = $null
    eventSelection = "benchmark log line range captured at run boundaries"
    completionError = $null
}
Write-Json $runPath $run

Push-Location $repoRoot
try {
    $adbVersion = Invoke-Adb -AdbArguments @("version")
    $state = Invoke-Adb -AdbArguments @("-s", $Serial, "get-state")
    if (($state.Output -join "`n").Trim() -ne "device") { throw "Device $Serial is not available." }

    $facts = [ordered]@{
        collectedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
        serial = $Serial
        adbVersion = ($adbVersion.Output -join "`n").Trim()
        manufacturer = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.product.manufacturer")).Output -join "`n").Trim()
        model = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.product.model")).Output -join "`n").Trim()
        device = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.product.device")).Output -join "`n").Trim()
        androidRelease = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.build.version.release")).Output -join "`n").Trim()
        apiLevel = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.build.version.sdk")).Output -join "`n").Trim()
        abi = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.product.cpu.abi")).Output -join "`n").Trim()
        abiList = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.product.cpu.abilist")).Output -join "`n").Trim()
        buildFingerprint = ((Invoke-Adb @("-s", $Serial, "shell", "getprop", "ro.build.fingerprint")).Output -join "`n").Trim()
        cpuPresent = ((Invoke-Adb @("-s", $Serial, "shell", "cat", "/sys/devices/system/cpu/present") -AllowFailure).Output -join "`n").Trim()
        memoryInfo = ((Invoke-Adb @("-s", $Serial, "shell", "cat", "/proc/meminfo") -AllowFailure).Output -join "`n").Trim()
        packageDump = ((Invoke-Adb @("-s", $Serial, "shell", "dumpsys", "package", $PackageName) -AllowFailure).Output -join "`n").Trim()
    }
    Write-Json (Join-Path $runDirectory "device-facts.json") $facts

    $prefListing = Invoke-Adb @("-s", $Serial, "shell", "run-as", $PackageName, "ls", "shared_prefs") -AllowFailure
    Write-Utf8NoBom (Join-Path $runDirectory "settings-files.txt") (($prefListing.Output -join [Environment]::NewLine) + [Environment]::NewLine)
    if ($prefListing.ExitCode -eq 0) {
        foreach ($preferenceFile in @($prefListing.Output | Where-Object { $_ -match '\.xml\s*$' })) {
            $preferenceFile = $preferenceFile.Trim()
            $preference = Invoke-Adb @("-s", $Serial, "shell", "run-as", $PackageName, "cat", "shared_prefs/$preferenceFile") -AllowFailure
            if ($preference.ExitCode -eq 0) {
                Write-Utf8NoBom (Join-Path $runDirectory ("settings-" + (Convert-ToSafeName $preferenceFile))) (($preference.Output -join [Environment]::NewLine) + [Environment]::NewLine)
            }
        }
    }

    $gfxBefore = Invoke-Adb @("-s", $Serial, "shell", "dumpsys", "gfxinfo", $PackageName) -AllowFailure
    Write-Utf8NoBom (Join-Path $runDirectory "gfxinfo-before.txt") (($gfxBefore.Output -join [Environment]::NewLine) + [Environment]::NewLine)
    [void](Invoke-Adb @("-s", $Serial, "shell", "dumpsys", "gfxinfo", $PackageName, "reset") -AllowFailure)

    $startAppEventLineCount = Get-BenchmarkEventLineCount $Serial $PackageName
    $startedUtc = [DateTimeOffset]::UtcNow
    $startedEpochMillis = Get-EpochMillis
    $run.startedAtUtc = $startedUtc.ToString("o")
    $run.startedAtEpochMillis = $startedEpochMillis
    $run.startAppEventLineCount = $startAppEventLineCount
    Write-Json $runPath $run
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $sampleIndex = 0
    while ($stopwatch.ElapsedMilliseconds -lt $targetDurationMillis) {
        $scheduledOffsetMillis = [long]$sampleIndex * $SampleIntervalSeconds * 1000
        $waitMillis = $scheduledOffsetMillis - $stopwatch.ElapsedMilliseconds
        if ($waitMillis -gt 0) { Start-Sleep -Milliseconds $waitMillis }
        if ($stopwatch.ElapsedMilliseconds -ge $targetDurationMillis) { break }

        $sampleStarted = $stopwatch.ElapsedMilliseconds
        $timestampEpochMillis = Get-EpochMillis
        $topProcess = Start-AdbCapture @("-s", $Serial, "shell", "top", "-n", "1", "-d", "0")
        $memoryProcess = Start-AdbCapture @("-s", $Serial, "shell", "dumpsys", "meminfo", $PackageName)
        $topResult = Complete-AdbCapture $topProcess
        $memoryResult = Complete-AdbCapture $memoryProcess
        $top = Get-TopMetrics $topResult.Output $PackageName
        $memory = Get-MemoryMetrics $memoryResult.Output
        $errors = @()
        if ($topResult.ExitCode -ne 0) { $errors += "top: $($topResult.Error.Trim())" }
        if ($memoryResult.ExitCode -ne 0) { $errors += "meminfo: $($memoryResult.Error.Trim())" }
        if (-not $top) { $errors += "process missing from top output" }
        Write-JsonLine $samplesPath ([ordered]@{
            sampleIndex = $sampleIndex
            timestampEpochMillis = $timestampEpochMillis
            offsetMillis = $sampleStarted
            scheduledOffsetMillis = $scheduledOffsetMillis
            scheduleDelayMillis = [long]$sampleStarted - $scheduledOffsetMillis
            collectionDurationMillis = [long]$stopwatch.ElapsedMilliseconds - $sampleStarted
            processAlive = ($null -ne $top)
            pid = if ($top) { $top.pid } else { $null }
            cpuPercent = if ($top) { $top.cpuPercent } else { $null }
            residentSizeKb = if ($top) { $top.residentSizeKb } else { $null }
            virtualSizeKb = if ($top) { $top.virtualSizeKb } else { $null }
            totalPssKb = $memory.totalPssKb
            nativePssKb = $memory.nativePssKb
            javaHeapKb = $memory.javaHeapKb
            totalSwapKb = $memory.totalSwapKb
            errors = $errors
        })
        $sampleIndex++
    }
    $stopwatch.Stop()

    $endAppEventLineCount = Get-BenchmarkEventLineCount $Serial $PackageName
    if ($endAppEventLineCount -lt $startAppEventLineCount) {
        throw "Benchmark log was truncated or deleted during collection (start=$startAppEventLineCount end=$endAppEventLineCount)."
    }
    $endedUtc = [DateTimeOffset]::UtcNow
    $endedEpochMillis = Get-EpochMillis
    $actualDurationMillis = [long]$stopwatch.ElapsedMilliseconds
    $gfxAfter = Invoke-Adb @("-s", $Serial, "shell", "dumpsys", "gfxinfo", $PackageName) -AllowFailure
    Write-Utf8NoBom (Join-Path $runDirectory "gfxinfo-after.txt") (($gfxAfter.Output -join [Environment]::NewLine) + [Environment]::NewLine)

    $events = Invoke-Adb @("-s", $Serial, "shell", "run-as", $PackageName, "cat", "files/benchmark/events.jsonl") -AllowFailure
    Write-Utf8NoBom $allEventsPath (($events.Output -join [Environment]::NewLine) + [Environment]::NewLine)
    $selectedEvents = [Collections.Generic.List[string]]::new()
    # Android adb on Windows can expose CRCRLF as an empty PowerShell item between JSONL records.
    # wc -l counts records on-device, so normalize empty host items before applying those boundaries.
    $eventLines = @($events.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($eventLines.Count -lt $endAppEventLineCount) {
        throw "Benchmark log contained fewer lines than the end boundary (actual=$($eventLines.Count) expected=$endAppEventLineCount)."
    }
    for ($lineIndex = $startAppEventLineCount; $lineIndex -lt $endAppEventLineCount; $lineIndex++) {
        $line = $eventLines[$lineIndex]
        try { $null = $line | ConvertFrom-Json } catch { throw "Invalid benchmark JSON at line $($lineIndex + 1)." }
        $selectedEvents.Add($line)
    }
    Write-Utf8NoBom $eventsPath (($selectedEvents -join [Environment]::NewLine) + $(if ($selectedEvents.Count) { [Environment]::NewLine } else { "" }))

    $run.status = "COMPLETE"
    $run.endedAtUtc = $endedUtc.ToString("o")
    $run.endedAtEpochMillis = $endedEpochMillis
    $run.collectionFinishedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    $run.actualDurationMillis = $actualDurationMillis
    $run.sampleCount = $sampleIndex
    $run.appEventCount = $selectedEvents.Count
    $run.endAppEventLineCount = $endAppEventLineCount
    Write-Json $runPath $run
    Write-Host "Collection complete: $runDirectory"
    Write-Host "Samples: $sampleIndex; app events: $($selectedEvents.Count); actual duration: $($run.actualDurationMillis) ms"
} catch {
    $run.status = "ERROR"
    $run.endedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    $run.endedAtEpochMillis = Get-EpochMillis
    $run.collectionFinishedAtUtc = $run.endedAtUtc
    $run.actualDurationMillis = if ($null -ne $startedEpochMillis) { [long]$run.endedAtEpochMillis - [long]$startedEpochMillis } else { $null }
    $run.completionError = $_.Exception.Message
    Write-Json $runPath $run
    throw
} finally {
    Pop-Location
}
