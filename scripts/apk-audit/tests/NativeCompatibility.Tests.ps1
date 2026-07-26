. "$PSScriptRoot/../Test-NativeCompatibility.ps1"

Describe 'Native compatibility' {
    It 'rejects unexpected ABI and duplicate libc++' {
        $inventory = [pscustomobject]@{
            nativeLibraries = @(
                [pscustomobject]@{ abi = 'armeabi-v7a'; name = 'libc++_shared.so'; sha256 = 'a' },
                [pscustomobject]@{ abi = 'armeabi-v7a'; name = 'libc++_shared.so'; sha256 = 'a' },
                [pscustomobject]@{ abi = 'x86'; name = 'libsample.so'; sha256 = 'b' }
            )
        }
        $result = Test-NativeInventory -Inventory $inventory -AllowedAbis @('armeabi-v7a')
        $result.passed | Should Be $false
        ($result.errors -contains 'UNEXPECTED_ABI:x86') | Should Be $true
        ($result.errors -contains 'DUPLICATE_LIBCXX:armeabi-v7a') | Should Be $true
    }

    It 'rejects colliding ONNX Runtime binaries for one ABI' {
        $inventory = [pscustomobject]@{
            nativeLibraries = @(
                [pscustomobject]@{ abi = 'armeabi-v7a'; name = 'libonnxruntime.so'; sha256 = 'a' },
                [pscustomobject]@{ abi = 'armeabi-v7a'; name = 'libonnxruntime.so'; sha256 = 'b' }
            )
        }
        $result = Test-NativeInventory -Inventory $inventory -AllowedAbis @('armeabi-v7a')
        ($result.errors -contains 'ORT_COLLISION:armeabi-v7a') | Should Be $true
    }

    It 'detects API23-forbidden undefined symbols' {
        $result = Test-Api23UndefinedSymbols -ReadElfOutput ' UND __write_chk@LIBC'
        $result.passed | Should Be $false
        ($result.forbiddenSymbols -contains '__write_chk') | Should Be $true
    }

    It 'accepts a single allowlisted native set' {
        $inventory = [pscustomobject]@{
            nativeLibraries = @(
                [pscustomobject]@{ abi = 'armeabi-v7a'; name = 'libsample.so'; sha256 = 'a' }
            )
        }
        (Test-NativeInventory -Inventory $inventory -AllowedAbis @('armeabi-v7a')).passed |
            Should Be $true
    }
}
