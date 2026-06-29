package farm.query.vgi.jolt;

import farm.query.vgi.Worker;

/**
 * VGI worker entry point for declarative JSON&rarr;JSON structural transformation
 * via <a href="https://github.com/bazaarvoice/jolt">Bazaarvoice Jolt</a>
 * (Apache-2.0).
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * ATTACH 'jolt' (TYPE vgi, LOCATION 'java -jar vgi-jolt-all.jar');
 * SELECT jolt.jolt_transform('{"rating":{"primary":{"value":3}}}',
 *   '[{"operation":"shift","spec":{"rating":{"primary":{"value":"Rating"}}}}]');
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_JOLT_GIT_COMMIT") != null
                    ? System.getenv("VGI_JOLT_GIT_COMMIT") : "unknown";

    /** Catalog-level metadata tags (provenance, descriptions, support) for the linter and DuckDB. */
    private static java.util.Map<String, String> catalogTags() {
        var tags = new java.util.LinkedHashMap<String, String>();
        tags.put("vgi.title", "Jolt JSON Transformation");
        tags.put(
                "vgi.keywords",
                "[\"jolt\",\"json\",\"transform\",\"transformation\",\"shift\",\"default\","
                        + "\"chainr\",\"restructure\",\"reshape\",\"remap\",\"rename\","
                        + "\"normalize\",\"json validation\",\"bazaarvoice\"]");
        tags.put(
                "vgi.doc_llm",
                "Apply declarative Bazaarvoice Jolt transformations to JSON documents in SQL: "
                        + "run a full Jolt chainr spec (a JSON array of shift/default/remove/"
                        + "cardinality/sort/modify operations), or a single shift or default "
                        + "operation, to restructure, rename, reshape, and enrich JSON; plus "
                        + "validators for Jolt specs and JSON documents. Use to normalize, remap, "
                        + "or reshape JSON payloads (API responses, event streams, nested records) "
                        + "without leaving SQL.");
        tags.put(
                "vgi.doc_md",
                "# Jolt JSON Transformation for DuckDB\n\n"
                        + "**Reshape, rename, and restructure JSON directly in SQL** with "
                        + "declarative JSON-to-JSON transformations powered by "
                        + "[Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt) — no "
                        + "imperative parsing code, no external ETL step, just a Jolt spec "
                        + "and a SQL query.\n\n"
                        + "This extension brings Jolt's battle-tested declarative JSON "
                        + "transformation engine to DuckDB so data engineers, analysts, and "
                        + "API integrators can normalize, remap, flatten, and enrich JSON "
                        + "documents where the data already lives. Instead of writing "
                        + "language-specific code to walk and rebuild nested objects, you "
                        + "describe the *target shape* with a Jolt spec and let the engine "
                        + "do the structural work. It is ideal for cleaning up messy API "
                        + "responses, conforming event-stream payloads to a canonical schema, "
                        + "and reshaping nested records for downstream analytics — entirely "
                        + "inside your DuckDB pipeline.\n\n"
                        + "Under the hood the worker embeds "
                        + "[Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt) "
                        + "(Apache-2.0), the widely used JVM library for specification-driven "
                        + "JSON transformation. A Jolt **chainr** spec is a JSON array of "
                        + "ordered operations — `shift` (path-based copy/move of values into a "
                        + "new tree), `default` (supply missing keys), `remove`, `cardinality` "
                        + "(single&harr;list coercion), `sort`, and the `modify-*-beta` "
                        + "function operators — applied in sequence to transform an input "
                        + "document into an output document. The worker compiles each spec "
                        + "once per Arrow batch and reuses it across rows for efficient "
                        + "columnar execution. To learn the spec language hands-on, try the "
                        + "[interactive Jolt demo](https://jolt-demo.appspot.com/) and read "
                        + "the [Jolt documentation](https://github.com/bazaarvoice/jolt/blob/master/README.md).\n\n"
                        + "## SQL functions\n\n"
                        + "- `jolt_transform(input_json, spec_json)` — apply a full Jolt "
                        + "chainr spec (a JSON array of operations) to a JSON document. The "
                        + "workhorse for multi-step restructuring.\n"
                        + "- `jolt_shift(input_json, shift_spec)` — apply a single `shift` "
                        + "operation to copy, move, rename, and nest fields by path.\n"
                        + "- `jolt_default(input_json, default_spec)` — apply a single "
                        + "`default` operation to fill in missing keys with default values.\n"
                        + "- `is_valid_jolt_spec(spec_json)` — return `true` when a chainr "
                        + "spec is well-formed and compilable, without throwing.\n"
                        + "- `json_valid(input_json)` — return `true` when the input is "
                        + "valid JSON, for guarding transforms against malformed payloads.\n\n"
                        + "```sql\n"
                        + "SELECT jolt.jolt_transform(\n"
                        + "  '{\"rating\":{\"primary\":{\"value\":3}}}',\n"
                        + "  '[{\"operation\":\"shift\",\"spec\":"
                        + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]');\n"
                        + "```\n\n"
                        + "Built and maintained by [Query.Farm](https://query.farm) for the "
                        + "[DuckDB](https://duckdb.org/) ecosystem.");
        tags.put("vgi.author", "Query.Farm");
        tags.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        tags.put("vgi.license", "MIT");
        tags.put("vgi.support_contact", "https://github.com/Query-farm/vgi-jolt/issues");
        tags.put(
                "vgi.support_policy_url",
                "https://github.com/Query-farm/vgi-jolt/blob/main/README.md");
        return tags;
    }

    /** Schema-level metadata tags for the single {@code main} schema. */
    private static java.util.Map<String, String> schemaTags() {
        var tags = new java.util.LinkedHashMap<String, String>();
        tags.put("vgi.title", "Jolt — main");
        tags.put(
                "vgi.keywords",
                "[\"jolt\",\"json\",\"transform\",\"shift\",\"default\",\"jolt_transform\","
                        + "\"jolt_shift\",\"jolt_default\",\"is_valid_jolt_spec\",\"json_valid\","
                        + "\"validation\",\"reshape\"]");
        // VGI123 classifying tags — BARE keys (NOT vgi.-namespaced) for faceting.
        tags.put("domain", "data-transformation");
        tags.put("category", "json");
        tags.put("topic", "structural-transformation");
        tags.put(
                "vgi.doc_llm",
                "JSON transformation and validation functions powered by Bazaarvoice Jolt: apply a "
                        + "full Jolt chainr spec or a single shift/default operation to a JSON "
                        + "document, and validate Jolt specs and JSON documents.");
        tags.put(
                "vgi.doc_md",
                "Declarative JSON->JSON transformation and validation functions over Apache "
                        + "Arrow, powered by Bazaarvoice Jolt. Apply a full chainr spec or a "
                        + "single shift/default operation to restructure, rename, nest, and "
                        + "default JSON fields, and validate Jolt specs and JSON documents — all "
                        + "from SQL.");
        // VGI506 representative example queries for the schema (plain SQL string).
        tags.put(
                "vgi.example_queries",
                "SELECT jolt.main.jolt_transform("
                        + "'{\"rating\":{\"primary\":{\"value\":3}}}', "
                        + "'[{\"operation\":\"shift\",\"spec\":"
                        + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]');\n"
                        + "SELECT jolt.main.jolt_shift("
                        + "'{\"first\":\"Ada\",\"last\":\"Lovelace\"}', "
                        + "'{\"first\":\"name.given\",\"last\":\"name.family\"}');\n"
                        + "SELECT jolt.main.jolt_default("
                        + "'{\"name\":\"widget\"}', "
                        + "'{\"inStock\":true,\"currency\":\"USD\"}');\n"
                        + "SELECT jolt.main.is_valid_jolt_spec("
                        + "'[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]');\n"
                        + "SELECT jolt.main.json_valid('{\"a\":[1,2,3]}');");
        return tags;
    }

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("jolt")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("Declarative JSON->JSON structural transformation via Bazaarvoice Jolt")
                .catalogTags(catalogTags())
                .sourceUrl("https://github.com/Query-farm/vgi-jolt")
                .schemaComment("main", "Declarative JSON->JSON transformation and validation functions.")
                .schemaTags("main", schemaTags())
                .registerScalar(new TransformFunction())
                .registerScalar(new ShiftFunction())
                .registerScalar(new DefaultFunction())
                .registerScalar(new IsValidSpecFunction())
                .registerScalar(new JsonValidFunction());
    }

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        buildWorker().runFromArgs(args);
    }
}
