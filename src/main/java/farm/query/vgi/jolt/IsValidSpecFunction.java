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
 * {@code jolt.is_valid_jolt_spec(spec_json) -> BOOLEAN} — true when the input
 * parses and compiles as a Jolt chainr spec. NULL input → NULL; malformed/invalid
 * spec → false (never an error).
 */
public final class IsValidSpecFunction extends ScalarFn {

    @Override public String name() { return "is_valid_jolt_spec"; }

    @Override public String description() {
        return "True when the input parses and compiles as a Bazaarvoice Jolt chainr spec "
                + "(a JSON array of operations).";
    }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "validation")
                .withTag("vgi.example_queries", JoltEngine.exampleQueriesTag(
                        "SELECT jolt.main.is_valid_jolt_spec("
                                + "'[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]');",
                        "Returns true: the input parses and compiles as a Jolt chainr spec. An "
                                + "unknown operation or malformed JSON returns false instead of "
                                + "erroring."));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.BOOL;
    }

    public void compute(@Vector(value = "spec_json", any = true) FieldVector in, BitVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            String spec = JsonInput.at(in, i);
            if (spec == null) { out.setNull(i); continue; }
            out.setSafe(i, JoltEngine.isValidSpec(spec) ? 1 : 0);
        }
    }
}
