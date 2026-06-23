# vgi-jolt

A [VGI](https://query.farm) worker that performs **declarative JSON→JSON
structural transformation** via [Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt),
exposed as DuckDB SQL functions.

Jolt applies a **spec** — a JSON document describing a sequence of transformation
operations — to an input JSON document. It is the standard tool for reshaping JSON:
renaming/moving keys, supplying defaults, removing fields, and normalizing
cardinality, all declaratively (no code).

```sql
ATTACH 'jolt' (TYPE vgi, LOCATION 'java -jar vgi-jolt-all.jar');

-- Remap a nested value to a top-level key with a full Jolt spec (a "chainr").
SELECT jolt.jolt_transform(
  '{"rating":{"primary":{"value":3}}}',
  '[{"operation":"shift","spec":{"rating":{"primary":{"value":"Rating"}}}}]');
-- -> {"Rating":3}
```

## Input

Every function accepts JSON as either:

- a **VARCHAR** carrying the JSON text directly (this is *not* a file path), or
- a **BLOB** carrying the JSON bytes (decoded as UTF-8).

`NULL` input row → `NULL` output. Malformed input JSON, or a malformed/invalid
spec, raises a **clear DuckDB error** from the transform functions; the validator
functions (`is_valid_jolt_spec`, `json_valid`) instead return `false` and never
error. All logging goes to stderr so the Arrow stdout stream stays clean.

## Functions

| Function | Returns | Description |
|---|---|---|
| `jolt_transform(input_json, spec_json)` | `VARCHAR` | Apply a **full Jolt spec** (a JSON array of operations, a "chainr") to `input_json`; returns the transformed JSON. The headline. |
| `jolt_shift(input_json, shift_spec)` | `VARCHAR` | Convenience: apply a single `shift` operation. `shift_spec` is the bare shift spec object. |
| `jolt_default(input_json, default_spec)` | `VARCHAR` | Convenience: apply a single `default` operation. `default_spec` is the bare default spec object. |
| `is_valid_jolt_spec(spec_json)` | `BOOLEAN` | Does the spec parse and compile as a Jolt chainr? `NULL` → `NULL`, invalid → `false`. |
| `json_valid(input_json)` | `BOOLEAN` | Is the input well-formed JSON? A handy companion. `NULL` → `NULL`, invalid → `false`. |

The `spec` argument is treated as a **query constant**: it is parsed and compiled
once, then reused across every input row in the batch.

## Jolt operations

A Jolt spec is a JSON array of operations applied in sequence. Each entry has an
`"operation"` name and a `"spec"`:

| Operation | What it does |
|---|---|
| `shift` | Copy/move values from the input tree to the output tree by matching paths (the most common operation). |
| `default` | Supply default values for keys absent from the input. |
| `remove` | Delete keys from the input. |
| `cardinality` | Coerce between single values and lists (`ONE` / `MANY`). |
| `sort` | Recursively sort map keys alphabetically (debugging aid). |
| `modify-overwrite-beta` / `modify-default-beta` / `modify-define-beta` | Compute/derive values via Jolt's function library. |

See the [Jolt documentation](https://github.com/bazaarvoice/jolt#jolt) and the
[live spec playground](https://jolt-demo.appspot.com/) for the full operation set
and spec syntax.

```sql
-- Full chainr: shift, then default, then remove.
SELECT jolt.jolt_transform(
  '{"rating":{"primary":{"value":4}},"id":"abc"}',
  '[{"operation":"shift","spec":{"rating":{"primary":{"value":"Rating"}},"id":"ProductId"}},
    {"operation":"default","spec":{"Reviewed":true}},
    {"operation":"remove","spec":{"ProductId":""}}]');
-- -> {"Rating":4,"Reviewed":true}
```

Because Jolt does not guarantee key order in its serialized output, compare results
semantically (e.g. `json_extract` per field) rather than by raw string equality.

## Build & test

```sh
./gradlew test     # JUnit: JoltEngine + scalar compute() coverage
make test-sql      # fat JAR + haybarn-unittest E2E over test/sql/*
make test          # both
make build         # fat JAR -> build/libs/vgi-jolt-<ver>-all.jar
```

The SQL E2E runner is `haybarn-unittest` (`uv tool install haybarn-unittest`, then
`export PATH="$HOME/.local/bin:$PATH"`).

The VGI Java SDK (`farm.query:vgi`, `farm.query:vgirpc`) and Jolt
(`com.bazaarvoice.jolt:jolt-core`, `:json-utils`) all resolve from **Maven
Central**, so the build is fully self-contained — no sibling checkout, no
`mavenLocal`.

## License

This worker is MIT (see `LICENSE`). It depends on **Bazaarvoice Jolt**
(`jolt-core`, `json-utils`), which is licensed under the **Apache License 2.0** — a
permissive license compatible with commercial and MIT-licensed use. Jolt pulls in
Jackson (also Apache-2.0) transitively for JSON (de)serialization.
