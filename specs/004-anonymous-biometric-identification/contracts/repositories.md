# Internal Repository Contract

- `getAll()` returns defensive copies.
- `identify(modelId, embedding, threshold, maxUpdateCount)` atomically matches or creates a cluster.
- `count()` returns all clusters for the modality.
- `deleteAll()` removes the file and resets the next ID to 1.
- Invalid input throws without changing persisted state.
