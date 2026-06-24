package farm.query.vgi.jolt;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.Defaultr;
import com.bazaarvoice.jolt.JsonUtils;
import com.bazaarvoice.jolt.Shiftr;

/**
 * Thin wrapper around Bazaarvoice Jolt that the scalar functions share.
 *
 * <p><a href="https://github.com/bazaarvoice/jolt">Jolt</a> performs declarative
 * JSON&rarr;JSON structural transformation. A <em>spec</em> is a JSON document
 * describing a list of operations applied in sequence (a "chainr"). Each operation
 * has a {@code "operation"} name and a {@code "spec"}:
 *
 * <ul>
 *   <li>{@code shift} &mdash; copy/move data from the input tree to the output tree
 *       by matching paths (the most common operation).</li>
 *   <li>{@code default} &mdash; apply default values for keys absent from the input.</li>
 *   <li>{@code remove} &mdash; delete keys from the input.</li>
 *   <li>{@code cardinality} &mdash; coerce between single values and lists.</li>
 *   <li>{@code sort} &mdash; recursively sort map keys alphabetically.</li>
 *   <li>{@code modify-overwrite-beta} / {@code modify-default-beta} /
 *       {@code modify-define-beta} &mdash; compute/derive values via functions.</li>
 * </ul>
 *
 * <p>See the Jolt docs for the full operation set:
 * <a href="https://github.com/bazaarvoice/jolt#jolt">github.com/bazaarvoice/jolt</a>.
 *
 * <p>All methods accept JSON <em>strings</em> and return JSON <em>strings</em>.
 * Parsing/compilation failures surface as {@link JoltException} with a clear
 * message; callers map that to a DuckDB error (for transforms) or {@code false}
 * (for validators).
 */
public final class JoltEngine {

    private JoltEngine() {}

    /** Raised for malformed input JSON, malformed spec JSON, or a transform failure. */
    public static final class JoltException extends RuntimeException {
        public JoltException(String message, Throwable cause) { super(message, cause); }
        public JoltException(String message) { super(message); }
    }

    /** Parse a JSON string into the Jolt object model, or fail with a clear message. */
    private static Object parseJson(String json, String what) {
        try {
            return JsonUtils.jsonToObject(json);
        } catch (Exception e) {
            throw new JoltException("malformed " + what + " JSON: " + rootMessage(e), e);
        }
    }

    /**
     * Apply a full Jolt spec (a JSON array of operations, a "chainr") to an input
     * JSON document. Returns the transformed document serialized as a JSON string.
     */
    public static String transform(String inputJson, String specJson) {
        Object input = parseJson(inputJson, "input");
        Chainr chainr = compile(specJson);
        Object output;
        try {
            output = chainr.transform(input);
        } catch (Exception e) {
            throw new JoltException("Jolt transform failed: " + rootMessage(e), e);
        }
        return JsonUtils.toJsonString(output);
    }

    /**
     * Apply a single {@code shift} operation. {@code shiftSpec} is the bare shift
     * spec object (not wrapped in the chainr operation envelope).
     */
    public static String shift(String inputJson, String shiftSpec) {
        Object input = parseJson(inputJson, "input");
        Object spec = parseJson(shiftSpec, "shift spec");
        try {
            return JsonUtils.toJsonString(new Shiftr(spec).transform(input));
        } catch (JoltException e) {
            throw e;
        } catch (Exception e) {
            throw new JoltException("malformed shift spec: " + rootMessage(e), e);
        }
    }

    /**
     * Apply a single {@code default} operation. {@code defaultSpec} is the bare
     * default spec object.
     */
    public static String applyDefault(String inputJson, String defaultSpec) {
        Object input = parseJson(inputJson, "input");
        Object spec = parseJson(defaultSpec, "default spec");
        try {
            return JsonUtils.toJsonString(new Defaultr(spec).transform(input));
        } catch (JoltException e) {
            throw e;
        } catch (Exception e) {
            throw new JoltException("malformed default spec: " + rootMessage(e), e);
        }
    }

    /** Compile a full Jolt spec into a {@link Chainr}, or fail with a clear message. */
    public static Chainr compile(String specJson) {
        Object spec;
        try {
            spec = JsonUtils.jsonToObject(specJson);
        } catch (Exception e) {
            throw new JoltException("malformed spec JSON: " + rootMessage(e), e);
        }
        try {
            return Chainr.fromSpec(spec);
        } catch (Exception e) {
            throw new JoltException("invalid Jolt spec: " + rootMessage(e), e);
        }
    }

    /** True when {@code specJson} parses and compiles as a Jolt chainr spec. */
    public static boolean isValidSpec(String specJson) {
        try {
            compile(specJson);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True when {@code json} parses as a JSON document. */
    public static boolean isValidJson(String json) {
        try {
            JsonUtils.jsonToObject(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Build the JSON value for a {@code vgi.example_queries} function tag from a
     * single {@code (sql, description)} example. The result is a JSON array of one
     * {@code {"sql","description"}} object; JSON escaping is handled by json-utils
     * so the (often quote-heavy) Jolt SQL examples are encoded correctly.
     */
    public static String exampleQueriesTag(String sql, String description) {
        java.util.Map<String, Object> ex = new java.util.LinkedHashMap<>();
        ex.put("sql", sql);
        ex.put("description", description);
        return JsonUtils.toJsonString(java.util.List.of(ex));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}
