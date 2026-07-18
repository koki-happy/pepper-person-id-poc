# Face UI Contract

## Registration

Inputs: person ID, display name, start registration, cancel, delete all.

Visible state: face count/status, model readiness, current yaw/pitch/roll, target pose, hold progress,
completed poses, registration message, stored people/sample count, and errors.

Events: start creates a front target; eligible frames update progress; a successful target requests one
embedding; persisted success advances to left/right or completes. No identity comparison is performed.

## Identification

Input: current camera frame; no identification button is required.

Visible state: registered person count, model readiness, a name or Unknown overlay for every current
face track, score, second score, margin, face count, and errors.

Event: each configured analysis interval consumes all visible face tracks, extracts their embeddings,
and replaces the current result set. Leaving faces are cleared. Only enrolled-person comparison occurs;
anonymous IDs and clusters are never generated.

## Deletion

Input: confirm delete all.

Output: all face and speaker embeddings currently stored in the shared person repository are removed;
the UI states the full scope before confirmation and reports success or failure.
