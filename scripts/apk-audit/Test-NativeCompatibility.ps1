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

if ($MyInvocation.InvocationName -ne '.') {
    throw 'Dot-source this script and call Test-NativeInventory or Test-Api23UndefinedSymbols.'
}
