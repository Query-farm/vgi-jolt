package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.JsonUtils;
import com.bazaarvoice.jolt.Shiftr;
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
 * {@code jolt.jolt_shift(input_json, shift_spec) -> VARCHAR} — convenience for the
 * single most common Jolt operation. {@code shift_spec} is the bare shift spec
 * object (no chainr operation envelope). NULL input row → NULL. Malformed input
 * JSON or shift spec raises a DuckDB error.
 */
public final class ShiftFunction extends ScalarFn {

    @Override public String name() { return "jolt_shift"; }

    @Override public String description() {
        return "Apply a single Jolt 'shift' operation (path-based copy/move of input values into "
                + "an output tree); shift_spec is the bare shift spec object. Returns JSON.";
    }

    @Override public FunctionMetadata metadata() {
        String exampleSql =
                "SELECT jolt.main.jolt_shift("
                        + "'{\"first\":\"Ada\",\"last\":\"Lovelace\"}', "
                        + "'{\"first\":\"name.given\",\"last\":\"name.family\"}');";
        String exampleDesc =
                "Apply a bare Jolt shift spec to rename and nest flat input keys into "
                        + "a name object with given/family.";
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "transform")
                .withExamples(java.util.List.of(
                        new FunctionExample(exampleSql, exampleDesc, null)))
                .withTags(Meta.objectTags(
                        "Apply Jolt Shift Operation",
                        "Apply a single [Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt) "
                                + "**shift** operation to a JSON document &mdash; the most common "
                                + "Jolt operation &mdash; and return the transformed JSON "
                                + "string.\n\n"
                                + "A shift spec is a tree that mirrors the input: each leaf is an "
                                + "**output path** telling Jolt where to copy the matched input "
                                + "value. This is the convenience entry point for the single "
                                + "shift case; for multi-step pipelines use `jolt_transform`.\n\n"
                                + "**Inputs:** `input_json` (VARCHAR/BLOB JSON, the data vector) "
                                + "and `shift_spec` (a constant **bare** shift spec object, NOT "
                                + "wrapped in the `{\"operation\":\"shift\",...}` chainr "
                                + "envelope). The `Shiftr` is built once per batch.\n\n"
                                + "**Output:** the reshaped document as a JSON VARCHAR. NULL "
                                + "input row yields NULL; malformed input JSON or shift spec "
                                + "raises a DuckDB error.",
                        "# jolt_shift\n\n"
                                + "Apply one Jolt **shift** operation &mdash; path-based "
                                + "copy/move of input values into an output tree.\n\n"
                                + "## Usage\n\n"
                                + "```sql\n"
                                + "SELECT jolt.main.jolt_shift(\n"
                                + "  '{\"first\":\"Ada\",\"last\":\"Lovelace\"}',\n"
                                + "  '{\"first\":\"name.given\",\"last\":\"name.family\"}');\n"
                                + "-- => {\"name\":{\"given\":\"Ada\",\"family\":\"Lovelace\"}}\n"
                                + "```\n\n"
                                + "The spec is the **bare** shift spec object, not the chainr "
                                + "operation envelope. Wildcards (`*`), array indexing, and `@`/"
                                + "`#`/`$` references follow standard Jolt shift semantics.\n\n"
                                + "## Notes\n\n"
                                + "- Returns VARCHAR JSON; output key order is not guaranteed.\n"
                                + "- NULL input row maps to a NULL result.\n"
                                + "- Malformed input JSON or shift spec raises an error.",
                        "jolt, shift, shiftr, json, copy, move, rename, nest, restructure, "
                                + "path, mapping, json shift",
                        "ShiftFunction.java"))
                .withTag("vgi.example_queries",
                        JoltEngine.exampleQueriesTag(exampleDesc, exampleSql))
                .withTag("vgi.executable_examples", JoltEngine.executableExamplesTag(
                        "Rename and nest flat keys into a name object via a bare shift spec.",
                        "SELECT jolt.main.jolt_shift("
                                + "'{\"first\":\"Ada\",\"last\":\"Lovelace\"}', "
                                + "'{\"first\":\"name.given\",\"last\":\"name.family\"}') AS out",
                        "Lift a deeply nested value to the top level with a full chainr spec.",
                        "SELECT jolt.main.jolt_transform("
                                + "'{\"rating\":{\"primary\":{\"value\":3}}}', "
                                + "'[{\"operation\":\"shift\",\"spec\":"
                                + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]') AS out",
                        "Fill in default keys absent from the input document.",
                        "SELECT jolt.main.jolt_default("
                                + "'{\"name\":\"widget\"}', "
                                + "'{\"inStock\":true,\"currency\":\"USD\"}') AS out",
                        "Validate that a string is a well-formed Jolt chainr spec.",
                        "SELECT jolt.main.is_valid_jolt_spec("
                                + "'[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]') AS ok",
                        "Validate that a string is a well-formed JSON document.",
                        "SELECT jolt.main.json_valid('{\"a\":[1,2,3]}') AS ok"));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "input_json", any = true,
                                doc = "The JSON document to reshape (one value per row, given "
                                        + "either as JSON text or as raw JSON bytes). This is the "
                                        + "data column; the shift spec is applied to each row's "
                                        + "document. A NULL row yields a NULL result; malformed "
                                        + "JSON raises an error.") FieldVector in,
                        @Const(value = "shift_spec",
                               doc = "A constant bare Jolt shift specification (NOT wrapped in the "
                                       + "{\"operation\":\"shift\",...} chainr envelope): a tree "
                                       + "mirroring the input where each leaf is the output path "
                                       + "Jolt copies the matched input value to. Compiled once per "
                                       + "batch; an invalid spec raises an error.")
                               String shiftSpec,
                        VarCharVector out) {
        Object spec;
        try {
            spec = JsonUtils.jsonToObject(shiftSpec);
        } catch (Exception e) {
            throw new JoltEngine.JoltException("malformed shift spec JSON: " + e.getMessage(), e);
        }
        Shiftr shiftr;
        try {
            shiftr = new Shiftr(spec);
        } catch (Exception e) {
            throw new JoltEngine.JoltException("invalid shift spec: " + e.getMessage(), e);
        }
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
                result = shiftr.transform(parsed);
            } catch (Exception e) {
                throw new JoltEngine.JoltException("Jolt shift failed: " + e.getMessage(), e);
            }
            out.setSafe(i, new Text(JsonUtils.toJsonString(result)));
        }
    }
}
