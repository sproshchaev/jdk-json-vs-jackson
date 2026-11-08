package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;

/**
 * Turns target/jmh.json into a short table. Reads the JMH report with the
 * JDK API itself: a typical "read a file, print a summary" job.
 */
public final class Report {

    public static void main(String[] args) throws IOException {
        JsonValue results = Json.parse(Files.readString(Path.of(args.length > 0 ? args[0] : "target/jmh.json")));
        System.out.printf("%-46s %-34s %12s %12s%n", "benchmark", "file", "us/op", "KB/op");
        for (JsonValue r : results.asList()) {
            String name = r.get("benchmark").asString().replace("demo.bench.", "");
            String file = r.tryGet("params")
                    .map(p -> Path.of(p.get("file").asString()).getFileName().toString())
                    .orElse("-");
            double score = r.get("primaryMetric").get("score").asDouble();
            double alloc = r.get("secondaryMetrics").tryGet("gc.alloc.rate.norm")
                    .map(m -> m.get("score").asDouble())
                    .orElse(Double.NaN);
            System.out.printf(Locale.ROOT, "%-46s %-34s %12.1f %12.1f%n", name, file, score, alloc / 1024);
        }
    }
}
