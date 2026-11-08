package demo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * End-to-end cold start of a one-shot program: a fresh JVM reads a file,
 * parses it, prints one number and exits. Reports extra jars on the class path,
 * classes loaded and median wall time. Only the JDK variant gets
 * --add-modules jdk.incubator.json, Jackson runs without it.
 * Timings depend on the machine, so they are printed but never compared.
 *
 * <pre>
 *   java demo.Footprint [runs]     # default 15 runs per variant
 * </pre>
 */
public final class Footprint {

    /** Everything after "java" on the command line. */
    record Variant(String name, List<String> args) {}

    private static final String JACKSON_CP = "target/classes" + File.pathSeparator + "target/jackson/*";

    private static final List<Variant> VARIANTS = List.of(
            new Variant("jdk", List.of("--add-modules", "jdk.incubator.json",
                    "-cp", "target/classes", "demo.OneShotJdk")),
            new Variant("jackson-tree", List.of("-cp", JACKSON_CP, "demo.OneShotJackson", "tree")),
            new Variant("jackson-binding", List.of("-cp", JACKSON_CP, "demo.OneShotJackson", "binding")));

    public static void main(String[] args) throws IOException, InterruptedException {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 15;

        List<Path> jars;
        try (Stream<Path> s = Files.list(Path.of("target/jackson"))) {
            jars = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
        long bytes = 0;
        for (Path jar : jars) {
            bytes += Files.size(jar);
        }
        System.out.printf("jackson jars: %d files, %d KB%n", jars.size(), bytes / 1024);
        jars.forEach(jar -> System.out.printf("  %s%n", jar.getFileName()));

        Map<Variant, List<Long>> times = new LinkedHashMap<>();
        VARIANTS.forEach(v -> times.put(v, new ArrayList<>()));
        // one untimed pass warms the OS file cache for every variant
        for (Variant v : VARIANTS) {
            wallMillis(v);
        }
        // interleave the variants and rotate who goes first, so drift hits all of them alike
        for (int i = 0; i < runs; i++) {
            for (int k = 0; k < VARIANTS.size(); k++) {
                Variant v = VARIANTS.get((i + k) % VARIANTS.size());
                times.get(v).add(wallMillis(v));
            }
        }
        for (Variant v : VARIANTS) {
            List<Long> t = times.get(v);
            t.sort(null);
            System.out.printf("%-16s loaded classes %5d   median wall %4d ms of %d runs%n",
                    v.name(), loadedClasses(v), t.get(runs / 2), runs);
        }
    }

    private static Process start(Variant v, ProcessBuilder.Redirect out, String... extra) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>(List.of(java));
        cmd.addAll(List.of(extra));
        cmd.addAll(v.args());
        return new ProcessBuilder(cmd).redirectOutput(out).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static void await(Process p, Variant v) throws InterruptedException {
        if (p.waitFor() != 0) {
            throw new IllegalStateException(v.name() + " failed");
        }
    }

    private static long loadedClasses(Variant v) throws IOException, InterruptedException {
        Process p = start(v, ProcessBuilder.Redirect.PIPE, "-Xlog:class+load=info:stdout");
        long n = p.inputReader().lines().filter(line -> line.contains("[class,load]")).count();
        await(p, v);
        return n;
    }

    private static long wallMillis(Variant v) throws IOException, InterruptedException {
        long start = System.nanoTime();
        await(start(v, ProcessBuilder.Redirect.DISCARD), v);
        return (System.nanoTime() - start) / 1_000_000;
    }
}
