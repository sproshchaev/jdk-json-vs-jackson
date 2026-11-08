package demo;

import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;
import tools.jackson.databind.JsonNode;

/**
 * Touches every value of a tree: string contents and numbers included.
 * The JDK tree computes strings and numbers lazily, so "parse only"
 * and "parse and read everything" are different amounts of work.
 */
public final class Walk {

    private Walk() {
    }

    public static long jdk(JsonValue v) {
        return switch (v) {
            case JsonObject o -> o.asMap().entrySet().stream()
                    .mapToLong(e -> e.getKey().length() + jdk(e.getValue())).sum();
            case JsonArray a -> a.asList().stream().mapToLong(Walk::jdk).sum();
            case JsonString s -> s.asString().length();
            case JsonNumber n -> (long) n.asDouble();
            case JsonBoolean b -> b.asBoolean() ? 1 : 0;
            case JsonNull ignored -> 0;
        };
    }

    public static long jackson(JsonNode n) {
        if (n.isObject()) {
            return n.properties().stream()
                    .mapToLong(e -> e.getKey().length() + jackson(e.getValue())).sum();
        }
        if (n.isArray()) {
            return n.valueStream().mapToLong(Walk::jackson).sum();
        }
        if (n.isString()) {
            return n.asString().length();
        }
        if (n.isNumber()) {
            return (long) n.doubleValue();
        }
        return n.isBoolean() && n.booleanValue() ? 1 : 0;
    }
}
