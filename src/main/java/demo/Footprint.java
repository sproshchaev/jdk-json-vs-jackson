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
 * What a one-shot program pays for each option: extra jars on the class path,
 * classes loaded before the answer is printed, and wall time of a fresh JVM.
 * Timings depend on the machine, so they are printed but never compared.
 *
 * <pre>
 *   java demo.Footprint [runs]     # default 15 runs per variant
 * </pre>
 */
public final class Footprint {

    private static final String JDK_CP = "target/classes";
    private static final String JACKSON_CP = JDK_CP + File.pathSeparator + "target/jackson/*";

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

        Map<String, String> classPath = new LinkedHashMap<>();
        classPath.put("jdk", JDK_CP);
        classPath.put("jackson-tree", JACKSON_CP);
        classPath.put("jackson-binding", JACKSON_CP);

        Map<String, List<Long>> times = new LinkedHashMap<>();
        classPath.keySet().forEach(impl -> times.put(impl, new ArrayList<>()));
        // interleave the variants so that drift and disk cache hit all of them alike
        for (int i = 0; i < runs; i++) {
            for (var e : classPath.entrySet()) {
                times.get(e.getKey()).add(wallMillis(e.getValue(), e.getKey()));
            }
        }
        for (var e : classPath.entrySet()) {
            List<Long> t = times.get(e.getKey());
            t.sort(null);
            System.out.printf("%-16s loaded classes %5d   median wall %4d ms of %d runs%n",
                    e.getKey(), loadedClasses(e.getValue(), e.getKey()), t.get(runs / 2), runs);
        }
    }

    private static Process start(String cp, String impl, ProcessBuilder.Redirect out, String... extra)
            throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>(List.of(java, "--add-modules", "jdk.incubator.json"));
        cmd.addAll(List.of(extra));
        cmd.addAll(List.of("-cp", cp, "demo.OneShot", impl));
        return new ProcessBuilder(cmd).redirectOutput(out).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static void await(Process p, String impl) throws InterruptedException {
        if (p.waitFor() != 0) {
            throw new IllegalStateException(impl + " failed");
        }
    }

    private static long loadedClasses(String cp, String impl) throws IOException, InterruptedException {
        Process p = start(cp, impl, ProcessBuilder.Redirect.PIPE, "-Xlog:class+load=info:stdout");
        long n = p.inputReader().lines().filter(line -> line.contains("[class,load]")).count();
        await(p, impl);
        return n;
    }

    private static long wallMillis(String cp, String impl) throws IOException, InterruptedException {
        long start = System.nanoTime();
        await(start(cp, impl, ProcessBuilder.Redirect.DISCARD), impl);
        return (System.nanoTime() - start) / 1_000_000;
    }
}
