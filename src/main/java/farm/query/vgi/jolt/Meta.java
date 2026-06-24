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
 *   <li>{@code vgi.keywords} (VGI126)   &mdash; comma-separated search terms/synonyms.</li>
 *   <li>{@code vgi.source_url} (VGI128) &mdash; link to the implementing source file.</li>
 * </ul>
 *
 * <p>{@link #sourceUrl(String)} builds the canonical GitHub blob URL (pinned to
 * {@code main}) so every object points at exactly where it is implemented.
 */
public final class Meta {

    private Meta() {}

    /** Base GitHub blob URL for source files in this repo (pinned to {@code main}). */
    private static final String SOURCE_BASE =
            "https://github.com/Query-farm/vgi-jolt/blob/main/src/main/java/farm/query/vgi/jolt";

    /**
     * Build the {@code vgi.source_url} for a Java source file under
     * {@code src/main/java/farm/query/vgi/jolt}, e.g.
     * {@code sourceUrl("TransformFunction.java")}.
     */
    public static String sourceUrl(String fileName) {
        return SOURCE_BASE + "/" + fileName;
    }

    /**
     * Build the five standard per-object discovery/description tags as a mutable,
     * insertion-ordered map (callers may add table/example-specific tags).
     *
     * @param title    human display name (must not normalize-equal the machine name)
     * @param docLlm   Markdown narrative for LLMs/agents (VGI112)
     * @param docMd    Markdown narrative for human docs (VGI113) — distinct from docLlm
     * @param keywords comma-separated search terms/synonyms (VGI126)
     * @param fileName implementing source file under the jolt package (VGI128)
     */
    public static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String fileName) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", title);
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", keywords);
        tags.put("vgi.source_url", sourceUrl(fileName));
        return tags;
    }
}
