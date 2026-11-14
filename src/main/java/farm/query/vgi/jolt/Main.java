package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.JsonUtils;
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
                "vgi.subtitle",
                "Reshape, rename, and validate JSON documents with declarative Bazaarvoice Jolt specs.");
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
                        + "## When to reach for it\n\n"
                        + "Reach for this worker whenever a JSON document arrives in the "
                        + "wrong shape for what comes next: keys need renaming, values "
                        + "need lifting out of (or nesting into) deep structures, arrays "
                        + "need flattening or grouping, missing fields need sensible "
                        + "defaults, or a heterogeneous feed needs conforming to one "
                        + "canonical schema. Because the transformation is a declarative "
                        + "spec rather than procedural code, the same query adapts to new "
                        + "shapes by editing the spec — not by rewriting logic.\n\n"
                        + "## Key concepts\n\n"
                        + "- **Spec-driven.** You describe the *target* shape; Jolt does "
                        + "the structural walking. A full **chainr** spec is an ordered "
                        + "JSON array of operations, applied in sequence.\n"
                        + "- **Operation vocabulary.** `shift` (path-based copy/move), "
                        + "`default` (supply absent keys), `remove`, `cardinality` "
                        + "(single↔list coercion), `sort`, and the `modify-*-beta` "
                        + "function operators.\n"
                        + "- **Validation first.** Companion predicates let a pipeline "
                        + "check that a spec compiles and that a payload is well-formed "
                        + "JSON before attempting a transform, so bad rows can be filtered "
                        + "rather than fault the query.\n"
                        + "- **Columnar & batched.** Each spec is compiled once per Arrow "
                        + "batch and reused across rows, and the document column is the "
                        + "data vector while the spec is a query constant.\n\n"
                        + "A worked example — lift a deeply nested value up to a "
                        + "top-level key with a full chainr spec:\n\n"
                        + "```sql\n"
                        + "SELECT jolt.jolt_transform(\n"
                        + "  '{\"rating\":{\"primary\":{\"value\":3}}}',\n"
                        + "  '[{\"operation\":\"shift\",\"spec\":"
                        + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]');\n"
                        + "```\n\n"
                        + "Built and maintained by [Query.Farm](https://query.farm) for the "
                        + "[DuckDB](https://duckdb.org/) ecosystem.");
        // VGI152/VGI920 fixed analyst-task suite for `vgi-lint simulate`: names +
        // prompts only; the private graders live in vgi-agent-tests.yaml (VGI416).
        tags.put("vgi.agent_test_tasks", agentTestTasks());
        tags.put("vgi.author", "Query.Farm");
        tags.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        tags.put("vgi.license", "MIT");
        tags.put("vgi.support_contact", "https://github.com/Query-farm/vgi-jolt/issues");
        tags.put(
                "vgi.support_policy_url",
                "https://github.com/Query-farm/vgi-jolt/blob/main/README.md");
        return tags;
    }

    /**
     * Build the {@code vgi.agent_test_tasks} JSON suite (VGI152/VGI920). Only the
     * public {@code name} + {@code prompt} are published (VGI416); the private
     * grader fields ({@code reference_sql}, {@code success_criteria}, ...) live in
     * the {@code vgi-agent-tests.yaml} sidecar at the repo root, keyed by task name.
     */
    private static String agentTestTasks() {
        var tasks = new java.util.ArrayList<java.util.Map<String, Object>>();
        tasks.add(agentTask(
                "transform_lift_nested_rating",
                "Given the JSON document {\"rating\":{\"primary\":{\"value\":3}}}, apply a "
                        + "Jolt transformation that lifts the deeply nested rating value up to a "
                        + "single top-level key named \"Rating\". Return the resulting JSON "
                        + "document."));
        tasks.add(agentTask(
                "shift_rename_into_name_object",
                "Given the JSON document {\"first\":\"Ada\",\"last\":\"Lovelace\"}, use a "
                        + "single Jolt shift operation to move the \"first\" field to name.given "
                        + "and the \"last\" field to name.family. Return the resulting JSON "
                        + "document."));
        tasks.add(agentTask(
                "default_supply_currency",
                "Given the JSON document {\"name\":\"widget\"}, use a Jolt default operation "
                        + "to supply the value \"USD\" for a \"currency\" key only when that key "
                        + "is absent from the input. Return the resulting JSON document."));
        tasks.add(agentTask(
                "validate_jolt_spec",
                "Determine whether the string "
                        + "[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}] is a valid "
                        + "Bazaarvoice Jolt chainr specification. Return a single boolean."));
        tasks.add(agentTask(
                "validate_json_document",
                "Using this worker, test whether the literal text value "
                        + "not valid json is a well-formed JSON document. Pass that text "
                        + "straight to the worker's JSON-validity check and return the single "
                        + "boolean it produces."));
        tasks.add(agentTask(
                "validate_reshape_default_tiers",
                "Five raw signup payloads arrive as JSON text: {\"usr\":\"a1\",\"plan\":\"pro\"}, "
                        + "{\"usr\":\"b2\"}, {\"usr\":\"c3\" (truncated), "
                        + "{\"usr\":\"d4\",\"plan\":\"team\"} and {\"usr\":\"e5\"}. Using this "
                        + "worker, drop any payload that is not well-formed JSON, reshape the rest "
                        + "with a Jolt shift that moves usr to user.id and plan to user.tier, then "
                        + "apply a Jolt default so user.tier is \"free\" when it is missing. Return "
                        + "one row per surviving payload with two VARCHAR columns, user_id then "
                        + "tier, ordered by user_id."));
        return JsonUtils.toJsonString(tasks);
    }

    private static java.util.Map<String, Object> agentTask(String name, String prompt) {
        var t = new java.util.LinkedHashMap<String, Object>();
        t.put("name", name);
        t.put("prompt", prompt);
        return t;
    }

    /** Schema-level metadata tags for the single {@code main} schema. */
    private static java.util.Map<String, String> schemaTags() {
        var tags = new java.util.LinkedHashMap<String, String>();
        tags.put("vgi.title", "Jolt Functions");
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
                "# Jolt transformation & validation\n\n"
                        + "Declarative JSON→JSON transformation and validation over Apache "
                        + "Arrow, powered by [Bazaarvoice "
                        + "Jolt](https://github.com/bazaarvoice/jolt). This schema groups the "
                        + "worker's SQL functions for reshaping JSON documents and for "
                        + "guarding those transforms against malformed input.\n\n"
                        + "## What lives here\n\n"
                        + "- **Transformation** — reshape, rename, nest, flatten, and default "
                        + "JSON fields by describing the target shape as a Jolt spec instead "
                        + "of writing imperative traversal code.\n"
                        + "- **Validation** — cheap, never-throwing predicates that check a "
                        + "spec compiles and a payload is well-formed JSON, so a pipeline can "
                        + "filter bad rows before transforming.\n\n"
                        + "## When to use it\n\n"
                        + "Use it to normalize messy API responses, conform event-stream "
                        + "payloads to a canonical schema, and restructure nested records for "
                        + "downstream analytics — all inside DuckDB, with no external ETL "
                        + "step. Transforms raise on malformed input, while the validation "
                        + "predicates never throw — pair them to guard a pipeline and filter "
                        + "bad rows before reshaping.");
        // VGI413 category registry: ordered array of {name, title?, description?}.
        // Each function carries a vgi.category naming one of these slugs.
        tags.put(
                "vgi.categories",
                "[{\"name\":\"transformation\",\"title\":\"Transformation\","
                        + "\"description\":\"Reshape, rename, nest, flatten, and default JSON "
                        + "documents by applying a Bazaarvoice Jolt spec — a full chainr or a "
                        + "single shift/default operation.\"},"
                        + "{\"name\":\"validation\",\"title\":\"Validation\","
                        + "\"description\":\"Cheap, never-throwing predicates that check a Jolt "
                        + "spec compiles and that a payload is well-formed JSON, so a pipeline "
                        + "can guard transforms and filter bad rows.\"}]");
        // VGI506 representative example queries for the schema (plain SQL string).
        tags.put(
                "vgi.example_queries",
                JoltEngine.executableExamplesTag(
                        "Apply a full chainr spec to lift a deeply nested rating value up "
                                + "to a top-level Rating key.",
                        "SELECT jolt.main.jolt_transform("
                                + "'{\"rating\":{\"primary\":{\"value\":3}}}', "
                                + "'[{\"operation\":\"shift\",\"spec\":"
                                + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]');",
                        "Rename and nest flat keys into a name object with a single shift "
                                + "operation.",
                        "SELECT jolt.main.jolt_shift("
                                + "'{\"first\":\"Ada\",\"last\":\"Lovelace\"}', "
                                + "'{\"first\":\"name.given\",\"last\":\"name.family\"}');",
                        "Supply default values for keys absent from the input document.",
                        "SELECT jolt.main.jolt_default("
                                + "'{\"name\":\"widget\"}', "
                                + "'{\"inStock\":true,\"currency\":\"USD\"}');",
                        "Guard a pipeline by checking that a string is a well-formed Jolt "
                                + "chainr spec before transforming.",
                        "SELECT jolt.main.is_valid_jolt_spec("
                                + "'[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]');",
                        "Check that a payload is well-formed JSON before reshaping it.",
                        "SELECT jolt.main.json_valid('{\"a\":[1,2,3]}');",
                        ComposedExamples.GUARDED_SHIFT.description(),
                        ComposedExamples.GUARDED_SHIFT.sql(),
                        ComposedExamples.SHIFT_DEFAULT_REVENUE.description(),
                        ComposedExamples.SHIFT_DEFAULT_REVENUE.sql(),
                        ComposedExamples.MAPPING_PREFLIGHT.description(),
                        ComposedExamples.MAPPING_PREFLIGHT.sql()));
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
