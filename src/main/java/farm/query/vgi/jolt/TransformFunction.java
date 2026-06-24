package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.Const;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

/**
 * {@code jolt.jolt_transform(input_json, spec_json) -> VARCHAR} — apply a full
 * Jolt spec (a JSON array of operations, a "chainr") to {@code input_json} and
 * return the transformed JSON string. The headline function.
 *
 * <p>The spec is a query constant: it is parsed and compiled once, then reused
 * across every input row. NULL input row → NULL output. Malformed input JSON or a
 * malformed/invalid spec raises a DuckDB error.
 */
public final class TransformFunction extends ScalarFn {

    @Override public String name() { return "jolt_transform"; }

    @Override public String description() {
        return "Apply a full Bazaarvoice Jolt spec (a JSON array of shift/default/remove/"
                + "cardinality/sort/modify operations) to an input JSON document; returns the "
                + "transformed JSON string.";
    }

    @Override public FunctionMetadata metadata() {
        String exampleSql =
                "SELECT jolt.main.jolt_transform("
                        + "'{\"rating\":{\"primary\":{\"value\":3}}}', "
                        + "'[{\"operation\":\"shift\",\"spec\":"
                        + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]');";
        String exampleDesc =
                "Apply a full Jolt chainr spec (a single shift operation) to lift a "
                        + "deeply nested value up to a top-level Rating key.";
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "transform")
                .withExamples(java.util.List.of(
                        new FunctionExample(exampleSql, exampleDesc, null)))
                .withTags(Meta.objectTags(
                        "Apply Jolt Transformation Spec",
                        "Apply a full [Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt) "
                                + "**chainr** spec to a JSON document and return the transformed "
                                + "JSON string.\n\n"
                                + "A chainr spec is a JSON **array** of operation entries, each "
                                + "`{\"operation\": <name>, \"spec\": <op-spec>}`, applied in "
                                + "order. Supported operations include `shift` (path-based "
                                + "copy/move), `default` (fill absent keys), `remove` (delete "
                                + "keys), `cardinality` (single&harr;list), `sort`, and the "
                                + "`modify-*-beta` function operations.\n\n"
                                + "**Inputs:** `input_json` (VARCHAR or BLOB JSON document, taken "
                                + "as the data vector) and `spec_json` (a constant chainr spec, "
                                + "compiled once per batch and reused across rows).\n\n"
                                + "**Output:** the transformed document as a JSON VARCHAR. A NULL "
                                + "input row yields NULL. Malformed input JSON or a "
                                + "malformed/invalid spec raises a DuckDB error. Use this as the "
                                + "headline function for arbitrary multi-step JSON restructuring; "
                                + "for a single operation prefer `jolt_shift` or `jolt_default`.",
                        "# jolt_transform\n\n"
                                + "Apply a complete Jolt **chainr** specification to a JSON "
                                + "document, returning the restructured JSON.\n\n"
                                + "## Usage\n\n"
                                + "```sql\n"
                                + "SELECT jolt.main.jolt_transform(\n"
                                + "  '{\"rating\":{\"primary\":{\"value\":3}}}',\n"
                                + "  '[{\"operation\":\"shift\",\"spec\":"
                                + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]');\n"
                                + "-- => {\"Rating\":3}\n"
                                + "```\n\n"
                                + "The spec is a JSON array of operations (`shift`, `default`, "
                                + "`remove`, `cardinality`, `sort`, `modify-*-beta`) applied in "
                                + "sequence. The spec argument is a query constant: it is "
                                + "compiled once and reused for every row in the batch.\n\n"
                                + "## Notes\n\n"
                                + "- Returns VARCHAR JSON; key order in the output is not "
                                + "guaranteed by Jolt.\n"
                                + "- NULL input row maps to a NULL result.\n"
                                + "- Malformed input JSON or an invalid spec raises an error "
                                + "rather than returning NULL.",
                        "jolt, transform, chainr, json, restructure, reshape, remap, rename, "
                                + "shift, default, remove, cardinality, modify, json transformation",
                        "TransformFunction.java"))
                .withTag("vgi.example_queries",
                        JoltEngine.exampleQueriesTag(exampleDesc, exampleSql));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "input_json", any = true) FieldVector in,
                        @Const("spec_json") String specJson,
                        VarCharVector out) {
        // Compile the chainr once for the whole batch; a bad spec is a clear error.
        Chainr chainr = JoltEngine.compile(specJson);
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            String input = JsonInput.at(in, i);
            if (input == null) { out.setNull(i); continue; }
            Object parsed;
            try {
                parsed = JsonUtils.jsonToObject(input);
            } catch (Exception e) {
                throw new JoltEngine.JoltException("malformed input JSON: " + e.getMessage(), e);
            }
            Object result;
            try {
                result = chainr.transform(parsed);
            } catch (Exception e) {
                throw new JoltEngine.JoltException("Jolt transform failed: " + e.getMessage(), e);
            }
            out.setSafe(i, new Text(JsonUtils.toJsonString(result)));
        }
    }
}
