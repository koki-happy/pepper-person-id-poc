param(
    [string]$Destination = "data/audio/smoke",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$destinationRoot = Join-Path $repoRoot $Destination
$artifacts = @(
    @{
        FileName = "fangjun-sr-1.wav"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/fangjun-sr-1.wav"
        Sha256 = "33C24061180224D2350143EE19E3AF031446995C676BD25996325D34BB20A4D5"
        Size = 73606
    },
    @{
        FileName = "fangjun-test-sr-1.wav"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/fangjun-test-sr-1.wav"
        Sha256 = "9175E523081BF6A630CE72A55B05F92148EAAFAF58CBBBE743686CD81C50848E"
        Size = 178374
    },
    @{
        FileName = "leijun-test-sr-1.wav"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/leijun-test-sr-1.wav"
        Sha256 = "36CDA04EE4D10E38095DE73B77C99E8E7C54347232967A9CDE4CF54F7D496BAB"
        Size = 263878
    }
)

New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
foreach ($artifact in $artifacts) {
    $target = Join-Path $destinationRoot $artifact.FileName
    $valid = (Test-Path -LiteralPath $target -PathType Leaf) -and
        ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $artifact.Sha256) -and
        ((Get-Item -LiteralPath $target).Length -eq [long]$artifact.Size)
    if ($valid -and -not $Force) {
        Write-Host "Verified $target"
        continue
    }

    $temporary = "$target.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $artifact.Url -OutFile $temporary
        $actualHash = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash
        $actualSize = (Get-Item -LiteralPath $temporary).Length
        if ($actualHash -ne $artifact.Sha256 -or $actualSize -ne [long]$artifact.Size) {
            throw "Verification failed for $($artifact.FileName): hash=$actualHash size=$actualSize"
        }
        Move-Item -LiteralPath $temporary -Destination $target -Force
    } catch {
        Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
        throw
    }
    Write-Host "Downloaded and verified $target"
}

Write-Host "Smoke WAV files are ready. data/audio is excluded from Git."
