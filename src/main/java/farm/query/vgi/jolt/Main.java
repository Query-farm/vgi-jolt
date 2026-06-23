package farm.query.vgi.jolt;

import farm.query.vgi.Worker;

/**
 * VGI worker entry point for declarative JSON&rarr;JSON structural transformation
 * via <a href="https://github.com/bazaarvoice/jolt">Bazaarvoice Jolt</a>
 * (Apache-2.0).
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * ATTACH 'jolt' (TYPE vgi, LOCATION 'java -jar vgi-jolt-all.jar');
 * SELECT jolt.jolt_transform('{"rating":{"primary":{"value":3}}}',
 *   '[{"operation":"shift","spec":{"rating":{"primary":{"value":"Rating"}}}}]');
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_JOLT_GIT_COMMIT") != null
                    ? System.getenv("VGI_JOLT_GIT_COMMIT") : "unknown";

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("jolt")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("Declarative JSON->JSON structural transformation via Bazaarvoice Jolt")
                .registerScalar(new TransformFunction())
                .registerScalar(new ShiftFunction())
                .registerScalar(new DefaultFunction())
                .registerScalar(new IsValidSpecFunction())
                .registerScalar(new JsonValidFunction());
    }

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        buildWorker().runFromArgs(args);
    }
}
