package demo;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Jackson 3 tree model with default settings: the closest analogue of the JDK API. */
public final class JacksonTreeTasks implements JsonTasks {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public double averageTemperature(String forecast) {
        return periods(forecast)
                .mapToInt(p -> p.get("temperature").intValue())
                .average().orElseThrow();
    }

    @Override
    public long unknownPrecipitation(String forecast) {
        return periods(forecast)
                .filter(p -> p.get("probabilityOfPrecipitation").isNull())
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
                .filter(t -> t.has("waitingOn"))
                .count();
    }

    @Override
    public long rootContainers(String threadDump) {
        return containers(threadDump)
                .filter(c -> c.get("parent").isNull())
                .count();
    }

    @Override
    public String providersConfig(List<String> providers) {
        ObjectNode root = MAPPER.createObjectNode();
        providers.forEach(root.putArray("providers")::add);
        root.put("count", providers.size());
        root.put("enabled", true);
        return MAPPER.writeValueAsString(root);
    }

    private static Stream<JsonNode> periods(String forecast) {
        return MAPPER.readTree(forecast).get("properties").get("periods").valueStream();
    }

    private static Stream<JsonNode> containers(String threadDump) {
        return MAPPER.readTree(threadDump).get("threadDump").get("threadContainers").valueStream();
    }

    private static Stream<JsonNode> threads(String threadDump) {
        return containers(threadDump).flatMap(c -> c.get("threads").valueStream());
    }
}
