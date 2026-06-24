package farm.query.vgi.jolt;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
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
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "validation")
                .withTag("vgi.example_queries", JoltEngine.exampleQueriesTag(
                        "SELECT jolt.main.json_valid('{\"a\":[1,2,3]}');",
                        "Returns true: the input is a well-formed JSON document. Malformed JSON "
                                + "returns false instead of erroring."));
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
