# Data Model: Guided Face Registration and Real-time Enrolled-person Identification

## HeadPose

- `yawDegrees: Float`
- `pitchDegrees: Float`
- `rollDegrees: Float`
- All values must be finite.

## FacePoseTarget

- Values: `FRONT`, `LEFT`, `RIGHT`
- Order: front -> left -> right.
- Front accepts `abs(yaw) <= frontYaw` and `abs(pitch) <= frontPitch`.
- Left accepts positive yaw and right accepts negative yaw within the configured absolute range, plus
  the front pitch tolerance. This sign convention was validated on the Nothing Phone (3a) front camera
  after rotating the analysis image into display orientation.

## PoseStabilityState

- `target`: current pose target
- `enteredAtMillis`: first instant continuously inside target, or absent
- `progressMillis`: current continuous duration capped at stable duration
- `completed`: ordered set of completed targets
- Transition out of range resets `enteredAtMillis` and progress.
- Reaching stable duration produces exactly one capture request and advances the target.

## RegistrationSession

- `personId`, `displayName`
- `target`, `completedTargets`, `capturingTarget`
- Starts only with valid non-blank identity fields.
- Completes only after three successful embedding writes.
- Zero/multiple faces and track changes reset the active hold.

## IdentificationFrame

- Contains every detector track ID visible at the configured identification interval.
- Each track advances independently to embedding and enrolled-profile comparison.
- Results replace the prior frame's result set; zero faces clears stale results.
- Multiple faces are supported. No anonymous ID or anonymous cluster is persisted.

## FaceIdentityResult

- `status`: `IDENTIFIED` or `UNKNOWN`
- `bestScore`, `secondScore`, `margin`
- `personId` and `displayName` only for `IDENTIFIED`
- `threshold`, `minimumMargin`, `processingTimeMillis`
- Acceptance requires threshold and, when present, second-candidate margin.

## FaceSettings

- Threshold: 0..1
- Minimum margin: 0..2
- Registration interval: 100..2,000 ms, default 200 ms
- Identification interval: 200..5,000 ms, default 1,000 ms
- Stable duration: 250..5,000 ms, default 1,000 ms
- Front yaw/pitch: 1..30 degrees, defaults 8/8
- Side minimum/maximum: 5..60 degrees with minimum < maximum, defaults 18/32
- Smoothing samples: 1..15, default 5

## Registration persistence rule

Front, left, and right embeddings stay in memory until the session completes. Completion atomically
replaces the selected model's prior three samples. Cancel, navigation away, extraction failure, or an
incomplete session writes no partial samples and leaves an existing completed profile unchanged.
