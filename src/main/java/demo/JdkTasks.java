package demo;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

/** The JDK 28 incubator API, no external dependencies. */
public final class JdkTasks implements JsonTasks {

    @Override
    public double averageTemperature(String forecast) {
        return periods(forecast)
                .mapToInt(p -> p.get("temperature").asInt())
                .average().orElseThrow();
    }

    @Override
    public long unknownPrecipitation(String forecast) {
        return periods(forecast)
                .filter(p -> p.get("probabilityOfPrecipitation").tryValue().isEmpty())
                .count();
    }

    @Override
    public Map<String, Long> threadsByState(String threadDump) {
        return threads(threadDump)
                .collect(groupingBy(t -> t.get("state").asString(), TreeMap::new, counting()));
    }

    @Override
    public long threadsWaitingOnMonitor(String threadDump) {
        return threads(threadDump)
                .filter(t -> t.tryGet("waitingOn").isPresent())
                .count();
    }

    @Override
    public long rootContainers(String threadDump) {
        return containers(threadDump)
                .filter(c -> c.get("parent").tryValue().isEmpty())
                .count();
    }

    @Override
    public String providersConfig(List<String> providers) {
        var root = new LinkedHashMap<String, JsonValue>();
        root.put("providers", JsonArray.of(providers.stream().map(JsonString::of).toList()));
        root.put("count", JsonNumber.of(providers.size()));
        root.put("enabled", JsonBoolean.of(true));
        return JsonObject.of(root).toString();
    }

    private static Stream<JsonValue> periods(String forecast) {
        return Json.parse(forecast).get("properties").get("periods").asList().stream();
    }

    private static Stream<JsonValue> containers(String threadDump) {
        return Json.parse(threadDump).get("threadDump").get("threadContainers").asList().stream();
    }

    private static Stream<JsonValue> threads(String threadDump) {
        return containers(threadDump).flatMap(c -> c.get("threads").asList().stream());
    }
}
