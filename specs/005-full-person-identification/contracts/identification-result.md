# Contract: Identification Result

## Input

- modality
- normalized finite embedding
- modelSpaceId
- embeddingDimension
- compatible session clusters
- threshold
- minimumLead

## Output

```text
IdentificationEvaluation
  candidates[]:
    rank
    anonymousId
    score
    modelSpaceId
    embeddingDimension
    updateCount
    selected
  highestScore?
  secondHighestScore?
  highestCandidateLead?
  threshold
  minimumLead
  decision
```

## Invariants

- Compatible means same modality, modelSpaceId, and embeddingDimension.
- Every compatible existing cluster appears exactly once.
- Sort by score descending, then anonymousId ascending.
- Candidate list is not truncated.
- Non-finite input returns no mutation request.
- One candidate produces `secondHighestScore=null`, `highestCandidateLead=null`; lead condition passes.
- Evaluation never mutates a repository.
