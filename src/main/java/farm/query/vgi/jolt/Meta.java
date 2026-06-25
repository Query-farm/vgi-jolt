package farm.query.vgi.jolt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile (0.26.0) expects on <em>every</em> function.
 *
 * <p>Each function surfaces these tags in its {@link
 * farm.query.vgi.function.FunctionMetadata}:
 * <ul>
 *   <li>{@code vgi.title} (VGI124)      &mdash; human-friendly display name.</li>
 *   <li>{@code vgi.doc_llm} (VGI112)    &mdash; a Markdown narrative aimed at LLMs/agents.</li>
 *   <li>{@code vgi.doc_md} (VGI113)     &mdash; a Markdown narrative for human docs.</li>
 *   <li>{@code vgi.keywords} (VGI126)   &mdash; a JSON array of search terms/synonyms.</li>
 * </ul>
 *
 * <p>Per-object {@code vgi.source_url} is intentionally omitted (VGI139): the
 * source link is carried once on the catalog object.
 */
public final class Meta {

    private Meta() {}

    /**
     * Build the four standard per-object discovery/description tags as a mutable,
     * insertion-ordered map (callers may add table/example-specific tags).
     *
     * <p>{@code keywords} is supplied as a comma-separated convenience string and
     * serialized as a {@code vgi.keywords} JSON array of strings (VGI138).
     * Per-object {@code vgi.source_url} is intentionally not emitted (VGI139).
     *
     * @param title    human display name (must not normalize-equal the machine name)
     * @param docLlm   Markdown narrative for LLMs/agents (VGI112)
     * @param docMd    Markdown narrative for human docs (VGI113) — distinct from docLlm
     * @param keywords comma-separated search terms/synonyms (VGI126), emitted as a JSON array
     * @param fileName implementing source file (retained for call-site clarity; not emitted)
     */
    public static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String fileName) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", title);
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", keywordsToJsonArray(keywords));
        return tags;
    }

    /**
     * Render a comma-separated keyword string as a JSON array of strings, e.g.
     * {@code "a, b"} &rarr; {@code ["a","b"]} (VGI138).
     */
    private static String keywordsToJsonArray(String keywords) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String raw : keywords.split(",")) {
            String kw = raw.trim();
            if (kw.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"')
                    .append(kw.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return sb.append(']').toString();
    }
}
