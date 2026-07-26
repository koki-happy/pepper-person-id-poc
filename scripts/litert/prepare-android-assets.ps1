param(
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$converter = Join-Path $PSScriptRoot "convert_models.py"
$requirementsLock = Join-Path $PSScriptRoot "requirements.lock"
$dockerfile = Join-Path $PSScriptRoot "Dockerfile"
$workDirectory = Join-Path $repoRoot "build/litert-setup"

$models = @(
    @{
        Name = "SFace 2021dec LiteRT"
        Source = "app/src/benchmark/assets/models/face_recognition_sface_2021dec.onnx"
        SourceSha256 = "0BA9FBFA01B5270C96627C4EF784DA859931E02F04419C829E83484087C34E79"
        Output = "app/src/benchmark/assets/models/face_recognition_sface_2021dec.tflite"
        OutputSha256 = "0859A63B74C8373CE47464D1834678193786282AAD35911491C0505F2CACBA1E"
        OutputSize = 38549364L
        Manifest = "scripts/litert/manifests/sface-2021dec-litert.json"
        InputShape = $null
    },
    @{
        Name = "face-reidentification-retail-0095 LiteRT"
        Source = "app/src/benchmark/assets/models/face-reidentification-retail-0095.onnx"
        SourceSha256 = "861D2EDC47214F19FE973F97A05B2BD8BA61E103279FE232F0365534903DD589"
        Output = "app/src/benchmark/assets/models/face-reidentification-retail-0095.tflite"
        OutputSha256 = "6AD2A160AB016B84A55442DCB2CD1D36B684AA9E8324357411B14A77756C68D8"
        OutputSize = 4475508L
        Manifest = "scripts/litert/manifests/face-0095-litert.json"
        InputShape = $null
    },
    @{
        Name = "YuNet 2026may LiteRT fixed 320"
        Source = "app/src/benchmark/assets/models/face_detection_yunet_2026may.onnx"
        SourceSha256 = "EBAFCE4E3C118D6554634BE5C27AB333B4C047A9A8C3FAF1D7CF93101C22F0F0"
        Output = "app/src/benchmark/assets/models/face_detection_yunet_2026may_320.tflite"
        OutputSha256 = "E9EF1BEE56DB8D5AEA88EDBA67515FC182D56EEB36ACFBF8B1DFF7F77934885D"
        OutputSize = 238836L
        Manifest = "scripts/litert/manifests/yunet-2026may-320-litert.json"
        InputShape = "input:1,3,320,320"
    }
)

function Get-VerifiedManifest {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Model
    )

    $manifestPath = Join-Path $repoRoot $Model.Manifest
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "$($Model.Name) normalized conversion manifest is missing: $($Model.Manifest)"
    }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.source.sha256.ToUpperInvariant() -ne $Model.SourceSha256 -or
        $manifest.output.sha256.ToUpperInvariant() -ne $Model.OutputSha256 -or
        [long]$manifest.output.fileSizeBytes -ne $Model.OutputSize) {
        throw "$($Model.Name) normalized manifest does not match the pinned source/output contract"
    }
    return $manifest
}

function Assert-FileHash {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedSha256,
        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description is missing: $Path"
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $ExpectedSha256) {
        throw "$Description SHA-256 mismatch: expected=$ExpectedSha256 actual=$actual"
    }
}

$missingModels = New-Object 'System.Collections.Generic.List[hashtable]'
foreach ($model in $models) {
    $null = Get-VerifiedManifest -Model $model
    $sourcePath = Join-Path $repoRoot $model.Source
    Assert-FileHash -Path $sourcePath -ExpectedSha256 $model.SourceSha256 -Description "$($model.Name) source ONNX"

    $outputPath = Join-Path $repoRoot $model.Output
    if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
        Assert-FileHash -Path $outputPath -ExpectedSha256 $model.OutputSha256 -Description "$($model.Name) output"
        if ((Get-Item -LiteralPath $outputPath).Length -ne $model.OutputSize) {
            throw "$($model.Name) output size mismatch"
        }
        Write-Host "Verified $($model.Output)"
    } else {
        $missingModels.Add($model)
    }
}

if ($missingModels.Count -eq 0) {
    exit 0
}

if (-not (Test-Path -LiteralPath $converter -PathType Leaf) -or
    -not (Test-Path -LiteralPath $requirementsLock -PathType Leaf) -or
    -not (Test-Path -LiteralPath $dockerfile -PathType Leaf)) {
    throw "LiteRT conversion tooling is incomplete under scripts/litert"
}

& $Python -c @'
import importlib.metadata as metadata
import re
import sys
from pathlib import Path

canonical = lambda value: re.sub(r"[-_.]+", "-", value).lower()
required = {}
for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith(("#", "--")):
        continue
    if "==" not in line:
        raise SystemExit(f"Unsupported unpinned LiteRT requirement: {line}")
    name, version = line.split("==", 1)
    required[canonical(name)] = version

installed = {
    canonical(distribution.metadata["Name"]): distribution.version
    for distribution in metadata.distributions()
    if distribution.metadata["Name"]
}
problems = [
    f"{name}=={expected} (found {installed.get(name, 'missing')})"
    for name, expected in required.items()
    if installed.get(name) != expected
]
raise SystemExit("Pinned LiteRT conversion dependencies are unavailable: " + ", ".join(problems) if problems else 0)
'@ $requirementsLock
if ($LASTEXITCODE -ne 0) {
    throw "Install the exact dependencies from scripts/litert/requirements.lock, then rerun setup"
}

New-Item -ItemType Directory -Force -Path $workDirectory | Out-Null
foreach ($model in $missingModels) {
    $sourcePath = Join-Path $repoRoot $model.Source
    $outputPath = Join-Path $repoRoot $model.Output
    $temporaryOutput = Join-Path $workDirectory ([IO.Path]::GetFileName($model.Output) + ".pending")
    $temporaryManifest = Join-Path $workDirectory ([IO.Path]::GetFileNameWithoutExtension($model.Output) + ".manifest.json")
    Remove-Item -LiteralPath $temporaryOutput -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $temporaryManifest -Force -ErrorAction SilentlyContinue

    $arguments = @(
        $converter,
        $sourcePath,
        $temporaryOutput,
        "--source-sha256", $model.SourceSha256.ToLowerInvariant(),
        "--manifest", $temporaryManifest,
        "--requirements-lock", $requirementsLock,
        "--dockerfile", $dockerfile
    )
    if ($null -ne $model.InputShape) {
        $arguments += @("--input-shape", $model.InputShape)
    }
    & $Python @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$($Model.Name) LiteRT conversion failed"
    }

    Assert-FileHash -Path $temporaryOutput -ExpectedSha256 $model.OutputSha256 -Description "$($model.Name) converted output"
    if ((Get-Item -LiteralPath $temporaryOutput).Length -ne $model.OutputSize) {
        throw "$($model.Name) converted output size mismatch"
    }
    $generatedManifest = Get-Content -LiteralPath $temporaryManifest -Raw | ConvertFrom-Json
    if ($generatedManifest.source.sha256.ToUpperInvariant() -ne $model.SourceSha256 -or
        $generatedManifest.output.sha256.ToUpperInvariant() -ne $model.OutputSha256 -or
        [long]$generatedManifest.output.fileSizeBytes -ne $model.OutputSize) {
        throw "$($model.Name) generated manifest does not match the normalized manifest contract"
    }

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null
    Move-Item -LiteralPath $temporaryOutput -Destination $outputPath
    Write-Host "Prepared and verified $($model.Output)"
}
