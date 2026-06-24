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
        tags.put(
                "vgi.description_llm",
                "Apply declarative Bazaarvoice Jolt transformations to JSON documents in SQL: "
                        + "run a full Jolt chainr spec (a JSON array of shift/default/remove/"
                        + "cardinality/sort/modify operations), or a single shift or default "
                        + "operation, to restructure, rename, reshape, and enrich JSON; plus "
                        + "validators for Jolt specs and JSON documents. Use to normalize, remap, "
                        + "or reshape JSON payloads (API responses, event streams, nested records) "
                        + "without leaving SQL.");
        tags.put(
                "vgi.description_md",
                "# jolt\n\n"
                        + "Declarative **JSON->JSON** structural transformation over Apache Arrow, "
                        + "powered by [Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt).\n\n"
                        + "Scalars: `jolt_transform` (full chainr spec), `jolt_shift`, "
                        + "`jolt_default`, `is_valid_jolt_spec`, `json_valid`.");
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
        tags.put(
                "vgi.description_llm",
                "JSON transformation and validation functions powered by Bazaarvoice Jolt: apply a "
                        + "full Jolt chainr spec or a single shift/default operation to a JSON "
                        + "document, and validate Jolt specs and JSON documents.");
        tags.put(
                "vgi.description_md",
                "Declarative JSON->JSON transformation and validation functions over Apache Arrow.");
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
