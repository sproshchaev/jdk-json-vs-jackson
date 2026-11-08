package demo.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import demo.DataFiles;
import demo.JacksonTreeTasks;
import demo.Walk;
import tools.jackson.databind.JsonNode;

/**
 * Text already in memory. "Parse" builds the tree only; "read all" also touches
 * every string and number, which matters because the JDK tree is lazy.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = "--add-modules=jdk.incubator.json")
public class ParseBench {

    @Param({DataFiles.FORECAST, DataFiles.MEDIUM, DataFiles.LARGE})
    public String file;

    private String doc;

    @Setup
    public void load() throws IOException {
        doc = Files.readString(Path.of(file));
    }

    @Benchmark
    public JsonValue jdk() {
        return Json.parse(doc);
    }

    @Benchmark
    public JsonNode jacksonTree() {
        return JacksonTreeTasks.MAPPER.readTree(doc);
    }

    @Benchmark
    public long jdkReadAll() {
        return Walk.jdk(Json.parse(doc));
    }

    @Benchmark
    public long jacksonTreeReadAll() {
        return Walk.jackson(JacksonTreeTasks.MAPPER.readTree(doc));
    }
}
