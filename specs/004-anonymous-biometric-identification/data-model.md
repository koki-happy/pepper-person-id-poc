# Data Model

- `AnonymousCluster`: anonymousId, modelId, embeddingDimension, normalizedEmbeddingSum, centroid, updateCount, createdAtMillis, updatedAtMillis.
- `RepositorySnapshot`: schemaVersion, nextId, clusters.
- `AnonymousIdentificationResult`: anonymousId, modelId, bestExistingScore (nullable), threshold, isNewCluster, update counts, current-model cluster count, total cluster count, existing candidate scores.
- `SessionStartupState`: INITIALIZING, READY, ERROR.
- `DeviceLoadSnapshot`: CPU state, app PSS, total and available memory, low-memory flag, timestamp.
