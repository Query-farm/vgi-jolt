package farm.query.vgi.jolt;

import farm.query.vgi.protocol.FunctionExample;

/**
 * Example queries that <em>combine</em> two or more of this worker's functions in
 * one realistic pipeline (validate &rarr; reshape &rarr; fill defaults &rarr;
 * aggregate). Each SQL string is defined once here and referenced from the
 * schema-level {@code vgi.example_queries} list and from the example list of the
 * function that drives the query, so the two can never drift apart.
 */
public final class ComposedExamples {

    private ComposedExamples() {}

    /** A {@code (description, sql)} example pair. */
    public record Example(String description, String sql) {
        /** This example as a function-level {@link FunctionExample} (no expected output). */
        public FunctionExample toFunctionExample() {
            return new FunctionExample(sql, description, null);
        }
    }

    /** json_valid as a WHERE guard in front of jolt_shift: malformed rows are skipped, not fatal. */
    public static final Example GUARDED_SHIFT = new Example(
            "Skip malformed payloads with json_valid, then reshape the well-formed ones with "
                    + "jolt_shift: row 2 is truncated JSON and is filtered out, rows 1 and 3 "
                    + "become {\"device\":...,\"celsius\":...} documents.",
            "SELECT raw.id, jolt.main.jolt_shift(raw.payload, "
                    + "'{\"dev\":\"device\",\"reading\":{\"c\":\"celsius\"}}') AS normalized "
                    + "FROM (VALUES (1, '{\"dev\":\"t-1\",\"reading\":{\"c\":21.5}}'), "
                    + "(2, '{\"dev\":\"t-2\",\"reading\":'), "
                    + "(3, '{\"dev\":\"t-3\",\"reading\":{\"c\":19.0}}')) AS raw(id, payload) "
                    + "WHERE jolt.main.json_valid(raw.payload) ORDER BY raw.id;");

    /**
     * jolt_shift flattens nested order payloads, jolt_default fills the missing
     * currency, then plain SQL aggregates the canonical documents.
     */
    public static final Example SHIFT_DEFAULT_REVENUE = new Example(
            "Normalize nested order payloads with jolt_shift, fill the missing currency with "
                    + "jolt_default (USD), then total revenue per currency: EUR has 2 orders "
                    + "summing 40.5 and the two currency-less orders roll up as USD 49.5.",
            "WITH orders(payload) AS (VALUES "
                    + "('{\"order\":{\"id\":101,\"total\":{\"amount\":25.0,\"currency\":\"EUR\"}}}'), "
                    + "('{\"order\":{\"id\":102,\"total\":{\"amount\":40.0}}}'), "
                    + "('{\"order\":{\"id\":103,\"total\":{\"amount\":15.5,\"currency\":\"EUR\"}}}'), "
                    + "('{\"order\":{\"id\":104,\"total\":{\"amount\":9.5}}}')), "
                    + "canonical AS (SELECT jolt.main.jolt_default(jolt.main.jolt_shift(orders.payload, "
                    + "'{\"order\":{\"id\":\"order_id\",\"total\":{\"amount\":\"amount\","
                    + "\"currency\":\"currency\"}}}'), '{\"currency\":\"USD\"}') AS doc FROM orders) "
                    + "SELECT json_extract_string(canonical.doc, '$.currency') AS currency, "
                    + "count(*) AS order_count, "
                    + "sum(CAST(json_extract_string(canonical.doc, '$.amount') AS DOUBLE)) AS revenue "
                    + "FROM canonical GROUP BY currency ORDER BY currency;");

    /** Preflight a mapping registry: is each sample payload JSON, and does each spec compile? */
    public static final Example MAPPING_PREFLIGHT = new Example(
            "Preflight a registry of mappings before deploying them: json_valid checks each "
                    + "sample payload and is_valid_jolt_spec checks each chainr spec. orders_v2 "
                    + "misspells the shift operation (spec_ok false) and sensors_v1 has a "
                    + "trailing comma in its sample (sample_ok false).",
            "SELECT m.mapping, jolt.main.json_valid(m.sample_doc) AS sample_ok, "
                    + "jolt.main.is_valid_jolt_spec(m.spec) AS spec_ok "
                    + "FROM (VALUES ('orders_v1', '{\"order\":{\"id\":1}}', "
                    + "'[{\"operation\":\"shift\",\"spec\":{\"order\":{\"id\":\"order_id\"}}}]'), "
                    + "('orders_v2', '{\"order\":{\"id\":2}}', "
                    + "'[{\"operation\":\"shfit\",\"spec\":{\"order\":{\"id\":\"order_id\"}}}]'), "
                    + "('sensors_v1', '{\"dev\":\"t-1\",}', "
                    + "'[{\"operation\":\"default\",\"spec\":{\"unit\":\"C\"}}]')) "
                    + "AS m(mapping, sample_doc, spec) ORDER BY m.mapping;");
}
