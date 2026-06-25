package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.Defaultr;
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
 * {@code jolt.jolt_default(input_json, default_spec) -> VARCHAR} — apply a single
 * Jolt 'default' operation, filling in keys/values absent from the input.
 * {@code default_spec} is the bare default spec object. NULL input row → NULL.
 * Malformed input JSON or default spec raises a DuckDB error.
 */
public final class DefaultFunction extends ScalarFn {

    @Override public String name() { return "jolt_default"; }

    @Override public String description() {
        return "Apply a single Jolt 'default' operation (supply default values for keys absent "
                + "from the input); default_spec is the bare default spec object. Returns JSON.";
    }

    @Override public FunctionMetadata metadata() {
        String exampleSql =
                "SELECT jolt.main.jolt_default("
                        + "'{\"name\":\"widget\"}', "
                        + "'{\"inStock\":true,\"currency\":\"USD\"}');";
        String exampleDesc =
                "Apply a bare Jolt default spec to fill in inStock and currency keys "
                        + "that are absent from the input document.";
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "transform")
                .withExamples(java.util.List.of(
                        new FunctionExample(exampleSql, exampleDesc, null)))
                .withTags(Meta.objectTags(
                        "Apply Jolt Default Operation",
                        "Apply a single [Bazaarvoice Jolt](https://github.com/bazaarvoice/jolt) "
                                + "**default** operation to a JSON document and return the "
                                + "result.\n\n"
                                + "The default operation supplies values for keys that are "
                                + "**absent** from the input; existing keys are left untouched. "
                                + "Use it to normalize documents to a known shape (guaranteeing "
                                + "fields exist, supplying fallback defaults) before downstream "
                                + "processing.\n\n"
                                + "**Inputs:** `input_json` (VARCHAR/BLOB JSON, the data vector) "
                                + "and `default_spec` (a constant **bare** default spec object). "
                                + "The `Defaultr` is built once per batch.\n\n"
                                + "**Output:** the enriched document as a JSON VARCHAR. NULL "
                                + "input row yields NULL; malformed input JSON or default spec "
                                + "raises a DuckDB error.",
                        "# jolt_default\n\n"
                                + "Apply one Jolt **default** operation &mdash; supply values "
                                + "for keys absent from the input document.\n\n"
                                + "## Usage\n\n"
                                + "```sql\n"
                                + "SELECT jolt.main.jolt_default(\n"
                                + "  '{\"name\":\"widget\"}',\n"
                                + "  '{\"inStock\":true,\"currency\":\"USD\"}');\n"
                                + "-- => {\"name\":\"widget\",\"inStock\":true,"
                                + "\"currency\":\"USD\"}\n"
                                + "```\n\n"
                                + "Keys already present in the input win; the spec only fills "
                                + "gaps. The spec is the **bare** default spec object, not the "
                                + "chainr operation envelope.\n\n"
                                + "## Notes\n\n"
                                + "- Returns VARCHAR JSON; output key order is not guaranteed.\n"
                                + "- NULL input row maps to a NULL result.\n"
                                + "- Malformed input JSON or default spec raises an error.",
                        "jolt, default, defaultr, json, fill, fallback, supply defaults, "
                                + "normalize, ensure keys, json default",
                        "DefaultFunction.java"))
                .withTag("vgi.example_queries",
                        JoltEngine.exampleQueriesTag(exampleDesc, exampleSql));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "input_json", any = true,
                                doc = "The JSON document to enrich (one value per row, given "
                                        + "either as JSON text or as raw JSON bytes). This is the "
                                        + "data column; the default spec fills in keys absent from "
                                        + "each row's document. A NULL row yields a NULL result; "
                                        + "malformed JSON raises an error.") FieldVector in,
                        @Const(value = "default_spec",
                               doc = "A constant bare Jolt default specification (NOT wrapped in "
                                       + "the chainr operation envelope): a tree of keys/values "
                                       + "supplied only where the input is missing them; existing "
                                       + "keys are left untouched. Compiled once per batch; an "
                                       + "invalid spec raises an error.")
                               String defaultSpec,
                        VarCharVector out) {
        Object spec;
        try {
            spec = JsonUtils.jsonToObject(defaultSpec);
        } catch (Exception e) {
            throw new JoltEngine.JoltException("malformed default spec JSON: " + e.getMessage(), e);
        }
        Defaultr defaultr;
        try {
            defaultr = new Defaultr(spec);
        } catch (Exception e) {
            throw new JoltEngine.JoltException("invalid default spec: " + e.getMessage(), e);
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
                result = defaultr.transform(parsed);
            } catch (Exception e) {
                throw new JoltEngine.JoltException("Jolt default failed: " + e.getMessage(), e);
            }
            out.setSafe(i, new Text(JsonUtils.toJsonString(result)));
        }
    }
}
