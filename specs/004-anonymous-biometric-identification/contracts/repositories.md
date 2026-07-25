# Internal Repository Contract

- `getAll()` returns defensive copies.
- `identify(modelId, embedding, threshold, maxUpdateCount)` atomically matches or creates a cluster.
- `count()` returns all clusters for the modality.
- `deleteAll()` removes the file and resets the next ID to 1.
- Invalid input throws without changing persisted state.
- Matching and centroid accumulation use the shared domain `AnonymousClusterEngine`.
- Repositories keep an in-process snapshot; reads do not reopen the file and unchanged frozen clusters are not saved.
- New-cluster results expose the best existing score or no-comparison state and never add the new cluster as a synthetic 1.0 candidate.
- Loaded snapshots reject inconsistent centroids, zero sums, duplicate IDs, invalid dimensions, and non-finite values.
