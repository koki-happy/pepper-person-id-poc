$acceptanceRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $acceptanceRoot "Get-DeviceEvidence.ps1")
. (Join-Path $acceptanceRoot "Invoke-SoakTest.ps1")

Describe "Privacy file scan" {
    It "allows ordinary preferences and database metadata" {
        $result = Test-PrivacyFileList -Paths @(
            "./shared_prefs/settings.xml",
            "./databases/app.db",
            "./cache/http-cache"
        )
        $result.IsClean | Should Be $true
        @($result.Violations).Count | Should Be 0
    }

    It "flags raw media and numeric-array files" {
        $result = Test-PrivacyFileList -Paths @(
            "./files/capture.wav",
            "./cache/face.jpg",
            "./files/vector.npy"
        )
        $result.IsClean | Should Be $false
        @($result.Violations).Count | Should Be 3
    }

    It "flags explicit biometric embedding or cluster stores" {
        $result = Test-PrivacyFileList -Paths @(
            "./files/biometric/session.dat",
            "./files/speaker_embedding.dat",
            "./files/anonymous-clusters/state.json"
        )
        $result.IsClean | Should Be $false
        @($result.Violations).Count | Should Be 3
    }
}

Describe "Package crash parser" {
    It "finds Java, ANR, and native package crashes" {
        $lines = @(
            "07-26 AndroidRuntime: Process: com.example.app, PID: 42",
            "07-26 AndroidRuntime: FATAL EXCEPTION: main",
            "07-26 ActivityManager: ANR in com.example.app",
            "07-26 DEBUG: pid: 42, name: com.example.app",
            "07-26 libc: Fatal signal 11 (SIGSEGV), code 1 in tid 42 (com.example.app)"
        )
        $findings = @(Get-CrashFindings -Lines $lines -PackageName "com.example.app")
        @($findings | Where-Object kind -eq "java-fatal-exception").Count | Should Be 1
        @($findings | Where-Object kind -eq "anr").Count | Should Be 1
        @($findings | Where-Object kind -eq "native-fatal-signal").Count | Should Be 1
    }

    It "ignores another package crash" {
        $lines = @(
            "Process: com.example.other, PID: 7",
            "FATAL EXCEPTION: main"
        )
        @(Get-CrashFindings -Lines $lines -PackageName "com.example.app").Count | Should Be 0
    }
}

Describe "Bounded soak schedule" {
    It "bounds 15, 30, and 60 minute runs" {
        (Get-SoakSchedule -DurationMinutes 15 -SampleIntervalSeconds 30).maximumSamples | Should Be 31
        (Get-SoakSchedule -DurationMinutes 30 -SampleIntervalSeconds 30).maximumSamples | Should Be 61
        (Get-SoakSchedule -DurationMinutes 60 -SampleIntervalSeconds 30).maximumSamples | Should Be 121
    }
}
