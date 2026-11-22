package software.spool.cli.acceptance;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compiles a class of the user's project outside the test classpath, so the CLI only finds it if told where. */
final class ProjectFixture {
    private static final String RUNNING_TOTAL = """
            package fixture;

            import software.spool.core.model.vo.PartitionKey;
            import software.spool.mounter.api.model.GenericRecord;
            import software.spool.mounter.api.port.IncrementalMountAggregator;
            import software.spool.mounter.api.port.PartitionedRecord;

            public class RunningTotal implements IncrementalMountAggregator<RunningTotal.Total, RunningTotal.Total> {
                public record Total(long value) {}
                public PartitionKey groupOf(PartitionedRecord<GenericRecord> record) {
                    return PartitionKey.ofEntriesWithoutDate().with("scope", "all").build();
                }
                public Total initial() { return new Total(0); }
                public Total add(Total state, GenericRecord record) { return new Total(state.value() + 1); }
                public Total result(Total state) { return state; }
                public Class<Total> stateType() { return Total.class; }
            }
            """;

    private ProjectFixture() {}

    static void compileRunningTotalInto(Path classes) throws IOException {
        Path sources = Files.createTempDirectory("spool-cli-sources");
        Path source = Files.writeString(Files.createDirectories(sources.resolve("fixture")).resolve("RunningTotal.java"),
                RUNNING_TOTAL);
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(), source.toString());
        if (result != 0) throw new IllegalStateException("The fixture class did not compile");
    }

    static String descriptorUsing(String aggregator) {
        return """
                infrastructure:
                  bus: {kind: EVENT_BUS, type: IN_MEMORY}
                  lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: target/cli-test/lake}}
                  mart: {kind: DATA_MART, type: FILE_SYSTEM, configuration: {path: target/cli-test/mart}}
                  quarantine: {kind: QUARANTINE_REPOSITORY, type: FILE_SYSTEM, configuration: {path: target/cli-test/quarantine}}
                modules:
                  - mounter:
                      id: running-total
                      type: POLL
                      schedule: {every: 1m}
                      mode: AGGREGATION
                      aggregator: %s
                      read: {partitions: ["source=payments"]}
                      write: {dataMart: running-total, partitioning: {type: SAME_AS_SOURCE}}
                """.formatted(aggregator);
    }
}
