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
        String exampleSql =
                "SELECT jolt.main.is_valid_jolt_spec("
                        + "'[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]');";
        String exampleDesc =
                "Returns true: the input parses and compiles as a Jolt chainr spec. An "
                        + "unknown operation or malformed JSON returns false instead of "
                        + "erroring.";
        return FunctionMetadata.describe(description())
                .withCategories("jolt", "json", "validation")
                .withExamples(java.util.List.of(
                        new FunctionExample(exampleSql, exampleDesc, null)))
                .withTags(Meta.objectTags(
                        "Validate Jolt Spec String",
                        "Test whether a string is a valid [Bazaarvoice "
                                + "Jolt](https://github.com/bazaarvoice/jolt) **chainr** spec, "
                                + "returning a BOOLEAN.\n\n"
                                + "Returns `true` when the input both parses as JSON and compiles "
                                + "via `Chainr.fromSpec` (i.e. it is a JSON array of recognized "
                                + "operation entries). Returns `false` for malformed JSON, a "
                                + "non-array, or an unknown/invalid operation &mdash; it never "
                                + "raises an error, so it is safe as a guard or filter before "
                                + "calling `jolt_transform`.\n\n"
                                + "**Input:** `spec_json` (VARCHAR/BLOB). **Output:** BOOLEAN, or "
                                + "NULL for a NULL input row.",
                        "# is_valid_jolt_spec\n\n"
                                + "Return whether a string compiles as a Jolt chainr spec.\n\n"
                                + "## Usage\n\n"
                                + "```sql\n"
                                + "SELECT jolt.main.is_valid_jolt_spec(\n"
                                + "  '[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]');"
                                + "  -- true\n"
                                + "SELECT jolt.main.is_valid_jolt_spec('not json');  -- false\n"
                                + "```\n\n"
                                + "## Notes\n\n"
                                + "- Never throws: invalid input returns `false`, not an error.\n"
                                + "- NULL input row maps to a NULL result.\n"
                                + "- Use as a pre-flight check before `jolt_transform`, which "
                                + "does raise on an invalid spec.",
                        "jolt, validate, valid, spec, chainr, check, lint, json, "
                                + "is_valid_jolt_spec, well-formed",
                        "IsValidSpecFunction.java"))
                .withTag("vgi.example_queries",
                        JoltEngine.exampleQueriesTag(exampleDesc, exampleSql));
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
