# Data Model

- `AnonymousCluster`: anonymousId, modelId, embeddingDimension, normalizedEmbeddingSum, centroid, updateCount, createdAtMillis, updatedAtMillis.
- `RepositorySnapshot`: schemaVersion, nextId, clusters.
- `SessionStartupState`: INITIALIZING, READY, ERROR.
- `DeviceLoadSnapshot`: CPU state, app PSS, total and available memory, low-memory flag, timestamp.
