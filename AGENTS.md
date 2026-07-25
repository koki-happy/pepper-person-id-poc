# Agent workflow

## GitHub Spec Kit

Use the existing `.specify/`, `.agents/skills/speckit-*`, and `specs/`
artifacts for feature development. Before implementation, ensure the active
feature contains:

- detailed requirements and acceptance criteria in `spec.md`
- the design and implementation approach in `plan.md`
- data models in `data-model.md` when persistent or structured data changes
- external and internal contracts under `contracts/` when interfaces change
- dependency-ordered implementation work in `tasks.md`

Do not overwrite unrelated existing Spec Kit artifacts. Keep requirements,
design decisions, acceptance evidence, and implementation status distinct.

## OpenWiki

Use OpenWiki for concise architecture documentation of already merged code.
The baseline is `git HEAD`; uncommitted working-tree changes must be identified
separately and must not be described as merged behavior.

Generated documentation belongs under `openwiki/`. It should explain high-level
structure and execution flow, then link to authoritative code. Prioritize the
Android app, face tracking and 1:N identification, selectable inference
backends, explicit registration, session-only anonymous learning, settings,
model assets, build and test paths, and Pepper validation boundaries.

After relevant changes are merged, refresh with:

```powershell
openwiki --update
```

## LeanCTX

Use LeanCTX MCP tools for agent reads, searches, and large command output.
Prefer `ctx_read`, `ctx_search`, and `ctx_shell` where available. Keep active
code, build output, device evidence, and Spec Kit artifacts as the source of
truth even when compressed context is used.
