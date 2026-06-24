package farm.query.vgi.jolt;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * {@code jolt.json_valid(input_json) -> BOOLEAN} — true when the input parses as a
 * JSON document. A companion to the Jolt scalars for guarding inputs. NULL input →
 * NULL; malformed JSON → false (never an error).
 */
public final class JsonValidFunction extends ScalarFn {

    @Override public String name() { return "json_valid"; }

    @Override public String description() {
        return "True when the input parses as a well-formed JSON document.";
    }

    @Override public FunctionMetadata metadata() {
        String exampleSql = "SELECT jolt.main.json_valid('{\"a\":[1,2,3]}');";
        String exampleDesc =
                "Returns true: the input is a well-formed JSON document. Malformed JSON "
                        + "returns false instead of erroring.";
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "validation")
                .withExamples(java.util.List.of(
                        new FunctionExample(exampleSql, exampleDesc, null)))
                .withTags(Meta.objectTags(
                        "Validate JSON Document String",
                        "Test whether a string is a well-formed JSON document, returning a "
                                + "BOOLEAN.\n\n"
                                + "Returns `true` when the input parses via the Jolt JSON reader "
                                + "(`JsonUtils.jsonToObject`), `false` otherwise. It never raises "
                                + "an error, making it a safe companion guard for the transform "
                                + "scalars (`jolt_transform`, `jolt_shift`, `jolt_default`), "
                                + "which DO error on malformed input.\n\n"
                                + "**Input:** `input_json` (VARCHAR/BLOB). **Output:** BOOLEAN, "
                                + "or NULL for a NULL input row.",
                        "# json_valid\n\n"
                                + "Return whether a string parses as a well-formed JSON "
                                + "document.\n\n"
                                + "## Usage\n\n"
                                + "```sql\n"
                                + "SELECT jolt.main.json_valid('{\"a\":[1,2,3]}');  -- true\n"
                                + "SELECT jolt.main.json_valid('{oops');           -- false\n"
                                + "```\n\n"
                                + "## Notes\n\n"
                                + "- Never throws: malformed JSON returns `false`, not an "
                                + "error.\n"
                                + "- NULL input row maps to a NULL result.\n"
                                + "- Pair with the transform scalars to filter out bad rows "
                                + "before transforming.",
                        "json, valid, validate, well-formed, parse, check, guard, json_valid, "
                                + "is json",
                        "JsonValidFunction.java"))
                .withTag("vgi.example_queries",
                        JoltEngine.exampleQueriesTag(exampleDesc, exampleSql));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.BOOL;
    }

    public void compute(@Vector(value = "input_json", any = true) FieldVector in, BitVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            String json = JsonInput.at(in, i);
            if (json == null) { out.setNull(i); continue; }
            out.setSafe(i, JoltEngine.isValidJson(json) ? 1 : 0);
        }
    }
}
