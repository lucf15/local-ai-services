# Initial capability evaluation

## Scope and limits

The 24 synthetic fixtures are an initial decision set: 3 capabilities x 2 languages
x 4 cases. They include factual content, longer input, negation/uncertainty,
already-correct text and quoted instructions. They are not a representative
language benchmark or a release certification. Their longer examples are still
short compared with long articles. Add token-bounded long-document, context-limit,
unicode, table and mixed-language cases before advertising those input types.

Human review instructions are held out of model requests. They describe properties
to assess, not an exact expected answer for open-ended transformations. Preserve
the dataset version and content hash. Add a separate held-out set before selecting
a production configuration; do not repeatedly tune prompts against these cases
and then claim independent validation on the same set.

## Run preparation

`tools/evaluate.py prepare` snapshots the catalog model, capability definitions and
corpus into a new run directory. A run compares one exact artifact and one backend.
The directory must not already exist. Each case starts with `status: pending`.

Before execution, fill `environment` in run.json with the actual Android device,
Android version, runtime version, app/build identity and effective runtime settings.
Record context and output limits, seed/sampling settings, thinking behavior and
any fallback actually used. A requested GPU run that executes on CPU is a separate
configuration, not a GPU measurement. The model catalog does not select these
values for the evaluator.

The Python tool only prepares and inspects records. Opt-in Android probes execute models and save compatible development records. Its requested
backend is recorded without an application-level fallback; internal accelerator
delegation is not independently traced. Normal completion is recorded with an
unknown stop reason and no inferred token counts. A source/configuration fingerprint
identifies the app build. Android exports currently contain process-PSS samples,
not peak-memory instrumentation or repeated performance distributions.
Do not populate results using an unrelated cloud model or a mock engine.

## Result rows

Each result row contains:

- `case_id`: references the frozen cases.json in this run.
- `status`: pending, success, error or cancelled.
- `output`: generated final text, or null before execution.
- `finish_reason`: stop, token_limit, unknown, error, cancelled or null. The Android
  LiteRT-LM 0.19.1 runner records `unknown` on normal callback completion because
  that callback does not distinguish EOS from the output limit. Do not interpret
  `unknown` as proof that output was not truncated.
- `error`: a typed error description, or null.
- `metrics`: measurement metadata, initially null. Record actual numbers with
  their units and scope. A single run is not a distribution or p95 estimate.
- `review`: manual rubric scores and critical-error labels, initially null.

For reviewed successful rows, `review` contains `scores` with each rubric dimension
rated 0-4, `critical_errors` as a list of observed labels from the capability, and
`notes` with reasons. Scores: 0 unusable, 1 major defects, 2 substantial editing
needed, 3 minor issues, 4 meets the task. A valid output format alone is not
semantic correctness. Detect unsupported facts, reversed negations, invented
commitments and unnecessary rewriting through human review.

`must_preserve` values aid review; they are not universal exact-match acceptance
rules. A summary may legitimately express a date differently. Likewise
`expected_unchanged` expresses the minimal-correction behavior, not a substitute
for checking meaning in cases with actual edits.

## Request construction

The future runner receives capability instruction, language, requested variant and
source text as distinct fields. Source text remains untrusted task data. Preserve
those boundaries in the actual model chat template. Do not send review fields.
Use the same pipeline and settings for comparative trials, changing only the
model-specific formatting required for correct execution. Log effective settings.

## Performance measurement

Use real Android hardware and optimized builds. The first phase is model/task
evaluation, before adding IPC. Record cold model loading separately from warm
inference; distinguish cold caches and first-install costs from warm runs.

Recommended measurement fields (not values inferred from another device):

- `queue_wait_ms`, `model_load_ms`, `time_to_first_token_ms`, `generation_ms`;
- actual input/output token counts and the measurement boundaries;
- process memory with its measurement method and scope, including whether GPU
  allocations are visible;
- cancellation-to-idle time, thermal state, and backend actually used.

Use repeated, interleaved configurations on the same device for distributions.
Do not compare medians from unmatched workloads or compute p95 from a single run.
Later measure direct inference against Binder/SDK with identical workloads and
report UI frame behavior independently from model throughput.

## Selection criteria

Report results per capability and language. Keep quoted-instruction cases visible
as a separate group. Critical semantic errors cannot be hidden by an average score.
Keep pending, failed, cancelled and token-limited cases in coverage reporting.

The selected candidate must meet agreed quality and resource budgets on the target
configuration. Do not assign a default from model size, upstream task labels or
an incomplete run. This initial evaluator does not automatically approve a model
or advertise a supported capability.
