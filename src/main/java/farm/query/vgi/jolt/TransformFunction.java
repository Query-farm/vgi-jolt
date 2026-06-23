package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
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
        return FunctionMetadata.describe(description()).withCategories("jolt", "json", "transform");
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
