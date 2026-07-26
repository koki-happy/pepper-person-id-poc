Set-StrictMode -Version Latest

function Test-Api23UndefinedSymbols {
    param(
        [Parameter(Mandatory)][string]$ReadElfOutput,
        [string[]]$ForbiddenSymbols = @('__write_chk')
    )
    $found = @($ForbiddenSymbols | Where-Object {
        $ReadElfOutput -match "(?m)\b$([regex]::Escape($_))\b"
    })
    [pscustomobject]@{
        passed = $found.Count -eq 0
        forbiddenSymbols = $found
    }
}

function Test-NativeInventory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Inventory,
        [Parameter(Mandatory)][string[]]$AllowedAbis
    )
    $native = @($Inventory.nativeLibraries)
    $unexpectedAbis = @($native.abi | Where-Object { $_ -notin $AllowedAbis } | Sort-Object -Unique)
    $duplicateCpp = @($native | Where-Object name -eq 'libc++_shared.so' |
        Group-Object abi | Where-Object Count -gt 1 | ForEach-Object Name)
    $ortCollisions = @($native | Where-Object name -eq 'libonnxruntime.so' |
        Group-Object abi | Where-Object {
            @($_.Group.sha256 | Sort-Object -Unique).Count -gt 1
        } | ForEach-Object Name)
    $errors = @()
    if ($unexpectedAbis.Count -gt 0) { $errors += "UNEXPECTED_ABI:$($unexpectedAbis -join ',')" }
    if ($duplicateCpp.Count -gt 0) { $errors += "DUPLICATE_LIBCXX:$($duplicateCpp -join ',')" }
    if ($ortCollisions.Count -gt 0) { $errors += "ORT_COLLISION:$($ortCollisions -join ',')" }
    [pscustomobject]@{
        passed = $errors.Count -eq 0
        errors = $errors
        unexpectedAbis = $unexpectedAbis
        duplicateLibcxxAbis = $duplicateCpp
        ortCollisionAbis = $ortCollisions
    }
}

function Invoke-NativeCompatibilityAudit {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ApkPath,
        [Parameter(Mandatory)][string[]]$AllowedAbis,
        [Parameter(Mandatory)][string]$ReadElfPath,
        [string[]]$ForbiddenApi23Symbols = @('__write_chk')
    )
    $readElf = (Resolve-Path -LiteralPath $ReadElfPath -ErrorAction Stop).Path
    $apk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
    $inventoryScript = Join-Path $PSScriptRoot 'Invoke-ApkAudit.ps1'
    . $inventoryScript
    $inventory = Get-ApkInventory -ApkPath $apk
    $inventoryResult = Test-NativeInventory -Inventory $inventory -AllowedAbis $AllowedAbis
    $symbolFailures = @()
    $temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) (
        'pepper-apk-audit-' + [guid]::NewGuid().ToString('N')
    )
    [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($library in @($inventory.nativeLibraries)) {
            if ($library.abi -notin $AllowedAbis) { continue }
            $entry = $archive.GetEntry($library.path)
            if ($null -eq $entry) {
                $symbolFailures += "MISSING_ZIP_ENTRY:$($library.path)"
                continue
            }
            $destination = Join-Path $temporaryRoot (
                $library.abi + '-' + ([IO.Path]::GetFileName($library.path))
            )
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
            $readElfOutput = & $readElf --dyn-syms --wide $destination 2>&1 | Out-String
            if ($LASTEXITCODE -ne 0) {
                $symbolFailures += "READELF_FAILED:$($library.path)"
                continue
            }
            $symbolResult = Test-Api23UndefinedSymbols `
                -ReadElfOutput $readElfOutput `
                -ForbiddenSymbols $ForbiddenApi23Symbols
            foreach ($symbol in $symbolResult.forbiddenSymbols) {
                $symbolFailures += "API23_SYMBOL:$($library.path):$symbol"
            }
        }
    }
    finally {
        $archive.Dispose()
        if ($temporaryRoot.StartsWith([IO.Path]::GetTempPath(), [StringComparison]::OrdinalIgnoreCase)) {
            [IO.Directory]::Delete($temporaryRoot, $true)
        }
    }
    $errors = @($inventoryResult.errors) + $symbolFailures
    [pscustomobject]@{
        passed = $errors.Count -eq 0
        errors = $errors
        inventory = $inventoryResult
        symbolFailures = $symbolFailures
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    Invoke-NativeCompatibilityAudit @args
}
