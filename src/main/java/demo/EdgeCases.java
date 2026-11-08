package demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Feeds the same input to both parsers with default settings and prints what
 * each of them returns, or the simple name of the exception it throws.
 */
public final class EdgeCases {

    record Case(String name, String json, Function<JsonValue, Object> jdk, Function<JsonNode, Object> jackson) {}

    public static void main(String[] args) {
        List<Case> cases = List.of(
                new Case("duplicate key", "{\"a\":1,\"a\":2}",
                        v -> v.get("a").asInt(), n -> n.get("a").asInt()),
                new Case("trailing comma", "[1,2,]",
                        v -> v.asList().size(), n -> n.size()),
                new Case("comment", "{\"a\":1 /* note */}",
                        v -> v.get("a").asInt(), n -> n.get("a").asInt()),
                new Case("single quotes", "{'a':1}",
                        v -> v.get("a").asInt(), n -> n.get("a").asInt()),
                new Case("NaN", "[NaN]",
                        v -> v.get(0).asDouble(), n -> n.get(0).asDouble()),
                new Case("leading zero", "[01]",
                        v -> v.get(0).asInt(), n -> n.get(0).asInt()),
                new Case("BOM before document", "\uFEFF{\"a\":1}",
                        v -> v.get("a").asInt(), n -> n.get("a").asInt()),
                new Case("second document after first", "{\"a\":1} {\"b\":2}",
                        v -> v.get("a").asInt(), n -> n.get("a").asInt()),
                new Case("scalar at root", "42",
                        JsonValue::asInt, JsonNode::asInt),
                new Case("2^64 read as long", "{\"id\":18446744073709551616}",
                        v -> v.get("id").asLong(), n -> n.get("id").asLong()),
                new Case("2^64 written back", "{\"id\":18446744073709551616}",
                        v -> v.get("id").toString(), n -> n.get("id").toString()),
                new Case("3000000000 read as int", "{\"n\":3000000000}",
                        v -> v.get("n").asInt(), n -> n.get("n").asInt()),
                new Case("1.5 read as int", "{\"n\":1.5}",
                        v -> v.get("n").asInt(), n -> n.get("n").asInt()),
                new Case("\"42\" read as int", "{\"n\":\"42\"}",
                        v -> v.get("n").asInt(), n -> n.get("n").asInt()),
                new Case("long fraction written back", "{\"x\":0.1000000000000000055511151231257827}",
                        v -> v.get("x").toString(), n -> n.get("x").toString()),
                new Case("1e400 read as double", "{\"x\":1e400}",
                        v -> v.get("x").asDouble(), n -> n.get("x").asDouble()),
                new Case("missing member as int", "{\"a\":1}",
                        v -> v.get("b").asInt(), n -> n.path("b").asInt()),
                new Case("null read as string", "{\"p\":null}",
                        v -> v.get("p").asString(), n -> n.get("p").asString()),
                new Case("lone surrogate \\ud800", "\"\\ud800\"",
                        v -> v.asString().length(), n -> n.asString().length()),
                new Case("2000-digit number as double", "[" + "9".repeat(2_000) + "]",
                        v -> v.get(0).asDouble(), n -> n.get(0).asDouble()),
                new Case("nesting depth 2000", nested(2_000),
                        v -> "parsed", n -> "parsed"),
                new Case("nesting depth 100000", nested(100_000),
                        v -> "parsed", n -> "parsed"));

        System.out.println("== parsing, default settings ==");
        System.out.printf("%-30s | %-34s | %s%n", "case", "JDK 28 jdk.incubator.json", "Jackson 3 tree");
        for (Case c : cases) {
            System.out.printf("%-30s | %-34s | %s%n", c.name(),
                    outcome(() -> c.jdk().apply(Json.parse(c.json()))),
                    outcome(() -> c.jackson().apply(JacksonTreeTasks.MAPPER.readTree(c.json()))));
        }

        System.out.println();
        System.out.println("== writing ==");
        String tricky = "quote \" slash / newline \n tag </script> u2028 \u2028 emoji \uD83D\uDE00";
        System.out.println("JDK     string: " + JsonString.of(tricky));
        ObjectNode holder = JacksonTreeTasks.MAPPER.createObjectNode().put("s", tricky);
        System.out.println("Jackson string: " + holder.get("s"));

        var ordered = new LinkedHashMap<String, JsonValue>();
        List<String> keys = List.of("zeta", "alpha", "mid");
        for (String k : keys) {
            ordered.put(k, JsonString.of(k));
        }
        System.out.println("JDK     key order from LinkedHashMap: " + JsonObject.of(ordered));
        ObjectNode jo = JacksonTreeTasks.MAPPER.createObjectNode();
        keys.forEach(k -> jo.put(k, k));
        System.out.println("Jackson key order from put():         " + JacksonTreeTasks.MAPPER.writeValueAsString(jo));

        JsonValue parsed = Json.parse("{\"b\":1,\"a\":2}");
        System.out.println("JDK     parse then toString keeps order: " + parsed);
        System.out.println("JDK     asMap() type: " + simpleType(parsed.asMap()));
    }

    private static String nested(int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }

    private static String simpleType(Map<?, ?> map) {
        try {
            map.clear();
            return "modifiable";
        } catch (UnsupportedOperationException e) {
            return "unmodifiable";
        }
    }

    private static String outcome(java.util.function.Supplier<Object> probe) {
        try {
            return "OK " + probe.get();
        } catch (Throwable t) {
            return "ERR " + t.getClass().getSimpleName();
        }
    }
}
