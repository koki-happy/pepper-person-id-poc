# Contract: Persistence Policy

## Input

- `IdentificationEvaluation`
- modality quality assessment
- current cluster update count
- model/runtime execution status

## Output

```text
PersistencePolicyResult
  createEligible
  updateEligible
  reasons[]

PersistenceOperation = CREATE | UPDATE | HOLD
```

## Rules

| Evaluation | Quality | Operation |
|---|---|---|
| MATCHED_EXISTING | update eligible | UPDATE |
| no match / CREATED_NEW | create eligible | CREATE |
| AMBIGUOUS | any | HOLD |
| any | low quality | HOLD |
| any | overlapped/multiple speakers | HOLD |
| any | unsupported input/runtime error | HOLD |

- Update thresholds MUST be at least as strict as create thresholds.
- HOLD MUST leave next ID, cluster count, centroid, sums, update count, and caches unchanged.
- CREATE/UPDATE operate only on the current modality and model space.
- No operation serializes biometric state.
