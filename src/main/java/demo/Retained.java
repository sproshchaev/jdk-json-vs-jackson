package demo;

import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;
import tools.jackson.databind.JsonNode;

/**
 * Heap kept alive by a parsed tree of the large file, measured after full GC,
 * right after parsing and again after every value has been read once.
 * The JDK tree computes strings and numbers lazily, so the two can differ.
 * Allocation per operation (JMH gc.alloc.rate.norm) is a different metric.
 */
public final class Retained {

    public static void main(String[] args) throws IOException {
        String doc = Files.readString(Path.of(DataFiles.LARGE));
        System.out.printf("document: %d KB of text%n", doc.length() / 1024);
        report("jdk", doc, Json::parse, (JsonValue v) -> Walk.jdk(v));
        report("jackson-tree", doc, JacksonTreeTasks.MAPPER::readTree, (JsonNode n) -> Walk.jackson(n));
    }

    private static <T> void report(String name, String doc, Function<String, T> parser, Function<T, Long> walk) {
        long parsed = Long.MAX_VALUE;
        long walked = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long before = usedAfterGc();
            T tree = parser.apply(doc);
            parsed = Math.min(parsed, usedAfterGc() - before);
            walk.apply(tree);
            walked = Math.min(walked, usedAfterGc() - before);
            Reference.reachabilityFence(tree);
        }
        System.out.printf("%-14s retained tree %6d KB after parse, %6d KB after reading every value%n",
                name, parsed / 1024, walked / 1024);
    }

    private static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return rt.totalMemory() - rt.freeMemory();
    }
}
