package farm.query.vgi.jolt;

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;

import java.nio.charset.StandardCharsets;

/**
 * Resolves the polymorphic JSON argument shared by every function: a
 * {@code VARCHAR} carrying the JSON document <em>text</em> directly, or a
 * {@code BLOB}/{@code BINARY} carrying the document <em>bytes</em>.
 *
 * <p>A VARCHAR here is the literal JSON content (not a file path) — JSON
 * documents travel inline. NULL input yields {@code null} (the caller maps that
 * to a NULL result).
 */
public final class JsonInput {

    private JsonInput() {}

    /** Decode one cell of an any-typed scalar vector into a JSON string, or null. */
    public static String at(FieldVector in, int row) {
        if (in.isNull(row)) {
            return null;
        }
        if (in instanceof VarCharVector s) {
            Object o = s.getObject(row);
            return o == null ? null : o.toString();
        }
        if (in instanceof VarBinaryVector b) {
            byte[] bytes = b.get(row);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
        Object o = in.getObject(row);
        return o == null ? null : o.toString();
    }
}
