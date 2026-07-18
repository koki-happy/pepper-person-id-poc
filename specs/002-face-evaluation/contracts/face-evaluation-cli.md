# Face Evaluation CLI Contract

## Dataset validation

`evaluate.py validate --dataset <biwi|pointing04> --root <path> --output <manifest.json>`

Returns zero only when provenance metadata, files, poses, subjects, series, and leakage rules validate.

## Evaluation

`evaluate.py run --manifest <manifest.json> --model <sface|0095> --protocol <front|multi> --output <dir>`

Outputs `trials.csv`, `summary.json`, and `report.md`. Repeating with unchanged inputs must reproduce
decisions and metric counts.

## Pepper collection

`collect-pepper-face-performance.ps1 -Serial <adb-serial> -DurationMinutes <n> -Scenario <name>`

Outputs raw app events, periodic CPU/memory samples, device facts, settings, timestamps, and completion
status without modifying registrations.

## Pepper summary

`summarize-pepper-face-performance.ps1 -RunDirectory <path>`

Outputs machine-readable and Markdown reports. Missing measurement boundaries are listed as unavailable.
