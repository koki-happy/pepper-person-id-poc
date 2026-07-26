# Contract: Model Catalog and Runtime Compatibility

## Catalog source

`config/models.json` schema v2 is the single machine-readable source for model artifacts, model spaces, runtime records, conversion records, compatibility, hashes, and licensing.

## Required validation

- IDs are unique and nonblank.
- Referenced modelSpaceId, sourceArtifactId, and runtimeId exist.
- Bundled/downloaded files match filename, size, and SHA-256.
- Tensor metadata and preprocessing are complete.
- Converted artifacts include source and conversion provenance.
- `VERIFIED` compatibility includes evidence for the exact artifact hash, runtime version, ABI, and API.
- Settings expose only `VERIFIED` or `BUILDABLE` pairs.
- Runtime failure never selects another pair automatically.

## Release/candidate gate

- Every included model and runtime has license evidence.
- Commercial-use status is `ALLOWED`.
- Candidate contents exactly match the selected allowlist.
- Gate applies to every candidate variant, including debug candidate APKs used for acceptance.
