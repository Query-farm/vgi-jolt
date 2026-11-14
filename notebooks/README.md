# Jolt notebooks

[01 · Reshaping JSON can discard information](01_audit_json_cardinality_changes.ipynb)
uses ten synthetic ticket payloads to demonstrate a Jolt `shift` → `default` →
`cardinality` chain through SQL. It guards malformed JSON, preserves original
null/missing/type diagnostics, audits string multiplicity, and charts label
occurrences separately from distinct ticket–label pairs.

With the default `LABEL_CARDINALITY="MANY"`, all eight known string occurrences
survive. Changing only the parameter to `"ONE"` retains four: selecting the first
array element discards information. Duplicate strings remain meaningful occurrences;
neither policy validates a business label taxonomy. The sample is entirely synthetic
and makes no API calls or writes to an external service.

## Run

Requirements: [uv](https://docs.astral.sh/uv/), Java 21 or newer on `PATH`,
macOS or Linux, and network access for the initial Python environment, VGI extension
and worker JAR download. The cache helper uses POSIX file locks. On macOS with
Homebrew Java, put `/opt/homebrew/opt/openjdk@21/bin` on `PATH` if needed.

From this repository:

```sh
uv sync --project notebooks --frozen
uv run --project notebooks --frozen python notebooks/execute.py
```

The complete `notebooks/` directory also runs independently of the worker checkout:

```sh
cd notebooks
uv sync --frozen
uv run --frozen python execute.py
```

For an editor, select `notebooks/.venv/bin/python`, use `notebooks/` as the kernel's
working directory, and restart/run all. Connection SQL and the analytical parameter
are visible; imports and plotting helpers are folded. The saved notebook contains
the executed default results. Charts use Seaborn styling and Matplotlib.

Run the one-parameter experiment without replacing the saved default:

```sh
uv run --project notebooks --frozen python notebooks/execute.py \
  --parameters '{"LABEL_CARDINALITY":"ONE"}' \
  --output-dir notebooks/validation/one
```

The runner starts a fresh kernel and only replaces a notebook after every cell
finishes successfully. It rejects unknown parameters and requires a separate output
directory for overrides. Ignored chart review files are saved under
`notebooks/validation/charts/`; charts are also embedded in the notebook.

## Portable worker connection

The notebook contains this literal location:

```sql
ATTACH 'jolt' (TYPE vgi,
 LOCATION 'uv run --frozen --project . python release_jar.py Query-farm/vgi-jolt v0.4.0 vgi-jolt-v0.4.0.jar 6062855f47a80bf6207773afac5d99aca624da14d838332c31ba99803a101b31');
```

[`release_jar.py`](release_jar.py) downloads the public
[v0.4.0 release JAR](https://github.com/Query-farm/vgi-jolt/releases/tag/v0.4.0),
verifies the exact SHA-256, and starts Java. It checks cached bytes on each launch,
uses a bounded lock to serialize downloads, and publishes completed files atomically.
The cache is `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`. No local worker binary,
Gradle installation, credentials, or worker source build is required. The separately
locked notebook environment uses Haybarn 2.0.0rc11; repository Java dependencies
remain unchanged.

## Interpretation and validation

The notebook retains all ten input row IDs, including malformed JSON and SQL NULL.
Unavailable or wrong-type fields have NULL comparison counts; a genuine empty array
has a known zero. MANY maps explicit JSON null to an empty list, while ONE maps an
empty list to null. Missing fields remain absent. The original diagnostics therefore
remain necessary even when the normalized output looks uniform.

SQL checks cover the chain, row identity, guarded transformation, source IDs,
explicit default policy, sub-multiset preservation, count reconciliation, and missing
fields. Comparisons extract JSON values; output object key order is not significant.
The review state is an explicit example policy, not an inferred source fact.

Validation used the actual released worker, including a detached notebook directory
and the ONE experiment. Repository checks passed: 18 JUnit tests, 49 stdio / 51 HTTP /
49 Unix SQL assertions, and executed metadata lint at 100/100 L2 with zero findings.
Those source checks build the worker for testing; notebook execution uses the release
JAR. The worker is MIT licensed and uses Jolt 0.1.8 (Apache-2.0).

Brand assets are the unmodified Query.Farm Strata Sun wordmark and VGI logo. Visit
[Query.Farm](https://query.farm/) and [VGI](https://query.farm/vgi/).
