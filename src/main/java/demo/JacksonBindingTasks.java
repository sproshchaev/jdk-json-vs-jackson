package demo;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson data binding into records, the part JEP 540 leaves out on purpose.
 * Only the fields the task needs are declared, the rest is ignored.
 */
public final class JacksonBindingTasks implements JsonTasks {

    // Jackson 3 ignores unknown properties by default (FAIL_ON_UNKNOWN_PROPERTIES is off)
    static final JsonMapper MAPPER = JsonMapper.builder().build();

    record Forecast(Properties properties) {}
    record Properties(List<Period> periods) {}
    record Period(int temperature, Integer probabilityOfPrecipitation) {}

    record Dump(ThreadDump threadDump) {}
    record ThreadDump(List<Container> threadContainers) {}
    record Container(String parent, List<JThread> threads) {}
    record JThread(String state, String waitingOn) {}

    record ProvidersConfig(List<String> providers, int count, boolean enabled) {}

    @Override
    public double averageTemperature(String forecast) {
        return periods(forecast).mapToInt(Period::temperature).average().orElseThrow();
    }

    @Override
    public long unknownPrecipitation(String forecast) {
        return periods(forecast).filter(p -> p.probabilityOfPrecipitation() == null).count();
    }

    @Override
    public Map<String, Long> threadsByState(String threadDump) {
        return threads(threadDump).collect(groupingBy(JThread::state, TreeMap::new, counting()));
    }

    @Override
    public long threadsWithWaitingOn(String threadDump) {
        // a record cannot tell an absent member from "waitingOn": null
        return threads(threadDump).map(JThread::waitingOn).filter(Objects::nonNull).count();
    }

    @Override
    public long rootContainers(String threadDump) {
        return containers(threadDump).filter(c -> c.parent() == null).count();
    }

    @Override
    public String providersConfig(List<String> providers) {
        return MAPPER.writeValueAsString(new ProvidersConfig(providers, providers.size(), true));
    }

    private static Stream<Period> periods(String forecast) {
        return MAPPER.readValue(forecast, Forecast.class).properties().periods().stream();
    }

    private static Stream<Container> containers(String threadDump) {
        return MAPPER.readValue(threadDump, Dump.class).threadDump().threadContainers().stream();
    }

    private static Stream<JThread> threads(String threadDump) {
        return containers(threadDump).flatMap(c -> c.threads().stream());
    }
}
