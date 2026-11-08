package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Runs every task with all three implementations on the same files and prints
 * the answers side by side, with a last line saying whether they all agree.
 * run.sh compares this output with expected.txt.
 */
public final class Compare {

    public static void main(String[] args) throws IOException {
        String forecast = Files.readString(Path.of(DataFiles.FORECAST));
        String dump = Files.readString(Path.of(DataFiles.MEDIUM));
        List<String> providers = List.of("SUN", "SunRsaSign", "SunEC");

        Map<String, JsonTasks> impls = new LinkedHashMap<>();
        impls.put("jdk", new JdkTasks());
        impls.put("jackson-tree", new JacksonTreeTasks());
        impls.put("jackson-binding", new JacksonBindingTasks());

        Map<String, Function<JsonTasks, String>> tasks = new LinkedHashMap<>();
        tasks.put("averageTemperature", t -> String.format(Locale.ROOT, "%.4f", t.averageTemperature(forecast)));
        tasks.put("unknownPrecipitation", t -> String.valueOf(t.unknownPrecipitation(forecast)));
        tasks.put("threadsByState", t -> String.valueOf(t.threadsByState(dump)));
        tasks.put("threadsWaitingOnMonitor", t -> String.valueOf(t.threadsWaitingOnMonitor(dump)));
        tasks.put("rootContainers", t -> String.valueOf(t.rootContainers(dump)));
        tasks.put("providersConfig", t -> t.providersConfig(providers));

        boolean same = true;
        for (var task : tasks.entrySet()) {
            String reference = null;
            for (var impl : impls.entrySet()) {
                String answer = task.getValue().apply(impl.getValue());
                System.out.printf("%-24s %-16s %s%n", task.getKey(), impl.getKey(), answer);
                if (reference == null) {
                    reference = answer;
                } else if (!reference.equals(answer)) {
                    same = false;
                    System.out.println("  ^ differs from jdk");
                }
            }
        }
        System.out.println(same ? "all implementations agree" : "IMPLEMENTATIONS DISAGREE");
    }
}
