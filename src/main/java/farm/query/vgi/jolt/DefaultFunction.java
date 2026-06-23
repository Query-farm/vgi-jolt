package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.Defaultr;
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
        return FunctionMetadata.describe(description()).withCategories("jolt", "json", "transform");
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "input_json", any = true) FieldVector in,
                        @Const("default_spec") String defaultSpec,
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
