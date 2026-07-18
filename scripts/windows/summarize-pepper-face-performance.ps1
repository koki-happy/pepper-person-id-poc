param(
    [Parameter(Mandatory = $true)]
    [string]$RunDirectory,
    [ValidateRange(1.1, 20)]
    [double]$StallMultiplier = 2.0
)

$ErrorActionPreference = "Stop"

function Read-JsonLines([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return @() }
    $values = [Collections.Generic.List[object]]::new()
    foreach ($line in [IO.File]::ReadLines((Resolve-Path -LiteralPath $Path))) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $values.Add(($line | ConvertFrom-Json)) } catch { throw "Invalid JSONL in ${Path}: $line" }
    }
    return @($values)
}

function Get-PropertyValue($Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Get-Stats($Values) {
    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)
    if ($numbers.Count -eq 0) { return $null }
    $sum = 0.0
    foreach ($number in $numbers) { $sum += $number }
    $p95Index = [Math]::Max(0, [Math]::Ceiling(0.95 * $numbers.Count) - 1)
    return [ordered]@{
        count = $numbers.Count
        mean = [Math]::Round($sum / $numbers.Count, 3)
        min = [Math]::Round($numbers[0], 3)
        max = [Math]::Round($numbers[-1], 3)
        p95 = [Math]::Round($numbers[$p95Index], 3)
    }
}

function Get-NumericAttributeStats($Events, [string]$Name) {
    $values = foreach ($event in $Events) {
        $value = Get-PropertyValue $event.attributes $Name
        if ($null -ne $value -and "$value" -match '^-?\d+(?:\.\d+)?$') { [double]$value }
    }
    return Get-Stats @($values)
}

function Parse-GfxInfo([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $text = Get-Content -LiteralPath $Path -Raw
    $result = [ordered]@{ source = (Resolve-Path $Path).Path }
    $patterns = [ordered]@{
        totalFrames = 'Total frames rendered:\s*(\d+)'
        jankyFrames = 'Janky frames:\s*(\d+)\s*\(([\d.]+)%\)'
        percentile50Millis = '50th percentile:\s*(\d+)ms'
        percentile90Millis = '90th percentile:\s*(\d+)ms'
        percentile95Millis = '95th percentile:\s*(\d+)ms'
        percentile99Millis = '99th percentile:\s*(\d+)ms'
        missedVsync = 'Number Missed Vsync:\s*(\d+)'
        highInputLatency = 'Number High input latency:\s*(\d+)'
        slowUiThread = 'Number Slow UI thread:\s*(\d+)'
        slowBitmapUploads = 'Number Slow bitmap uploads:\s*(\d+)'
        slowDrawCommands = 'Number Slow issue draw commands:\s*(\d+)'
    }
    foreach ($entry in $patterns.GetEnumerator()) {
        if ($text -match $entry.Value) { $result[$entry.Key] = [long]$Matches[1] } else { $result[$entry.Key] = $null }
        if ($entry.Key -eq 'jankyFrames') {
            $result.jankyFramePercent = if ($text -match $entry.Value) { [double]$Matches[2] } else { $null }
        }
    }
    return $result
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

$resolvedRunDirectory = (Resolve-Path -LiteralPath $RunDirectory).Path
$runPath = Join-Path $resolvedRunDirectory "run.json"
if (-not (Test-Path -LiteralPath $runPath -PathType Leaf)) { throw "Missing run.json: $runPath" }
$run = Get-Content -LiteralPath $runPath -Raw | ConvertFrom-Json
$samples = @(Read-JsonLines (Join-Path $resolvedRunDirectory "samples.jsonl"))
$events = @(Read-JsonLines (Join-Path $resolvedRunDirectory "app-events.jsonl"))
$deviceFactsPath = Join-Path $resolvedRunDirectory "device-facts.json"
$deviceFacts = if (Test-Path -LiteralPath $deviceFactsPath -PathType Leaf) {
    Get-Content -LiteralPath $deviceFactsPath -Raw | ConvertFrom-Json
} else { $null }
$settingsArtifacts = @(Get-ChildItem -LiteralPath $resolvedRunDirectory -File -Filter "settings-*.xml" | ForEach-Object FullName)

$aliveSamples = @($samples | Where-Object { $_.processAlive -eq $true })
$cpuStats = Get-Stats @($aliveSamples | ForEach-Object { $_.cpuPercent })
$pssStats = Get-Stats @($aliveSamples | ForEach-Object { $_.totalPssKb })
$nativePssStats = Get-Stats @($aliveSamples | ForEach-Object { $_.nativePssKb })
$javaHeapStats = Get-Stats @($aliveSamples | ForEach-Object { $_.javaHeapKb })
$swapStats = Get-Stats @($aliveSamples | ForEach-Object {
    $value = Get-PropertyValue $_ "totalSwapKb"
    if ($null -eq $value) { $value = Get-PropertyValue $_ "swapPssKb" }
    $value
})
$collectionDurationStats = Get-Stats @($samples | ForEach-Object { $_.collectionDurationMillis })
$scheduleDelayStats = Get-Stats @($samples | ForEach-Object { $_.scheduleDelayMillis })
$sampleGaps = [Collections.Generic.List[double]]::new()
for ($index = 1; $index -lt $samples.Count; $index++) {
    $sampleGaps.Add([double]([long]$samples[$index].timestampEpochMillis - [long]$samples[$index - 1].timestampEpochMillis))
}
$sampleGapStats = Get-Stats @($sampleGaps)
$sampleIntervalMillis = [double]$run.sampleIntervalMillis
$lateSampleCount = @($samples | Where-Object { [double]$_.scheduleDelayMillis -gt $sampleIntervalMillis }).Count
$longSampleGapCount = @($sampleGaps | Where-Object { $_ -gt ($sampleIntervalMillis * 1.5) }).Count
$effectiveSampleRateHz = if ($samples.Count -gt 1) {
    $sampleSpan = [long]$samples[-1].timestampEpochMillis - [long]$samples[0].timestampEpochMillis
    if ($sampleSpan -gt 0) { [Math]::Round(($samples.Count - 1) * 1000.0 / $sampleSpan, 3) } else { $null }
} else { $null }

$pssValues = @($aliveSamples | Where-Object { $null -ne $_.totalPssKb } | ForEach-Object { [long]$_.totalPssKb })
$memoryGrowthKb = if ($pssValues.Count -gt 1) { $pssValues[-1] - $pssValues[0] } else { $null }
$processMissingSamples = @($samples | Where-Object { $_.processAlive -ne $true }).Count

$eventMetrics = [Collections.Generic.List[object]]::new()
$groups = $events | Group-Object {
    $model = Get-PropertyValue $_.attributes "model"
    "$($_.event)|$($_.status)|$model"
}
foreach ($group in $groups) {
    $first = $group.Group | Select-Object -First 1
    $eventMetrics.Add([ordered]@{
        event = $first.event
        status = $first.status
        model = Get-PropertyValue $first.attributes "model"
        durationMillis = Get-Stats @($group.Group | ForEach-Object { $_.durationMillis })
        comparisonMillis = Get-NumericAttributeStats $group.Group "comparisonMillis"
        requestTotalMillis = Get-NumericAttributeStats $group.Group "requestTotalMillis"
        captureToStateReadyMillis = Get-NumericAttributeStats $group.Group "captureToStateReadyMillis"
        nativeHeapBytes = Get-NumericAttributeStats $group.Group "nativeHeapBytes"
    })
}

$detectionEvents = @($events | Where-Object { $_.event -eq "face_detection" } | Sort-Object { [long]$_.timestampMillis })
$gaps = [Collections.Generic.List[double]]::new()
for ($index = 1; $index -lt $detectionEvents.Count; $index++) {
    $gaps.Add([double]([long]$detectionEvents[$index].timestampMillis - [long]$detectionEvents[$index - 1].timestampMillis))
}
$configuredIntervals = foreach ($event in $detectionEvents) {
    $value = Get-PropertyValue $event.attributes "analysisIntervalMillis"
    if ($null -ne $value -and "$value" -match '^\d+$') { [long]$value }
}
$intervalStats = Get-Stats @($configuredIntervals)
$nominalIntervalMillis = if ($intervalStats) { [double]$intervalStats.p95 } else { $null }
$stallThresholdMillis = if ($null -ne $nominalIntervalMillis) { [Math]::Max(2000.0, $nominalIntervalMillis * $StallMultiplier) } else { $null }
$stallGaps = if ($null -ne $stallThresholdMillis) { @($gaps | Where-Object { $_ -gt $stallThresholdMillis }) } else { @() }
$stallExcessMillis = if ($null -ne $stallThresholdMillis) {
    [double](($stallGaps | ForEach-Object { $_ - $nominalIntervalMillis } | Measure-Object -Sum).Sum)
} else { $null }
$effectiveAnalysisFps = if ($detectionEvents.Count -gt 1) {
    $span = [long]$detectionEvents[-1].timestampMillis - [long]$detectionEvents[0].timestampMillis
    if ($span -gt 0) { [Math]::Round(($detectionEvents.Count - 1) * 1000.0 / $span, 3) } else { $null }
} else { $null }
$positiveGapRates = @($gaps | Where-Object { $_ -gt 0 } | ForEach-Object { 1000.0 / $_ })
$minimumEffectiveAnalysisFps = if ($positiveGapRates.Count -gt 0) {
    [Math]::Round(($positiveGapRates | Measure-Object -Minimum).Minimum, 3)
} else { $null }
$stallGapStats = Get-Stats @($stallGaps)
$stallExcessStats = if ($null -ne $stallThresholdMillis) {
    Get-Stats @($stallGaps | ForEach-Object { $_ - $nominalIntervalMillis })
} else { $null }

$poseReadyEvents = @($events | Where-Object {
    $estimated = Get-PropertyValue $_.attributes "estimatedPoseCount"
    $_.event -eq "face_pose" -and $null -ne $estimated -and [double]$estimated -gt 0
} | Sort-Object { [long]$_.timestampMillis })
$poseUpdateFrequencyHz = if ($poseReadyEvents.Count -gt 1) {
    $poseSpan = [long]$poseReadyEvents[-1].timestampMillis - [long]$poseReadyEvents[0].timestampMillis
    if ($poseSpan -gt 0) { [Math]::Round(($poseReadyEvents.Count - 1) * 1000.0 / $poseSpan, 3) } else { $null }
} else { $null }

$pipelineFrames = @($events | Where-Object { $_.event -eq "face_pipeline_frame" } | Sort-Object { [long]$_.timestampMillis })
$counterResetDetected = $false
for ($index = 1; $index -lt $pipelineFrames.Count; $index++) {
    foreach ($name in @("analyzerInputFrameCount", "analyzedFrameCount", "throttleSkippedFrameCount")) {
        if ([long](Get-PropertyValue $pipelineFrames[$index].attributes $name) -lt
            [long](Get-PropertyValue $pipelineFrames[$index - 1].attributes $name)) {
            $counterResetDetected = $true
        }
    }
}
$pipelineCounters = $null
if ($pipelineFrames.Count -gt 1 -and -not $counterResetDetected) {
    $firstCounters = $pipelineFrames[0].attributes
    $lastCounters = $pipelineFrames[-1].attributes
    $inputDelta = [long](Get-PropertyValue $lastCounters "analyzerInputFrameCount") - [long](Get-PropertyValue $firstCounters "analyzerInputFrameCount")
    $analyzedDelta = [long](Get-PropertyValue $lastCounters "analyzedFrameCount") - [long](Get-PropertyValue $firstCounters "analyzedFrameCount")
    $skippedDelta = [long](Get-PropertyValue $lastCounters "throttleSkippedFrameCount") - [long](Get-PropertyValue $firstCounters "throttleSkippedFrameCount")
    $pipelineCounters = [ordered]@{
        observedAnalyzerInputCount = $inputDelta
        observedAnalyzedCount = $analyzedDelta
        observedThrottleSkippedCount = $skippedDelta
        analyzerThrottleDropRate = if ($inputDelta -gt 0) { [Math]::Round($skippedDelta / [double]$inputDelta, 6) } else { $null }
        counterResetDetected = $false
        semantics = "Deltas between the first and last analyzed-frame events inside the collection boundary."
    }
}
$faceCountStats = Get-NumericAttributeStats $detectionEvents "faceCount"
$faceStatusCounts = [ordered]@{}
foreach ($group in ($detectionEvents | Group-Object status)) { $faceStatusCounts[$group.Name] = $group.Count }
$alivePids = @($aliveSamples | Where-Object { $null -ne $_.pid } | ForEach-Object { [int]$_.pid } | Select-Object -Unique)
$processRestartDetected = $alivePids.Count -gt 1
$validityIssues = [Collections.Generic.List[string]]::new()
if ($run.status -ne "COMPLETE") { $validityIssues.Add("run status is $($run.status)") }
if ($processMissingSamples -gt 0) { $validityIssues.Add("process was missing in $processMissingSamples resource samples") }
if ($processRestartDetected) { $validityIssues.Add("process PID changed during the run") }
if ($counterResetDetected) { $validityIssues.Add("face pipeline counters reset during the run") }
if ($null -eq $deviceFacts) {
    $validityIssues.Add("device facts are missing")
} elseif ("$($deviceFacts.apiLevel)" -ne "23" -or "$($deviceFacts.abi)" -ne "armeabi-v7a") {
    $validityIssues.Add("device target is API $($deviceFacts.apiLevel) / $($deviceFacts.abi), not API 23 / armeabi-v7a")
}
$gfxInfo = Parse-GfxInfo (Join-Path $resolvedRunDirectory "gfxinfo-after.txt")

$unavailable = [Collections.Generic.List[object]]::new()
$unavailable.Add([ordered]@{ metric = "cameraFrameDropRate"; reason = "CameraX KEEP_ONLY_LATEST discards producer frames before the analyzer callback; the app records no offered-frame counter." })
$unavailable.Add([ordered]@{ metric = "cameraBacklog"; reason = "No producer/queue depth or discarded-frame telemetry is emitted." })
$unavailable.Add([ordered]@{ metric = "cameraToPoseLatencyMillis"; reason = "Camera capture timestamp is not correlated with face_pose events." })
$unavailable.Add([ordered]@{ metric = "cameraToVisibleUpdateLatencyMillis"; reason = "The app measures capture-to-state-ready, but Compose display/render completion is not instrumented." })
$unavailable.Add([ordered]@{ metric = "minimumCameraFps"; reason = "Camera input FPS is displayed as an in-memory RateMeter value but is not persisted as a time series." })
$unavailable.Add([ordered]@{ metric = "pureFaceDetectionMillis"; reason = "face_detection combines image conversion, rotation, YuNet inference, result parsing, and tracking." })
$unavailable.Add([ordered]@{ metric = "cpuHotspots"; reason = "The collector records process CPU; symbolized simpleperf/perfetto profiles are not available on the Pepper image." })
$unavailable.Add([ordered]@{ metric = "telemetryObserverOverhead"; reason = "Benchmark JSONL events are appended synchronously on the camera executor; their file-I/O overhead is included in full-pipeline timing and is not measured separately." })
if ($null -eq $run.startedAtEpochMillis -or $null -eq $run.endedAtEpochMillis) {
    $unavailable.Add([ordered]@{ metric = "measurementBoundaries"; reason = "run.json does not contain both measurement start and end epoch timestamps." })
}
if ($samples.Count -eq 0) {
    $unavailable.Add([ordered]@{ metric = "cpuMemoryAndProcessSurvival"; reason = "No periodic samples were collected." })
}
if ($null -eq $gfxInfo) {
    $unavailable.Add([ordered]@{ metric = "screenRendering"; reason = "gfxinfo-after.txt was not collected." })
}
if ($events.Count -eq 0) {
    $unavailable.Add([ordered]@{ metric = "appStageTimings"; reason = "No app benchmark events fell inside the measurement boundaries." })
}
if (@($events | Where-Object { $_.event -eq "face_identification" }).Count -eq 0) {
    $unavailable.Add([ordered]@{ metric = "faceIdentificationMillis"; reason = "No face_identification event fell inside the measurement boundaries." })
}
if (@($detectionEvents | Where-Object { $_.status -ne "NO_FACE" }).Count -eq 0) {
    $unavailable.Add([ordered]@{ metric = "facePresentPerformance"; reason = "No face-present detection event fell inside the measurement boundaries." })
}
if (-not $intervalStats) {
    $unavailable.Add([ordered]@{ metric = "stallCountAndDuration"; reason = "No face_detection analysisIntervalMillis values were collected, so a stall boundary cannot be derived." })
}
if ($pipelineFrames.Count -lt 2) {
    $unavailable.Add([ordered]@{ metric = "analyzerThrottleDroppedCountAndRate"; reason = "Fewer than two face_pipeline_frame counter snapshots were collected." })
} elseif ($counterResetDetected) {
    $unavailable.Add([ordered]@{ metric = "analyzerThrottleDroppedCountAndRate"; reason = "Pipeline counters reset during the run, so a single delta is invalid." })
}
if ($poseReadyEvents.Count -lt 2) {
    $unavailable.Add([ordered]@{ metric = "poseUpdateFrequency"; reason = "Fewer than two successful pose-ready events were collected." })
}
if ($settingsArtifacts.Count -eq 0) {
    $unavailable.Add([ordered]@{ metric = "exactAppSettings"; reason = "No shared-preference XML was collected." })
}
$unavailable.Add([ordered]@{ metric = "verifiedPoseActions"; reason = "Pose actions are operator-described by the scenario; the app does not emit ground-truth action labels." })

$summary = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    run = [ordered]@{
        directory = $resolvedRunDirectory
        status = $run.status
        serial = $run.serial
        packageName = $run.packageName
        scenario = $run.scenario
        startedAtUtc = $run.startedAtUtc
        endedAtUtc = $run.endedAtUtc
        requestedDurationMillis = $run.requestedDurationMillis
        actualDurationMillis = $run.actualDurationMillis
        sampleIntervalMillis = $run.sampleIntervalMillis
        sampleCount = $samples.Count
        appEventCount = $events.Count
        validRunIntegrity = $validityIssues.Count -eq 0
        validityIssues = @($validityIssues)
    }
    device = $deviceFacts
    artifacts = [ordered]@{
        deviceFacts = $deviceFactsPath
        settings = $settingsArtifacts
        samples = Join-Path $resolvedRunDirectory "samples.jsonl"
        appEvents = Join-Path $resolvedRunDirectory "app-events.jsonl"
        gfxinfoBefore = Join-Path $resolvedRunDirectory "gfxinfo-before.txt"
        gfxinfoAfter = Join-Path $resolvedRunDirectory "gfxinfo-after.txt"
    }
    process = [ordered]@{
        aliveSampleCount = $aliveSamples.Count
        missingSampleCount = $processMissingSamples
        survivedEntireRun = ($samples.Count -gt 0 -and $processMissingSamples -eq 0 -and -not $processRestartDetected)
        processRestartDetected = $processRestartDetected
        observedPids = $alivePids
        cpuPercent = $cpuStats
        cpuPercentSemantics = "Android top process CPU; values may exceed 100% when multiple logical cores are used."
        collectionDurationMillis = $collectionDurationStats
        scheduleDelayMillis = $scheduleDelayStats
        sampleCadence = [ordered]@{
            targetIntervalMillis = $run.sampleIntervalMillis
            actualGapMillis = $sampleGapStats
            effectiveRateHz = $effectiveSampleRateHz
            scheduleDelayOverOneIntervalCount = $lateSampleCount
            actualGapOverOneAndHalfIntervalsCount = $longSampleGapCount
        }
    }
    memory = [ordered]@{
        totalPssKb = $pssStats
        nativePssKb = $nativePssStats
        javaHeapKb = $javaHeapStats
        totalSwapKb = $swapStats
        totalPssGrowthKb = $memoryGrowthKb
    }
    appEvents = @($eventMetrics)
    faceObservations = [ordered]@{
        faceCount = $faceCountStats
        statusCounts = $faceStatusCounts
    }
    pipelineCounters = $pipelineCounters
    poseUpdates = [ordered]@{
        successfulPoseEventCount = $poseReadyEvents.Count
        effectiveUpdateFrequencyHz = $poseUpdateFrequencyHz
    }
    analysisCadence = [ordered]@{
        faceDetectionEventCount = $detectionEvents.Count
        effectiveAnalysisFps = $effectiveAnalysisFps
        minimumEffectiveAnalysisFps = $minimumEffectiveAnalysisFps
        eventGapMillis = Get-Stats @($gaps)
        configuredIntervalMillis = $intervalStats
        stallRule = if ($null -ne $stallThresholdMillis) { "gap > max(2000 ms, configured interval x $StallMultiplier)" } else { $null }
        stallThresholdMillis = $stallThresholdMillis
        stallCount = if ($null -ne $stallThresholdMillis) { $stallGaps.Count } else { $null }
        stallExcessMillis = $stallExcessMillis
        stallGapMillis = $stallGapStats
        stallExcessPerGapMillis = $stallExcessStats
    }
    gfxinfo = $gfxInfo
    unavailableMetrics = @($unavailable)
}

$summaryJsonPath = Join-Path $resolvedRunDirectory "summary.json"
Write-Utf8NoBom $summaryJsonPath (($summary | ConvertTo-Json -Depth 12) + [Environment]::NewLine)

function Format-Stats($Stats, [string]$Unit = "") {
    if ($null -eq $Stats) { return "unavailable" }
    return "n=$($Stats.count), avg=$($Stats.mean)$Unit, max=$($Stats.max)$Unit, P95=$($Stats.p95)$Unit, min=$($Stats.min)$Unit"
}

$markdown = [Text.StringBuilder]::new()
[void]$markdown.AppendLine("# Pepper face performance summary")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("- Scenario: ``$($run.scenario)``")
[void]$markdown.AppendLine("- Device: ``$($run.serial)``")
[void]$markdown.AppendLine("- Status: ``$($run.status)``")
[void]$markdown.AppendLine("- Run integrity valid: ``$($summary.run.validRunIntegrity)``$(if ($validityIssues.Count) { " ($($validityIssues -join '; '))" })")
[void]$markdown.AppendLine("- Exact duration: $($run.actualDurationMillis) ms (requested $($run.requestedDurationMillis) ms)")
[void]$markdown.AppendLine("- Samples / app events: $($samples.Count) / $($events.Count)")
[void]$markdown.AppendLine("- Face count: $(Format-Stats $faceCountStats); statuses: $(($faceStatusCounts.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', ')")
[void]$markdown.AppendLine("- Settings artifacts: $(if ($settingsArtifacts.Count) { $settingsArtifacts -join ', ' } else { 'unavailable' })")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("## Process and memory")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("| Metric | Result |")
[void]$markdown.AppendLine("|---|---|")
[void]$markdown.AppendLine("| Process CPU | $(Format-Stats $cpuStats '%') |")
[void]$markdown.AppendLine("| Actual CPU/PSS sample gap | $(Format-Stats $sampleGapStats ' ms') |")
[void]$markdown.AppendLine("| Effective CPU/PSS sample rate | $(if ($null -ne $effectiveSampleRateHz) { "$effectiveSampleRateHz Hz" } else { 'unavailable' }); gaps >1.5x target=$longSampleGapCount |")
[void]$markdown.AppendLine("| Total PSS | $(Format-Stats $pssStats ' KB') |")
[void]$markdown.AppendLine("| Native PSS | $(Format-Stats $nativePssStats ' KB') |")
[void]$markdown.AppendLine("| Java heap | $(Format-Stats $javaHeapStats ' KB') |")
[void]$markdown.AppendLine("| Total swap | $(Format-Stats $swapStats ' KB') |")
[void]$markdown.AppendLine("| Total PSS first-to-last growth | $(if ($null -ne $memoryGrowthKb) { "$memoryGrowthKb KB" } else { 'unavailable' }) |")
[void]$markdown.AppendLine("| Process survival | $($aliveSamples.Count)/$($samples.Count) alive samples; survived=$($summary.process.survivedEntireRun) |")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("CPU is Android ``top`` process CPU and may exceed 100% when the process uses multiple logical cores.")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("## App event timings")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("| Event | Status | Model | Duration | Request total | State ready | Comparison |")
[void]$markdown.AppendLine("|---|---|---|---:|---:|---:|---:|")
foreach ($metric in $eventMetrics) {
    [void]$markdown.AppendLine("| $($metric.event) | $($metric.status) | $($metric.model) | $(Format-Stats $metric.durationMillis ' ms') | $(Format-Stats $metric.requestTotalMillis ' ms') | $(Format-Stats $metric.captureToStateReadyMillis ' ms') | $(Format-Stats $metric.comparisonMillis ' ms') |")
}
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("## Cadence and stalls")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("- Effective face-analysis rate from event timestamps: $(if ($null -ne $effectiveAnalysisFps) { "$effectiveAnalysisFps fps" } else { 'unavailable' })")
[void]$markdown.AppendLine("- Minimum inter-event effective analysis rate: $(if ($null -ne $minimumEffectiveAnalysisFps) { "$minimumEffectiveAnalysisFps fps" } else { 'unavailable' })")
[void]$markdown.AppendLine("- Successful pose-ready update rate: $(if ($null -ne $poseUpdateFrequencyHz) { "$poseUpdateFrequencyHz Hz ($($poseReadyEvents.Count) events)" } else { 'unavailable' })")
[void]$markdown.AppendLine("- Analyzer throttle drops: $(if ($pipelineCounters) { "$($pipelineCounters.observedThrottleSkippedCount)/$($pipelineCounters.observedAnalyzerInputCount) ($($pipelineCounters.analyzerThrottleDropRate))" } else { 'unavailable' })")
[void]$markdown.AppendLine("- Event gaps: $(Format-Stats (Get-Stats @($gaps)) ' ms')")
[void]$markdown.AppendLine("- Stall rule: $(if ($summary.analysisCadence.stallRule) { $summary.analysisCadence.stallRule } else { 'unavailable' })")
[void]$markdown.AppendLine("- Stalls: $(if ($summary.analysisCadence.stallCount -gt 0) { "$($summary.analysisCadence.stallCount), max gap $($summary.analysisCadence.stallGapMillis.max) ms, total excess $($summary.analysisCadence.stallExcessMillis) ms" } elseif ($summary.analysisCadence.stallCount -eq 0) { '0' } else { 'unavailable' })")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("## gfxinfo")
[void]$markdown.AppendLine()
if ($summary.gfxinfo) {
    [void]$markdown.AppendLine("- Frames / janky: $($summary.gfxinfo.totalFrames) / $($summary.gfxinfo.jankyFrames) ($($summary.gfxinfo.jankyFramePercent)%)")
    [void]$markdown.AppendLine("- Frame latency P90/P95/P99: $($summary.gfxinfo.percentile90Millis)/$($summary.gfxinfo.percentile95Millis)/$($summary.gfxinfo.percentile99Millis) ms")
} else {
    [void]$markdown.AppendLine("unavailable")
}
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("## Unavailable metrics")
[void]$markdown.AppendLine()
foreach ($item in $unavailable) { [void]$markdown.AppendLine("- ``$($item.metric)``: $($item.reason)") }

$summaryMarkdownPath = Join-Path $resolvedRunDirectory "summary.md"
Write-Utf8NoBom $summaryMarkdownPath $markdown.ToString()
Write-Host "Summary written: $summaryJsonPath"
Write-Host "Report written: $summaryMarkdownPath"
