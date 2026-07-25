# Research Decisions

- Use `AtomicFile` binary snapshots for API 23 compatible replacement writes.
- Treat unknown/corrupt schemas as empty because retention is session-only.
- Share repositories through a custom `Application` so Activity recreation preserves clusters.
- Gate UI with startup state so coordinators cannot start before deletion finishes.
