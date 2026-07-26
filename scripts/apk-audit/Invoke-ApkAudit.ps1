Set-StrictMode -Version Latest

function Get-StreamSha256 {
    param([Parameter(Mandatory)][System.IO.Stream]$Stream)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        ([BitConverter]::ToString($algorithm.ComputeHash($Stream))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $algorithm.Dispose()
    }
}

function Get-ApkInventory {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$ApkPath)

    $resolved = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($resolved)
    try {
        $entries = @($archive.Entries | Where-Object { -not [string]::IsNullOrEmpty($_.Name) } | ForEach-Object {
            $entry = $_
            $stream = $entry.Open()
            try {
                [pscustomobject]@{
                    path = $entry.FullName.Replace('\', '/')
                    sizeBytes = [long]$entry.Length
                    compressedSizeBytes = [long]$entry.CompressedLength
                    sha256 = Get-StreamSha256 -Stream $stream
                }
            }
            finally {
                $stream.Dispose()
            }
        })
    }
    finally {
        $archive.Dispose()
    }
    $native = @($entries | Where-Object { $_.path -match '^lib/([^/]+)/(.+\.so)$' } | ForEach-Object {
        [pscustomobject]@{
            path = $_.path
            abi = ([regex]::Match($_.path, '^lib/([^/]+)/')).Groups[1].Value
            name = [System.IO.Path]::GetFileName($_.path)
            sizeBytes = $_.sizeBytes
            sha256 = $_.sha256
        }
    })
    $models = @($entries | Where-Object {
        $_.path -match '\.(onnx|tflite|mnn|bin|param|xml)$'
    })
    [pscustomobject]@{
        schemaVersion = 1
        apkPath = $resolved
        apkSha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
        apkSizeBytes = (Get-Item -LiteralPath $resolved).Length
        entryCount = $entries.Count
        entries = $entries
        nativeLibraries = $native
        modelAssets = $models
        abiList = @($native.abi | Sort-Object -Unique)
        uncompressedEntryBytes = [long](($entries | Measure-Object sizeBytes -Sum).Sum)
    }
}

function Invoke-ApkAudit {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ApkPath,
        [Parameter(Mandatory)][string]$OutputPath
    )
    $inventory = Get-ApkInventory -ApkPath $ApkPath
    $parent = Split-Path -Parent $OutputPath
    if ($parent) { [System.IO.Directory]::CreateDirectory($parent) | Out-Null }
    $inventory | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8
    $inventory
}

if ($MyInvocation.InvocationName -ne '.') {
    Invoke-ApkAudit @args
}
