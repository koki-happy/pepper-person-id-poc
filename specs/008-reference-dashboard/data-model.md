# Data model: dashboard display state

The dashboard state is ephemeral Compose state. It is not a persistence contract.

```text
DashboardRow
  elapsed: String              # mm:ss.d, same session clock for all panels
  id: String                   # FPnnn or SPnnn, or —
  similarity: Float?           # null renders —
  processingMillis: Long?      # total pipeline time only
  utteranceSeconds: Float?     # speaker table only
  relation: Relation?          # only current session, not persisted

Relation
  faceId: String?
  speakerId: String?
  overlapRatio: Float?
  status: MATCHED | NO_MATCH | MULTIPLE_SPEAKERS | UNAVAILABLE

DashboardMetric
  group: DEVICE | FACE | SPEAKER
  label: String
  unit: PERCENT | MB | FPS | MS
  average: Double?

DashboardEvent
  elapsed: String
  kind: FACE | VOICE | SYSTEM
  text: String                    # user-visible event log row
```

`DashboardRow` and `DashboardEvent` are retained only while the route is alive and are
bounded by the visible panel. Reset clears them. The anonymous repositories remain the
existing session store for current identity matching; no dashboard relation is persisted.
