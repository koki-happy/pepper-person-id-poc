[CmdletBinding()]
param(
    [Alias("Serial")]
    [string]$EntrySerial,
    [ValidateSet("Android", "Pepper")]
    [Alias("TargetClass")]
    [string]$EntryTargetClass = "Android",
    [Alias("ExpectedAbi")]
    [string]$EntryExpectedAbi,
    [Alias("ExpectedApiLevel")]
    [int]$EntryExpectedApiLevel = 0,
    [Alias("AdbPath")]
    [string]$EntryAdbPath = "adb",
    [Alias("OutputPath")]
    [string]$EntryOutputPath
)

Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "AcceptanceCommon.ps1")

function Get-AdbDeviceRecords {
    [CmdletBinding()]
    param([string]$AdbPath = "adb")

    $result = Invoke-AcceptanceProcess -FilePath $AdbPath -Arguments @("devices", "-l")
    $records = @()
    foreach ($line in $result.Output) {
        if ($line -match '^(\S+)\s+(\S+)(?:\s+(.*))?$' -and $Matches[1] -ne "List") {
            $recordSerial = [string]$Matches[1]
            $recordState = [string]$Matches[2]
            $recordMetadata = [string]$Matches[3]
            $metadata = @{}
            foreach ($token in @($recordMetadata -split '\s+')) {
                if ($token -match '^([^:]+):(.*)$') {
                    $metadata[$Matches[1]] = $Matches[2]
                }
            }
            $records += [pscustomobject]@{
                Serial = $recordSerial
                State = $recordState
                Product = [string]$metadata["product"]
                Model = [string]$metadata["model"]
                Device = [string]$metadata["device"]
                TransportId = [string]$metadata["transport_id"]
            }
        }
    }
    return @($records)
}

function Get-ExactAdbDevice {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][object[]]$Records
    )

    $matches = @($Records | Where-Object { [string]$_.Serial -ceq $Serial })
    if ($matches.Count -ne 1) {
        throw "Expected exactly one adb record for serial '$Serial'; found $($matches.Count)"
    }
    if ([string]$matches[0].State -ne "device") {
        throw "ADB serial '$Serial' is not ready; state=$($matches[0].State)"
    }
    return $matches[0]
}

function Get-AdbProperty {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$AdbPath = "adb"
    )
    $result = Invoke-AcceptanceProcess -FilePath $AdbPath -Arguments @("-s", $Serial, "shell", "getprop", $Name)
    return (($result.Output -join "`n").Trim())
}

function Assert-DeviceClassification {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$TargetClass,
        [Parameter(Mandatory = $true)][int]$ApiLevel,
        [Parameter(Mandatory = $true)][string[]]$AbiList,
        [string]$ExpectedAbi,
        [int]$ExpectedApiLevel = 0,
        [string]$Model = ""
    )

    if ($ExpectedApiLevel -gt 0 -and $ApiLevel -ne $ExpectedApiLevel) {
        throw "Serial '$Serial' API mismatch: expected $ExpectedApiLevel, actual $ApiLevel"
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedAbi) -and $ExpectedAbi -notin $AbiList) {
        throw "Serial '$Serial' ABI mismatch: expected $ExpectedAbi, actual $($AbiList -join ',')"
    }
    if ($TargetClass -eq "Android") {
        if ("arm64-v8a" -notin $AbiList) {
            throw "Android acceptance requires an ARM64 device; actual $($AbiList -join ',')"
        }
        if ($ApiLevel -lt 23) {
            throw "Android acceptance device API must be at least 23; actual $ApiLevel"
        }
        if ($ApiLevel -eq 23 -and $AbiList -contains "armeabi-v7a" -and $Model -match 'LPT_200') {
            throw "Serial '$Serial' classifies as Pepper, not Android"
        }
    } else {
        if ($ApiLevel -ne 23) {
            throw "Pepper acceptance requires exact API 23; actual $ApiLevel"
        }
        if ("armeabi-v7a" -notin $AbiList) {
            throw "Pepper acceptance requires armeabi-v7a; actual $($AbiList -join ',')"
        }
        if ($AbiList -contains "arm64-v8a") {
            throw "Pepper acceptance must not classify an ARM64 phone as Pepper"
        }
    }
}

function Get-DevicePreflight {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)]
        [ValidateSet("Android", "Pepper")]
        [string]$TargetClass,
        [string]$ExpectedAbi,
        [int]$ExpectedApiLevel = 0,
        [string]$AdbPath = "adb",
        [string]$OutputPath
    )

    $device = Get-ExactAdbDevice -Serial $Serial -Records @(Get-AdbDeviceRecords -AdbPath $AdbPath)
    $apiText = Get-AdbProperty -Serial $Serial -Name "ro.build.version.sdk" -AdbPath $AdbPath
    $apiLevel = 0
    if (-not [int]::TryParse($apiText, [ref]$apiLevel)) {
        throw "Serial '$Serial' returned invalid API level '$apiText'"
    }
    $abiText = Get-AdbProperty -Serial $Serial -Name "ro.product.cpu.abilist" -AdbPath $AdbPath
    if ([string]::IsNullOrWhiteSpace($abiText)) {
        $abiText = Get-AdbProperty -Serial $Serial -Name "ro.product.cpu.abi" -AdbPath $AdbPath
    }
    $abiList = @($abiText -split ',' | ForEach-Object { $_.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $model = Get-AdbProperty -Serial $Serial -Name "ro.product.model" -AdbPath $AdbPath
    $manufacturer = Get-AdbProperty -Serial $Serial -Name "ro.product.manufacturer" -AdbPath $AdbPath
    Assert-DeviceClassification -Serial $Serial -TargetClass $TargetClass -ApiLevel $apiLevel `
        -AbiList $abiList -ExpectedAbi $ExpectedAbi -ExpectedApiLevel $ExpectedApiLevel -Model $model

    $featureResult = Invoke-AcceptanceProcess -FilePath $AdbPath `
        -Arguments @("-s", $Serial, "shell", "pm", "list", "features")
    $features = @($featureResult.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -like "feature:*" })
    $preflight = [pscustomobject]@{
        schemaVersion = 1
        capturedAtUtc = [DateTime]::UtcNow.ToString("o")
        serial = $Serial
        targetClass = $TargetClass
        state = [string]$device.State
        product = [string]$device.Product
        model = $model
        manufacturer = $manufacturer
        device = [string]$device.Device
        apiLevel = $apiLevel
        abi = [string]$abiList[0]
        abiList = @($abiList)
        expectedApiLevel = $ExpectedApiLevel
        expectedAbi = $ExpectedAbi
        hasCamera = (@($features | Where-Object { $_ -match 'camera' }).Count -gt 0)
        hasFrontCamera = (@($features | Where-Object { $_ -eq 'feature:android.hardware.camera.front' }).Count -gt 0)
        hasMicrophone = (@($features | Where-Object { $_ -eq 'feature:android.hardware.microphone' }).Count -gt 0)
        commands = @(
            "adb devices -l",
            "adb -s <exact-serial> shell getprop <read-only-property>",
            "adb -s <exact-serial> shell pm list features"
        )
        mutatingCommandsExecuted = 0
    }
    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        $directory = Split-Path -Parent $OutputPath
        if (-not [string]::IsNullOrWhiteSpace($directory)) {
            New-Item -ItemType Directory -Force -Path $directory | Out-Null
        }
        $preflight | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }
    return $preflight
}

if ($MyInvocation.InvocationName -ne ".") {
    if ([string]::IsNullOrWhiteSpace($EntrySerial)) { throw "-Serial is required" }
    Get-DevicePreflight -Serial $EntrySerial -TargetClass $EntryTargetClass -ExpectedAbi $EntryExpectedAbi `
        -ExpectedApiLevel $EntryExpectedApiLevel -AdbPath $EntryAdbPath -OutputPath $EntryOutputPath
}
