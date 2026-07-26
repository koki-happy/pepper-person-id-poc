. "$PSScriptRoot/../Invoke-ApkAudit.ps1"

Describe 'APK inventory' {
    It 'reports entries, native ABI, model hashes, and APK hash' {
        $root = Join-Path $TestDrive 'apk'
        New-Item -ItemType Directory -Path (Join-Path $root 'lib/armeabi-v7a') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $root 'assets/models') -Force | Out-Null
        [IO.File]::WriteAllBytes((Join-Path $root 'lib/armeabi-v7a/libsample.so'), [byte[]](1, 2, 3))
        [IO.File]::WriteAllBytes((Join-Path $root 'assets/models/model.onnx'), [byte[]](4, 5, 6))
        $apk = Join-Path $TestDrive 'sample.apk'
        Compress-Archive -Path (Join-Path $root '*') -DestinationPath $apk

        $inventory = Get-ApkInventory -ApkPath $apk

        $inventory.entryCount | Should Be 2
        $inventory.abiList | Should Be @('armeabi-v7a')
        $inventory.nativeLibraries[0].name | Should Be 'libsample.so'
        $inventory.modelAssets[0].path | Should Be 'assets/models/model.onnx'
        $inventory.apkSha256 | Should Match '^[0-9a-f]{64}$'
    }
}
