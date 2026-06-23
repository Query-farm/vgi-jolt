package farm.query.vgi.jolt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.util.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct-vector coverage for jolt_transform, jolt_shift, jolt_default,
 * is_valid_jolt_spec, json_valid — driving each scalar's {@code compute} exactly
 * as the VGI runtime would.
 */
class ScalarFunctionsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RootAllocator alloc = new RootAllocator();

    @AfterEach void tearDown() { alloc.close(); }

    private VarCharVector vec(String name, String... values) {
        VarCharVector v = new VarCharVector(name, alloc);
        v.allocateNew();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) v.setNull(i);
            else v.setSafe(i, new Text(values[i]));
        }
        v.setValueCount(values.length);
        return v;
    }

    private VarCharVector out() {
        VarCharVector v = new VarCharVector("out", alloc);
        v.allocateNew();
        return v;
    }

    private String at(VarCharVector v, int i) {
        return v.isNull(i) ? null : v.getObject(i).toString();
    }

    private void assertJsonEquals(String expected, String actual) {
        try {
            assertEquals(MAPPER.readTree(expected), MAPPER.readTree(actual));
        } catch (Exception e) {
            throw new AssertionError("could not parse JSON: " + e.getMessage(), e);
        }
    }

    // ---- jolt_transform ----------------------------------------------------

    @Test void transformRemapsAndHandlesNull() {
        String spec = "[{\"operation\":\"shift\",\"spec\":"
                + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]";
        try (VarCharVector in = vec("input_json",
                "{\"rating\":{\"primary\":{\"value\":3}}}", null);
             VarCharVector out = out()) {
            new TransformFunction().compute(in, spec, out);
            assertJsonEquals("{\"Rating\":3}", at(out, 0));
            assertNull(at(out, 1), "NULL input -> NULL");
        }
    }

    @Test void transformMalformedSpecErrors() {
        try (VarCharVector in = vec("input_json", "{\"a\":1}"); VarCharVector out = out()) {
            assertThrows(RuntimeException.class,
                    () -> new TransformFunction().compute(in, "{not a spec", out));
        }
    }

    @Test void transformMalformedInputErrors() {
        String spec = "[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]";
        try (VarCharVector in = vec("input_json", "{not json"); VarCharVector out = out()) {
            assertThrows(RuntimeException.class,
                    () -> new TransformFunction().compute(in, spec, out));
        }
    }

    // ---- jolt_shift --------------------------------------------------------

    @Test void shiftConvenience() {
        try (VarCharVector in = vec("input_json", "{\"rating\":{\"primary\":{\"value\":3}}}");
             VarCharVector out = out()) {
            new ShiftFunction().compute(in,
                    "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}", out);
            assertJsonEquals("{\"Rating\":3}", at(out, 0));
        }
    }

    // ---- jolt_default ------------------------------------------------------

    @Test void defaultConvenience() {
        try (VarCharVector in = vec("input_json", "{\"name\":\"widget\"}");
             VarCharVector out = out()) {
            new DefaultFunction().compute(in, "{\"inStock\":true}", out);
            assertJsonEquals("{\"name\":\"widget\",\"inStock\":true}", at(out, 0));
        }
    }

    // ---- is_valid_jolt_spec ------------------------------------------------

    @Test void isValidSpecTrueFalseNull() {
        try (VarCharVector in = vec("spec_json",
                "[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]",
                "[{\"operation\":\"no-such-op\",\"spec\":{}}]",
                "not json",
                null);
             BitVector out = new BitVector("out", alloc)) {
            out.allocateNew();
            new IsValidSpecFunction().compute(in, out);
            assertTrue(out.get(0) != 0, "valid chainr spec");
            assertFalse(out.get(1) != 0, "unknown operation -> false");
            assertFalse(out.get(2) != 0, "garbage -> false");
            assertTrue(out.isNull(3), "NULL -> NULL");
        }
    }

    // ---- json_valid --------------------------------------------------------

    @Test void jsonValidTrueFalseNull() {
        try (VarCharVector in = vec("input_json",
                "{\"a\":1}", "[1,2,3]", "{not valid", null);
             BitVector out = new BitVector("out", alloc)) {
            out.allocateNew();
            new JsonValidFunction().compute(in, out);
            assertTrue(out.get(0) != 0);
            assertTrue(out.get(1) != 0);
            assertFalse(out.get(2) != 0);
            assertTrue(out.isNull(3), "NULL -> NULL");
        }
    }
}
