# Jolt JSON transformation walkthrough

[01 · Reshaping JSON can discard information](01_audit_json_cardinality_changes.ipynb)
asks whether a ticket-label normalization retains known strings and their multiplicity.
Ten **synthetic** payloads include duplicate strings, a mixed list, an empty list,
explicit null, missing fields, a number, malformed JSON and SQL NULL. All analytical
transformations use the real released Jolt worker through visible SQL.

The walkthrough validates the specification and payloads, inspects the independent
`shift` and `default` stages, compares their composition with the full chain, then
counts **string occurrences** separately from **distinct ticket–label pairs**.
The default `LABEL_CARDINALITY="MANY"` retains eight known string occurrences;
`"ONE"` retains four. Selecting the first element is neither deduplication nor a rule
for choosing the most important label. Structural normalization is not business-label
validation, and the original field-state diagnostics remain available under either policy.

## Run locally or in Colab

Requirements: [uv](https://docs.astral.sh/uv/), macOS or glibc Linux on x86_64/arm64,
and network access for the first shared-package, Python, VGI extension, managed Java
and JAR downloads. A system Java installation, Gradle and the worker source checkout
are unnecessary. POSIX cache locking makes this launcher unsuitable for native Windows.

The complete notebook runs independently of the repository. From `notebooks/`:

```sh
uv sync --locked
uv run --locked vgi-notebooks 01_audit_json_cardinality_changes.ipynb
```

For an editor, select `notebooks/.venv/bin/python`, use `notebooks/` as the working
directory, then restart/run all. The notebook's Colab badge opens the same source.
Its visible bootstrap installs only `vgi-example-notebooks` base dependencies;
the local locked environment adds `[runner]`. The shared package supplies Haybarn
2.0.0rc11, JupySQL, presentation and connection setup. The SQL connection uses an
empty temporary secret directory with persistent secrets disabled.

The shared `jolt` registry downloads the immutable launcher and its lock; even a
notebook-only copy can run without pre-copied helper files. The notebook contains:

```sql
ATTACH 'jolt' (TYPE vgi,
 LOCATION 'uv run --locked --no-project --python 3.13 jolt_release.py v0.4.0');
```

[`jolt_release.py`](jolt_release.py) accepts the reviewed release tag. Its manifest
pins the public [v0.4.0 JAR](https://github.com/Query-farm/vgi-jolt/releases/tag/v0.4.0)
to SHA256 `6062855f47a80bf6207773afac5d99aca624da14d838332c31ba99803a101b31`.
A separate PEP 723 environment, locked in [jolt_release.py.lock](jolt_release.py.lock),
supplies `jdk4py==21.0.8.2`. The launcher checks the actual **Java 21.0.8** binary,
never uses system Java, rechecks cached JAR bytes, and uses a bounded lock plus atomic
download publication. Neither Java nor the worker libraries enter the notebook kernel.
Review and retest the helper manifest, registry and release pin together when upgrading.

## Focused experiment

Predict which label occurrences disappear and how the repeated `bug` label changes,
then change only the cardinality policy. Preserve the successful default:

```sh
uv run --locked vgi-notebooks 01_audit_json_cardinality_changes.ipynb \
  --parameters '{"LABEL_CARDINALITY":"ONE"}' \
  --output-dir validation/one
```

Compare both charts, the transformed field-state table and the intermediate-stage table.
The input rows and pre-cardinality stages stay fixed. The count of eight string
occurrences falls to four, while unavailable measurements remain NULL and the empty
array's known count remains zero. MANY maps explicit null to an empty list; ONE maps
an empty list to null. Keep the original diagnostics rather than treating these output
states as equivalent. Reset to `"MANY"` for the saved default.

The runner uses a fresh kernel and only replaces a notebook after full success. It
rejects unknown parameters and requires a separate destination for overrides. Published
charts are linked SVGs under `charts/`; ignored PNG previews and execution diagnostics
are under `artifacts/` and `validation/`. Experiment copies include their presentation
companions and documentation.

## Coverage and validation

[coverage.json](coverage.json) maps all **five of five** distinct public SQL names to
executed SQL, outputs, narrative and editorial findings. Complete native function,
table and view exports are in [coverage/](coverage/), including the empty table/view
exports. There are no advertised COPY-handler names or capability exclusions.

The actual public **v0.4.0** release and current public-main baseline
`5ccf38eb7f521987e1851824dceaf44af14f2966` expose the same names. The current catalog
was captured from its existing built JAR without rebuilding. Source review finds only
metadata/composed-example changes in the runtime Java files since the release; the
transformation and validation implementations are unchanged. Hash-bound notebook
receipts describe execution against the release: current-interface coverage is reviewed
compatibility, not an assertion that the notebook ran the current build.

Validation includes the saved default, a notebook-only ONE experiment with cold UV/JAR
caches, twelve SQL invariants, three runner guards and two invalid-parameter guards.
A fresh Python 3.12 kernel with older preloaded pandas/Matplotlib/IPython/ipykernel/
jupyter-client verifies bootstrap compatibility and full execution without upgrading
those loaded packages or installing runner/Java dependencies into the kernel. This is
an automated preloaded-kernel compatibility check; hosted Colab was not manually run.
The launcher also passed actual SQL with missing system Java and fake Java 11.

For this notebook-only change, existing green [source CI at the baseline](https://github.com/Query-farm/vgi-jolt/actions/runs/43570103441)
is reused; no worker rebuild or source test repetition is required. The worker is MIT
licensed and uses Jolt 0.1.8 (Apache-2.0). No external service requests, credentials,
real ticket data or account mutations are part of the analysis.
