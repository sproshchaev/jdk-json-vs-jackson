package demo.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import demo.DataFiles;
import demo.JacksonBindingTasks;
import demo.JacksonTreeTasks;
import demo.JdkTasks;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** A whole task: parse, navigate, compute. Binding is in the race here. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = "--add-modules=jdk.incubator.json")
public class TaskBench {

    private final JdkTasks jdk = new JdkTasks();
    private final JacksonTreeTasks tree = new JacksonTreeTasks();
    private final JacksonBindingTasks binding = new JacksonBindingTasks();

    private String forecast;
    private String dump;

    @Setup
    public void load() throws IOException {
        forecast = Files.readString(Path.of(DataFiles.FORECAST));
        dump = Files.readString(Path.of(DataFiles.MEDIUM));
    }

    @Benchmark
    public double avgTemperatureJdk() {
        return jdk.averageTemperature(forecast);
    }

    @Benchmark
    public double avgTemperatureJacksonTree() {
        return tree.averageTemperature(forecast);
    }

    @Benchmark
    public double avgTemperatureJacksonBinding() {
        return binding.averageTemperature(forecast);
    }

    @Benchmark
    public Map<String, Long> threadsByStateJdk() {
        return jdk.threadsByState(dump);
    }

    @Benchmark
    public Map<String, Long> threadsByStateJacksonTree() {
        return tree.threadsByState(dump);
    }

    @Benchmark
    public Map<String, Long> threadsByStateJacksonBinding() {
        return binding.threadsByState(dump);
    }
}
