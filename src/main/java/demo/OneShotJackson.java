package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The same script on Jackson: "tree" or "binding". Started without
 * --add-modules jdk.incubator.json, the JDK JSON module is not resolved.
 */
public final class OneShotJackson {

    public static void main(String[] args) throws IOException {
        JsonTasks tasks = switch (args.length == 1 ? args[0] : "") {
            case "tree" -> new JacksonTreeTasks();
            case "binding" -> new JacksonBindingTasks();
            default -> throw new IllegalArgumentException("usage: OneShotJackson tree|binding");
        };
        String doc = Files.readString(Path.of(DataFiles.FORECAST));
        System.out.printf(Locale.ROOT, "%.4f%n", tasks.averageTemperature(doc));
    }
}
