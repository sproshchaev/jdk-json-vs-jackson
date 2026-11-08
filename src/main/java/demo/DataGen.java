package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Writes the test documents with a fixed seed, without any JSON library,
 * so that both parsers read exactly the same bytes.
 *
 * <pre>
 *   java demo.DataGen data target/data
 * </pre>
 * The first directory gets the small files kept in git, the second one gets
 * the large file that is regenerated on every run.
 */
public final class DataGen {

    private static final String[] STATES = {"RUNNABLE", "WAITING", "TIMED_WAITING", "BLOCKED"};
    private static final String[] FRAMES = {
            "java.base/java.lang.Object.wait0(Native Method)",
            "java.base/java.lang.Object.wait(Object.java:389)",
            "java.base/java.lang.ref.Reference.waitForReferencePendingList(Native Method)",
            "java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:371)",
            "java.base/java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:435)",
            "java.base/java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1070)",
            "java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)",
            "java.base/java.lang.Thread.run(Thread.java:1474)",
            "shop.order.OrderService.place(OrderService.java:118)",
            "shop.order.OrderController.create(OrderController.java:42)",
    };

    public static void main(String[] args) throws IOException {
        Path small = Path.of(args.length > 0 ? args[0] : "data");
        Path large = Path.of(args.length > 1 ? args[1] : "target/data");
        Files.createDirectories(small);
        Files.createDirectories(large);
        write(small.resolve("forecast.json"), forecast(new Random(42)));
        write(small.resolve("threaddump-medium.json"), threadDump(new Random(42), 4, 40));
        write(large.resolve("threaddump-large.json"), threadDump(new Random(42), 40, 400));
    }

    private static void write(Path file, String json) throws IOException {
        Files.writeString(file, json);
        System.out.printf("%s %d bytes%n", file, Files.size(file));
    }

    static String forecast(Random rnd) {
        String[] names = {"Today", "Tonight", "Monday", "Monday Night", "Tuesday", "Tuesday Night",
                "Wednesday", "Wednesday Night", "Thursday", "Thursday Night", "Friday",
                "Friday Night", "Saturday", "Saturday Night"};
        var sb = new StringBuilder();
        sb.append("{\n  \"type\": \"Feature\",\n  \"properties\": {\n")
          .append("    \"units\": \"us\",\n    \"generatedAt\": \"2026-11-08T06:00:00+00:00\",\n")
          .append("    \"periods\": [\n");
        for (int i = 0; i < names.length; i++) {
            boolean day = i % 2 == 0;
            int temp = day ? 50 + rnd.nextInt(15) : 35 + rnd.nextInt(12);
            sb.append("      {\n")
              .append("        \"number\": ").append(i + 1).append(",\n")
              .append("        \"name\": \"").append(names[i]).append("\",\n")
              .append("        \"startTime\": \"2026-11-").append(String.format(Locale.ROOT, "%02d", 8 + i / 2))
              .append(day ? "T06:00:00-05:00" : "T18:00:00-05:00").append("\",\n")
              .append("        \"isDaytime\": ").append(day).append(",\n")
              .append("        \"temperature\": ").append(temp).append(",\n")
              .append("        \"temperatureUnit\": \"F\",\n")
              .append("        \"probabilityOfPrecipitation\": ")
              .append(rnd.nextInt(3) == 0 ? "null" : String.valueOf(rnd.nextInt(10) * 10)).append(",\n")
              .append("        \"windSpeed\": \"").append(5 + rnd.nextInt(10)).append(" mph\",\n")
              .append("        \"shortForecast\": \"").append(day ? "Partly Sunny" : "Mostly Cloudy").append("\"\n")
              .append(i + 1 < names.length ? "      },\n" : "      }\n");
        }
        sb.append("    ]\n  }\n}\n");
        return sb.toString();
    }

    static String threadDump(Random rnd, int containers, int threadsPerContainer) {
        var sb = new StringBuilder();
        sb.append("{\n  \"threadDump\": {\n")
          .append("    \"formatVersion\": 2,\n    \"processId\": 45178,\n")
          .append("    \"time\": \"2026-11-08T06:00:00.000000Z\",\n")
          .append("    \"runtimeVersion\": \"28-ea\",\n")
          .append("    \"threadContainers\": [\n");
        long tid = 1;
        for (int c = 0; c < containers; c++) {
            boolean root = c == 0;
            sb.append("      {\n")
              .append("        \"container\": \"").append(root ? "<root>" : "java.util.concurrent.ThreadPoolExecutor@" + Integer.toHexString(0x1936a586 + c)).append("\",\n")
              .append("        \"parent\": ").append(root ? "null" : "\"<root>\"").append(",\n")
              .append("        \"owner\": null,\n")
              .append("        \"threads\": [\n");
            for (int t = 0; t < threadsPerContainer; t++) {
                String state = STATES[rnd.nextInt(STATES.length)];
                sb.append("          {\n")
                  .append("            \"tid\": ").append(tid++).append(",\n")
                  .append("            \"time\": \"2026-11-08T06:00:00.").append(String.format(Locale.ROOT, "%06d", rnd.nextInt(1_000_000))).append("Z\",\n")
                  .append("            \"name\": \"").append(root && t == 0 ? "main" : "pool-" + c + "-thread-" + t).append("\",\n")
                  .append("            \"state\": \"").append(state).append("\",\n");
                // as in a real JDK dump: waitingOn only for threads in Object.wait(),
                // blockedOn for BLOCKED; sleeping and parked threads have neither
                String monitorField = switch (state) {
                    case "WAITING", "TIMED_WAITING" -> "waitingOn";
                    case "BLOCKED" -> "blockedOn";
                    default -> null;
                };
                if (monitorField != null && rnd.nextBoolean()) {
                    sb.append("            \"").append(monitorField).append("\": \"java.lang.Object@")
                      .append(Integer.toHexString(rnd.nextInt())).append("\",\n");
                }
                sb.append("            \"stack\": [\n");
                int depth = 3 + rnd.nextInt(8);
                for (int f = 0; f < depth; f++) {
                    sb.append("              \"").append(FRAMES[rnd.nextInt(FRAMES.length)]).append(f + 1 < depth ? "\",\n" : "\"\n");
                }
                sb.append("            ]\n")
                  .append(t + 1 < threadsPerContainer ? "          },\n" : "          }\n");
            }
            sb.append("        ],\n")
              .append("        \"threadCount\": ").append(threadsPerContainer).append("\n")
              .append(c + 1 < containers ? "      },\n" : "      }\n");
        }
        sb.append("    ]\n  }\n}\n");
        return sb.toString();
    }
}
