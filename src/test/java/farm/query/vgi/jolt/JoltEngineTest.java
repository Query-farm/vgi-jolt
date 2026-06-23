package farm.query.vgi.jolt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link JoltEngine}: transform / shift / default / validators.
 *
 * <p>JSON outputs are compared as parsed trees, not strings, because Jolt does not
 * guarantee key order in serialized output.
 */
class JoltEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Assert two JSON strings are structurally equal (key order ignored). */
    private static void assertJsonEquals(String expected, String actual) {
        try {
            JsonNode e = MAPPER.readTree(expected);
            JsonNode a = MAPPER.readTree(actual);
            assertEquals(e, a, () -> "expected " + e + " but got " + a);
        } catch (Exception ex) {
            throw new AssertionError("could not parse JSON: " + ex.getMessage(), ex);
        }
    }

    // ---- jolt_shift --------------------------------------------------------

    @Test void shiftRemapsKey() {
        String out = JoltEngine.shift(
                "{\"rating\":{\"primary\":{\"value\":3}}}",
                "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}");
        assertJsonEquals("{\"Rating\":3}", out);
    }

    // ---- jolt_default ------------------------------------------------------

    @Test void defaultAddsMissingField() {
        String out = JoltEngine.applyDefault(
                "{\"name\":\"widget\"}",
                "{\"inStock\":true,\"qty\":0}");
        assertJsonEquals("{\"name\":\"widget\",\"inStock\":true,\"qty\":0}", out);
    }

    @Test void defaultDoesNotOverwritePresentField() {
        String out = JoltEngine.applyDefault(
                "{\"name\":\"widget\",\"qty\":5}",
                "{\"qty\":0}");
        assertJsonEquals("{\"name\":\"widget\",\"qty\":5}", out);
    }

    // ---- jolt_transform: full chainr (shift + default + remove) -------------

    @Test void transformFullChainr() {
        String spec = "["
                + "{\"operation\":\"shift\",\"spec\":{"
                + "  \"rating\":{\"primary\":{\"value\":\"Rating\"}},"
                + "  \"id\":\"ProductId\"}},"
                + "{\"operation\":\"default\",\"spec\":{\"Reviewed\":true}},"
                + "{\"operation\":\"remove\",\"spec\":{\"ProductId\":\"\"}}"
                + "]";
        String input = "{\"rating\":{\"primary\":{\"value\":4}},\"id\":\"abc\"}";
        String out = JoltEngine.transform(input, spec);
        // shift -> {Rating:4, ProductId:"abc"}; default adds Reviewed; remove drops ProductId.
        assertJsonEquals("{\"Rating\":4,\"Reviewed\":true}", out);
    }

    @Test void transformShiftOnlyChainr() {
        String spec = "[{\"operation\":\"shift\",\"spec\":"
                + "{\"rating\":{\"primary\":{\"value\":\"Rating\"}}}}]";
        String out = JoltEngine.transform("{\"rating\":{\"primary\":{\"value\":3}}}", spec);
        assertJsonEquals("{\"Rating\":3}", out);
    }

    // ---- validators --------------------------------------------------------

    @Test void isValidSpecTrueForCompilableSpec() {
        assertTrue(JoltEngine.isValidSpec(
                "[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]"));
    }

    @Test void isValidSpecFalseForGarbageAndUnknownOperation() {
        assertFalse(JoltEngine.isValidSpec("not json"));
        assertFalse(JoltEngine.isValidSpec(
                "[{\"operation\":\"no-such-op\",\"spec\":{}}]"));
    }

    @Test void jsonValidTrueAndFalse() {
        assertTrue(JoltEngine.isValidJson("{\"a\":1}"));
        assertTrue(JoltEngine.isValidJson("[1,2,3]"));
        assertFalse(JoltEngine.isValidJson("{not valid"));
        assertFalse(JoltEngine.isValidJson(""));
    }

    // ---- error handling ----------------------------------------------------

    @Test void transformMalformedInputThrows() {
        String spec = "[{\"operation\":\"shift\",\"spec\":{\"a\":\"b\"}}]";
        assertThrows(JoltEngine.JoltException.class,
                () -> JoltEngine.transform("{not json", spec));
    }

    @Test void transformMalformedSpecThrows() {
        assertThrows(JoltEngine.JoltException.class,
                () -> JoltEngine.transform("{\"a\":1}", "{not a spec"));
    }

    @Test void transformInvalidOperationThrows() {
        assertThrows(JoltEngine.JoltException.class,
                () -> JoltEngine.transform("{\"a\":1}",
                        "[{\"operation\":\"no-such-op\",\"spec\":{}}]"));
    }
}
