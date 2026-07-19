param(
    [string]$Destination = "data/downloads",
    [switch]$AcceptTerms,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$destinationRoot = Join-Path $repoRoot $Destination
$target = Join-Path $destinationRoot "jvs_ver1.zip"
$partial = "$target.download"
$metadataPath = Join-Path $destinationRoot "jvs_ver1.download-metadata.json"

$sourcePageUrl = "https://sites.google.com/site/shinnosuketakamichi/research-topics/jvs_corpus"
$downloadUrl = "https://drive.usercontent.google.com/download?id=19oAw8wWn3Y7z6CKChRdAyGOB9yupL_Xt&export=download&confirm=t"
$expectedSizeBytes = 3536595425L
$expectedSha256 = "37180E2F87BD1A3E668D7C020378F77CEBF61DD57D4D74C71EB0114F386A3999"
$pinNote = "Reproducibility pin measured from the archive downloaded on 2026-07-13; not an official publisher checksum."

if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
    throw "The supported JVS download path runs on native Windows."
}
if (-not $AcceptTerms) {
    throw "Read the JVS terms at $sourcePageUrl. The corpus is local-only, must not be redistributed, and commercial use requires separate confirmation. Rerun with -AcceptTerms only after accepting those terms."
}
$curl = Get-Command curl.exe -ErrorAction SilentlyContinue
if ($null -eq $curl) {
    throw "curl.exe is required for a resumable JVS download."
}

function Test-PinnedArchive {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $false
    }
    if ((Get-Item -LiteralPath $Path).Length -ne $expectedSizeBytes) {
        return $false
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $expectedSha256
}

function Write-DownloadMetadata {
    param([string]$ArchivePath)
    $archive = Get-Item -LiteralPath $ArchivePath
    $metadata = [ordered]@{
        schemaVersion = 1
        dataset = "JVS Corpus"
        sourcePageUrl = $sourcePageUrl
        downloadUrl = $downloadUrl
        googleDriveFileId = "19oAw8wWn3Y7z6CKChRdAyGOB9yupL_Xt"
        archiveFile = $archive.Name
        expectedSizeBytes = $expectedSizeBytes
        actualSizeBytes = $archive.Length
        sha256 = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        checksumAuthority = "local-reproducibility-pin"
        checksumNote = $pinNote
        termsAcceptedByCommandFlag = $true
        verifiedAtUtc = [DateTime]::UtcNow.ToString("o")
    }
    $temporaryMetadata = "$metadataPath.tmp"
    $metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $temporaryMetadata -Encoding utf8
    Move-Item -LiteralPath $temporaryMetadata -Destination $metadataPath -Force
}

New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
if (Test-PinnedArchive -Path $target) {
    Write-DownloadMetadata -ArchivePath $target
    Write-Host "Verified pinned JVS archive: $target"
    Write-Host $pinNote
    exit 0
}

if ((Test-Path -LiteralPath $target) -and -not $Force) {
    throw "Existing JVS archive does not match the pinned size/hash: $target. Use -Force to replace it."
}
if ($Force) {
    Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
}

Write-Host "Downloading the pinned JVS archive with resume support."
Write-Host "Partial file: $partial"
$curlArguments = @(
    "--location",
    "--fail",
    "--retry", "5",
    "--retry-all-errors",
    "--continue-at", "-",
    "--output", $partial,
    $downloadUrl
)
& $curl.Source @curlArguments
if ($LASTEXITCODE -ne 0) {
    throw "JVS download failed with curl exit code $LASTEXITCODE. The partial file is retained for the next resumed run."
}

$actualSize = (Get-Item -LiteralPath $partial).Length
if ($actualSize -ne $expectedSizeBytes) {
    throw "JVS archive size mismatch: expected=$expectedSizeBytes, actual=$actualSize. The partial file is retained."
}
$actualSha256 = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash
if ($actualSha256 -ne $expectedSha256) {
    throw "JVS archive SHA-256 mismatch: expected=$expectedSha256, actual=$actualSha256. This hash is a local reproducibility pin, not an official publisher checksum."
}

Move-Item -LiteralPath $partial -Destination $target -Force
Write-DownloadMetadata -ArchivePath $target
Write-Host "Downloaded and verified the local-only JVS archive: $target"
Write-Host $pinNote
