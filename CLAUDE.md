# CLAUDE.md — vgi-jolt

Contributor/agent notes. User-facing docs live in `README.md`; this is the
"how it's built and where the sharp edges are" companion.

## What this is

A [VGI](https://query.farm) worker (Java) that performs **declarative JSON→JSON
structural transformation** via [Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt),
exposed as DuckDB **scalar** functions. Modeled on the sibling `vgi-hl7` /
`vgi-tika` / `vgi-grammar` workers; built with Gradle (Kotlin DSL, JDK 21) into a
shaded fat JAR. Catalog name `jolt` (single `main` schema). There are **no table
functions** — every function is a scalar, so this worker does not use a
`TableInitParams` test driver.

## Licensing — Jolt is Apache-2.0

The worker itself is **MIT** (see `LICENSE`). Runtime dependencies:

- VGI SDK: `farm.query:vgi:0.5.0` (+ `farm.query:vgirpc:0.10.2`, declared
  explicitly since the code imports `farm.query.vgirpc.*`).
- **Bazaarvoice Jolt**: `com.bazaarvoice.jolt:jolt-core:0.1.8` +
  `:json-utils:0.1.8` — **Apache License 2.0**, a permissive license compatible
  with commercial/MIT use. `jolt-core` declares `json-utils` as `test` scope in
  its POM, so `json-utils` is declared explicitly here; it pulls
  `jackson-databind` (Apache-2.0, compile scope) transitively for JSON I/O.
- `slf4j-simple` (stderr logging).

## Jolt model

A Jolt **spec** is a JSON array of operations (a "chainr") applied in order. Each
entry is `{"operation": <name>, "spec": <op-spec>}`:

- `shift` — path-based copy/move of input values into an output tree (most common).
- `default` — supply values for keys absent from the input.
- `remove` — delete keys.
- `cardinality` — coerce single↔list.
- `sort` — recursively sort map keys (debug aid).
- `modify-overwrite-beta` / `modify-default-beta` / `modify-define-beta` — derive
  values via Jolt's function library.

`JoltEngine` is the single wrapper over Jolt: `transform` compiles a full spec via
`Chainr.fromSpec`; `shift` / `applyDefault` use the single-operation `Shiftr` /
`Defaultr` directly; `isValidSpec` / `isValidJson` swallow errors and return a
boolean. JSON I/O is `JsonUtils.jsonToObject` / `toJsonString` from json-utils.

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   Gradle, shadow plugin (com.gradleup.shadow 9.4.2)
src/main/java/farm/query/vgi/jolt/
  Main.java                  Worker.builder().catalogName("jolt")...registerScalar x5
  JoltEngine.java            the Jolt wrapper (transform / shift / default / validators)
  JsonInput.java             VARCHAR-text-or-BLOB-bytes input dispatch (JSON inline, not a path)
  TransformFunction.java     scalar: jolt_transform(input_json, spec_json) — full chainr
  ShiftFunction.java         scalar: jolt_shift(input_json, shift_spec)
  DefaultFunction.java       scalar: jolt_default(input_json, default_spec)
  IsValidSpecFunction.java   scalar: is_valid_jolt_spec(spec_json) -> BOOLEAN
  JsonValidFunction.java     scalar: json_valid(input_json) -> BOOLEAN
src/test/java/...            JUnit: JoltEngineTest (semantic JSON tree compare via Jackson),
                             ScalarFunctionsTest (drives each ScalarFn.compute directly)
test/sql/scalars.test        haybarn-unittest E2E (inline JSON literals)
Makefile                     build / test-unit / test-sql / test / clean
```

## Sharp edges

1. **The spec is a `@Const`, the input is a `@Vector`.** `jolt_transform` /
   `jolt_shift` / `jolt_default` take the document column as the data vector and
   the spec as a constant string, so the `Chainr` / `Shiftr` / `Defaultr` is
   compiled **once per batch** and reused across rows (matching the `correct(text,
   language)` pattern in vgi-grammar).
2. **Jolt does not guarantee output key order.** Tests compare JSON
   *semantically*: JUnit parses both sides with Jackson and compares trees; the
   `.test` file asserts per-field via `json_extract` rather than raw string match.
3. **`LOAD json;` is explicit in the `.test` file.** haybarn does not autoload the
   json extension, and the assertions use `json_extract` / `json_extract_string`.
4. **NULL → NULL; malformed → error or false.** NULL input row yields a NULL
   output cell. Malformed input JSON / malformed or invalid spec → a clear
   `JoltException` (DuckDB error) from the transform scalars; the validators
   (`is_valid_jolt_spec`, `json_valid`) return `false` instead and never throw.
5. **`Add-Opens: java.base/java.nio`** is baked into the fat-JAR manifest (Arrow
   needs it); the input/spec args are `any`-typed so a VARCHAR binds from SQL.
6. **`haybarn-unittest` skips `require vgi`** — the `.test` file uses explicit
   `LOAD vgi;`, `require-env VGI_JOLT_WORKER`, and `# group: [vgi_jolt]`.

## SDK dependency & CI (self-contained via Maven Central)

Everything resolves from **Maven Central** — the VGI SDK and Jolt — so the build
is fully self-contained: no sibling checkout, no `mavenLocal`, no composite build.
`.github/workflows/test.yml` is a single `build-and-test` job that resolves from
Central and runs JUnit + shadowJar + an HTTP boot smoke test + `make test-sql`.

## Testing

```sh
./gradlew test     # JUnit: JoltEngine + scalar compute() coverage
make test-sql      # shadowJar + haybarn-unittest over test/sql/*
make test          # both
```
