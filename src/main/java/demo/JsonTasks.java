package demo;

import java.util.List;
import java.util.Map;

/**
 * Everyday JSON chores, implemented three times: with the JDK 28 incubator API,
 * with the Jackson tree model and with Jackson data binding into records.
 */
public interface JsonTasks {

    /** Average of properties.periods[].temperature, the example from JEP 540. */
    double averageTemperature(String forecast);

    /** Periods where probabilityOfPrecipitation is JSON null. */
    long unknownPrecipitation(String forecast);

    /** Number of threads per state over all thread containers. */
    Map<String, Long> threadsByState(String threadDump);

    /** Threads that have the optional waitingOn member (only WAITING and TIMED_WAITING threads in Object.wait()). */
    long threadsWithWaitingOn(String threadDump);

    /** Containers whose parent is JSON null. */
    long rootContainers(String threadDump);

    /** {"providers":[...],"count":N,"enabled":true} */
    String providersConfig(List<String> providers);
}
