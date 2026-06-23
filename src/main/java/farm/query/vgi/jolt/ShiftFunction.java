package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.JsonUtils;
import com.bazaarvoice.jolt.Shiftr;
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
        return FunctionMetadata.describe(description()).withCategories("jolt", "json", "transform");
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "input_json", any = true) FieldVector in,
                        @Const("shift_spec") String shiftSpec,
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
